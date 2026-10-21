package sa.org.talmaarifa.tvsignage.data.repo

import sa.org.talmaarifa.tvsignage.core.Checksum
import sa.org.talmaarifa.tvsignage.core.LocalMediaState
import sa.org.talmaarifa.tvsignage.core.Manifest
import sa.org.talmaarifa.tvsignage.core.PlaybackEventFactory
import sa.org.talmaarifa.tvsignage.core.SyncPlanner
import sa.org.talmaarifa.tvsignage.data.db.ActivePublicationDao
import sa.org.talmaarifa.tvsignage.data.db.ActivePublicationEntity
import sa.org.talmaarifa.tvsignage.data.db.ActivePublicationItemEntity
import sa.org.talmaarifa.tvsignage.data.db.ActivePublicationWithItems
import sa.org.talmaarifa.tvsignage.data.db.MediaCacheDao
import sa.org.talmaarifa.tvsignage.data.db.MediaCacheEntity
import sa.org.talmaarifa.tvsignage.data.mapper.toDomain
import sa.org.talmaarifa.tvsignage.network.ApiCaller
import sa.org.talmaarifa.tvsignage.network.ApiResult
import sa.org.talmaarifa.tvsignage.network.DeviceApi
import sa.org.talmaarifa.tvsignage.network.dto.HeartbeatRequestDto
import sa.org.talmaarifa.tvsignage.network.dto.HeartbeatResponseDto
import sa.org.talmaarifa.tvsignage.sync.DownloadOutcome
import sa.org.talmaarifa.tvsignage.sync.MediaDownloader
import sa.org.talmaarifa.tvsignage.sync.StorageLayout
import sa.org.talmaarifa.tvsignage.sync.isPermanentForThisItem

sealed interface SyncOutcome {
    data object NoChange : SyncOutcome
    data class Activated(val publicationId: String, val version: Int) : SyncOutcome
    data class PartiallyDownloaded(val verifiedCount: Int, val total: Int) : SyncOutcome
    /** 401 while downloading a media file: the stored credential was revoked mid-session. */
    data object CredentialInvalid : SyncOutcome
    data class Failed(val error: ApiResult<*>) : SyncOutcome
}

/**
 * Orchestrates heartbeat + manifest sync: fetches the resolved manifest (the server already picked
 * the winning schedule, docs/tv-device-api.md §4 — this never re-implements priority/conflict
 * resolution), downloads only what [sa.org.talmaarifa.tvsignage.core.SyncPlanner] says is missing,
 * and only flips the "active publication" pointer once every item verifies (FR-25's atomic switch).
 */
