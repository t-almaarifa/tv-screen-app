package sa.org.talmaarifa.tvsignage.data.repo

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import sa.org.talmaarifa.tvsignage.core.MAX_EVENTS_PER_BATCH
import sa.org.talmaarifa.tvsignage.core.PlaybackEventRecord
import sa.org.talmaarifa.tvsignage.data.db.PlaybackEventDao
import sa.org.talmaarifa.tvsignage.data.db.PlaybackEventEntity
import sa.org.talmaarifa.tvsignage.network.ApiCaller
import sa.org.talmaarifa.tvsignage.network.ApiResult
import sa.org.talmaarifa.tvsignage.network.DeviceApi
import sa.org.talmaarifa.tvsignage.network.dto.EventsRequestDto
import sa.org.talmaarifa.tvsignage.network.dto.PlaybackEventDto
import java.time.Instant

/**
 * Local playback log (Room) queued for `POST /api/v1/tv/device/events` (docs/tv-device-api.md §6),
 * uploaded in batches of at most [MAX_EVENTS_PER_BATCH] as soon as connectivity allows. `confirmed`
 * is whatever [PlaybackEventRecord] was created with — never touched here.
 */
class EventLogRepository(
    private val dao: PlaybackEventDao,
    private val deviceApi: DeviceApi,
    private val apiCaller: ApiCaller,
    moshi: Moshi,
) {
    private val detailsAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    suspend fun record(event: PlaybackEventRecord) {
        dao.insert(
            PlaybackEventEntity(
                type = event.type.name,
                occurredAtEpochMillis = event.occurredAtEpochMillis,
                confirmed = event.confirmed,
                detailsJson = detailsAdapter.toJson(event.details),
            ),
        )
    }

    /** Uploads whatever is queued, one server-accepted batch at a time. Stops at the first failure. */
    suspend fun flushPending(): ApiResult<Int> {
        var totalUploaded = 0
        while (true) {
            val batch = dao.getUnuploaded(MAX_EVENTS_PER_BATCH)
            if (batch.isEmpty()) return ApiResult.Success(totalUploaded)

            val dtos = batch.map { row ->
                PlaybackEventDto(
                    type = row.type,
                    occurredAt = Instant.ofEpochMilli(row.occurredAtEpochMillis).toString(),
                    confirmed = row.confirmed,
                    details = runCatching { detailsAdapter.fromJson(row.detailsJson) }.getOrNull() ?: emptyMap(),
                )
            }
            when (val r = apiCaller.call { deviceApi.uploadEvents(EventsRequestDto(dtos)) }) {
                is ApiResult.Success -> {
                    dao.markUploaded(batch.map { it.id })
                    totalUploaded += batch.size
                }
                is ApiResult.ApiError -> return r
                is ApiResult.NetworkError -> return r
            }
        }
    }
}
