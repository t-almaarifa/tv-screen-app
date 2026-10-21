package sa.org.talmaarifa.tvsignage.core

/**
 * What the sync engine must do to converge local storage on a remote [Manifest], and whether it is
 * safe to switch playback to that manifest right now.
 *
 * Mirrors the acceptance criterion in docs/signage-module.md §3.2.4 / §13 ("ملف غير مكتمل"): the device
 * must compare `publication_id`/`version` against what it has locally, download only what's missing,
 * verify every checksum, and never activate a new version until *all* of its items are present and
 * verified — previous content keeps playing in the meantime, and a partially-downloaded file never
 * plays.
 */
data class SyncPlan(
    val targetPublicationId: String?,
    val targetVersion: Int,
    /** Already matches the currently active local publication; nothing to do. */
    val alreadyActive: Boolean,
    /** Items that must be downloaded (missing locally, or present but not checksum-verified). */
    val itemsToDownload: List<ManifestItem>,
    /** Media ids cached locally that no longer appear in the target manifest and can be freed. */
    val mediaIdsToEvict: List<String>,
) {
    /** True once every target item has a verified local copy — the only moment an atomic switch may happen. */
    fun isCompleteFor(verifiedMediaIds: Set<String>): Boolean =
        itemsToDownload.isEmpty() || itemsToDownload.all { it.mediaId in verifiedMediaIds }
}

object SyncPlanner {

    /**
     * @param remote the manifest just fetched from the server.
     * @param currentLocalPublicationId the publication id the device is *currently playing* (null if none yet).
     * @param currentLocalVersion the version of that publication (0 if none).
     * @param localMedia known local media state, keyed by media id (from Room).
     */
    fun plan(
        remote: Manifest,
        currentLocalPublicationId: String?,
        currentLocalVersion: Int,
        localMedia: Map<String, LocalMediaState>,
    ): SyncPlan {
        val alreadyActive = remote.publicationId != null &&
            remote.publicationId == currentLocalPublicationId &&
            remote.version == currentLocalVersion

        val toDownload = remote.items.filter { item ->
            val local = localMedia[item.mediaId]
            local == null || !local.verified || !Checksum.matches(item.checksum, local.checksum)
        }

        val remoteIds = remote.items.map { it.mediaId }.toSet()
        val toEvict = localMedia.keys.filterNot { it in remoteIds }

        return SyncPlan(
            targetPublicationId = remote.publicationId,
            targetVersion = remote.version,
            alreadyActive = alreadyActive,
            itemsToDownload = toDownload,
            mediaIdsToEvict = toEvict,
        )
    }

    /**
     * Should the device switch its active publication pointer now? Only once every target item has a
     * verified local file — an all-or-nothing atomic switch (FR-25). Never switch to an empty/unknown
     * manifest just because it is "complete" with zero items unless that really is the resolved state
     * (publicationId non-null with zero items is a valid, if unusual, resolved manifest).
     */
    fun shouldActivate(plan: SyncPlan, verifiedMediaIds: Set<String>): Boolean {
        if (plan.alreadyActive) return false
        if (plan.targetPublicationId == null) return false
        return plan.isCompleteFor(verifiedMediaIds)
    }
}