class SyncRepository(
    private val deviceApi: DeviceApi,
    private val apiCaller: ApiCaller,
    private val mediaCacheDao: MediaCacheDao,
    private val activePublicationDao: ActivePublicationDao,
    private val mediaDownloader: MediaDownloader,
    private val storage: StorageLayout,
    private val eventLogRepository: EventLogRepository,
) {
    suspend fun heartbeat(appVersion: String?, lastError: String?): ApiResult<HeartbeatResponseDto> {
        val active = activePublicationDao.getOnce()
        val body = HeartbeatRequestDto(
            appVersion = appVersion,
            currentPublicationId = active?.publication?.publicationId,
            storageFreeBytes = storage.freeBytes(),
            lastError = lastError,
        )
        return apiCaller.call { deviceApi.heartbeat(body) }
    }

    suspend fun syncManifest(): SyncOutcome {
        val manifest = when (val r = apiCaller.call { deviceApi.manifest() }) {
            is ApiResult.Success -> r.value.toDomain()
            is ApiResult.ApiError -> return SyncOutcome.Failed(r)
            is ApiResult.NetworkError -> return SyncOutcome.Failed(r)
        }

        val activeBefore = activePublicationDao.getOnce()
        val cachedRows = mediaCacheDao.getAll()
        val localMedia = cachedRows.associate { it.mediaId to LocalMediaState(it.mediaId, it.checksum, it.verified) }

        val plan = SyncPlanner.plan(
            remote = manifest,
            currentLocalPublicationId = activeBefore?.publication?.publicationId,
            currentLocalVersion = activeBefore?.publication?.version ?: 0,
            localMedia = localMedia,
        )

        if (plan.alreadyActive && plan.itemsToDownload.isEmpty()) {
            // Only safe moment to evict without a switch: the manifest didn't change, so whatever is
            // cached-but-unreferenced really is stale, not something still on screen.
            evict(plan.mediaIdsToEvict, protect = activeBefore)
            return SyncOutcome.NoChange
        }

        val verifiedNow = mutableSetOf<String>()
        for (item in manifest.items) {
            val local = localMedia[item.mediaId]
            if (local != null && local.verified && Checksum.matches(item.checksum, local.checksum)) verifiedNow += item.mediaId
        }

        for (item in plan.itemsToDownload) {
            eventLogRepository.record(PlaybackEventFactory.downloadStarted(item.mediaId, now()))
            when (val outcome = mediaDownloader.download(item)) {
                is DownloadOutcome.Success -> {
                    mediaCacheDao.upsert(
                        MediaCacheEntity(
                            mediaId = item.mediaId,
                            checksum = item.checksum,
                            type = item.type.name,
                            localPath = outcome.file.absolutePath,
                            sizeBytes = outcome.bytesWritten,
                            verified = true,
                            updatedAtEpochMillis = now(),
                        ),
                    )
                    eventLogRepository.record(PlaybackEventFactory.downloadVerified(item.mediaId, item.checksum, now()))
                    verifiedNow += item.mediaId
                }
                is DownloadOutcome.ChecksumMismatch -> eventLogRepository.record(
                    PlaybackEventFactory.playbackError("checksum_mismatch expected=${outcome.expected} actual=${outcome.actual}", item.mediaId, now()),
                )
                is DownloadOutcome.HttpError -> {
                    eventLogRepository.record(
                        PlaybackEventFactory.playbackError(
                            "http_error status=${outcome.httpStatus} code=${outcome.code} permanent=${outcome.isPermanentForThisItem()}",
                            item.mediaId,
                            now(),
                        ),
                    )
                    if (outcome.httpStatus == 401) return SyncOutcome.CredentialInvalid
                    // Anything else (permanent or not) is left for the next sync cycle to retry — this
                    // device never guesses at a partial playlist (FR-25).
                }
                is DownloadOutcome.NetworkError -> {
                    // Offline mid-sync: stop here, nothing is evicted, the previous publication (if
                    // any) keeps playing untouched. The next cycle resumes by re-requesting whatever
                    // is still missing from byte zero (no Range support server-side today).
                    return SyncOutcome.PartiallyDownloaded(verifiedNow.size, manifest.items.size)
                }
            }
        }

        if (SyncPlanner.shouldActivate(plan, verifiedNow)) {
            activate(manifest, verifiedNow)
            val activeAfter = activePublicationDao.getOnce()
            evict(plan.mediaIdsToEvict, protect = activeAfter)
            return SyncOutcome.Activated(manifest.publicationId!!, manifest.version)
        }

        return SyncOutcome.PartiallyDownloaded(verifiedNow.size, manifest.items.size)
    }

    private suspend fun activate(manifest: Manifest, verifiedNow: Set<String>) {
        val rows = manifest.items.filter { it.mediaId in verifiedNow }.map { item ->
            ActivePublicationItemEntity(
                mediaId = item.mediaId,
                checksum = item.checksum,
                type = item.type.name,
                order = item.order,
                seconds = item.seconds,
                localPath = storage.finalFile(item.mediaId).absolutePath,
            )
        }
        activePublicationDao.replaceActive(
            ActivePublicationEntity(publicationId = manifest.publicationId!!, version = manifest.version, generatedAt = manifest.generatedAt),
            rows,
        )
    }

    /** Never deletes a file the currently-displayed publication ([protect]) still references. */
    private suspend fun evict(mediaIds: List<String>, protect: ActivePublicationWithItems?) {
        val protectedIds = protect?.items?.map { it.mediaId }?.toSet().orEmpty()
        for (id in mediaIds) {
            if (id in protectedIds) continue
            storage.finalFile(id).delete()
            mediaCacheDao.deleteByMediaId(id)
        }
    }

    private fun now() = System.currentTimeMillis()
}
