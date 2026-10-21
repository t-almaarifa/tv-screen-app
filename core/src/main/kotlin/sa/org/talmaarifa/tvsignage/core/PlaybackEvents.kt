package sa.org.talmaarifa.tvsignage.core

/** Mirrors `PLAYBACK_EVENT_TYPES` in src/lib/tv/rules.ts — any other string is rejected by the server (422). */
enum class PlaybackEventType { DOWNLOAD_START, DOWNLOAD_DONE, PLAY_START, ERROR, HEARTBEAT }

/**
 * One row of the local playback log (Room), queued for batch upload via `POST /api/v1/tv/device/events`
 * (docs/signage-module.md §3.2.5). `occurredAt` is the device's own clock, which the doc explicitly
 * warns may be out of sync after a long outage — the server stores it as sent.
 *
 * `confirmed` is set once, at creation time, by the factory functions below — never re-derived or
 * "upgraded" later. The server stores and displays it exactly as sent (FR-28): this app must not
 * reclassify an estimate as confirmed just because, say, playback later succeeded.
 */
data class PlaybackEventRecord(
    val type: PlaybackEventType,
    val occurredAtEpochMillis: Long,
    val confirmed: Boolean,
    val details: Map<String, Any?> = emptyMap(),
)

/**
 * The only place allowed to construct [PlaybackEventRecord]s, so "confirmed" can never be set
 * ad hoc at a call site. Each factory encodes *why* that event either does or doesn't count as
 * confirmed evidence:
 *
 * - [downloadStarted] / [downloadVerified] / [playbackStarted] / [playbackError]: the device directly
 *   observed the thing it's reporting (a started HTTP request, a checksum match, a player callback) —
 *   confirmed = true.
 * - [contentContinuedOffline]: there is no server round-trip and no fresh proof during a network
 *   outage that the right content is still showing — only an assumption that nothing crashed —
 *   confirmed = false, reported under the generic `HEARTBEAT` type per §3.2.5's own example
 *   ("استمر العرض" أثناء انقطاع دون دليل قطعي").
 */
object PlaybackEventFactory {
    fun downloadStarted(mediaId: String, atEpochMillis: Long): PlaybackEventRecord =
        PlaybackEventRecord(PlaybackEventType.DOWNLOAD_START, atEpochMillis, confirmed = true, details = mapOf("media_id" to mediaId))

    fun downloadVerified(mediaId: String, checksum: String, atEpochMillis: Long): PlaybackEventRecord =
        PlaybackEventRecord(PlaybackEventType.DOWNLOAD_DONE, atEpochMillis, confirmed = true, details = mapOf("media_id" to mediaId, "checksum" to checksum))

    fun playbackStarted(publicationId: String?, mediaId: String, atEpochMillis: Long): PlaybackEventRecord =
        PlaybackEventRecord(PlaybackEventType.PLAY_START, atEpochMillis, confirmed = true, details = mapOf("publication_id" to publicationId, "media_id" to mediaId))

    fun playbackError(message: String, mediaId: String?, atEpochMillis: Long): PlaybackEventRecord =
        PlaybackEventRecord(PlaybackEventType.ERROR, atEpochMillis, confirmed = true, details = mapOf("message" to message, "media_id" to mediaId))

    fun contentContinuedOffline(publicationId: String?, atEpochMillis: Long): PlaybackEventRecord =
        PlaybackEventRecord(PlaybackEventType.HEARTBEAT, atEpochMillis, confirmed = false, details = mapOf("publication_id" to publicationId, "note" to "offline_continuation"))
}

/** Server caps a batch at 200 events (`parsePlaybackEvents`, src/lib/tv/rules.ts). */
const val MAX_EVENTS_PER_BATCH = 200

/** Splits a queued backlog into server-accepted batch sizes, oldest first. */
fun chunkForUpload(records: List<PlaybackEventRecord>): List<List<PlaybackEventRecord>> =
    if (records.isEmpty()) emptyList() else records.chunked(MAX_EVENTS_PER_BATCH)
