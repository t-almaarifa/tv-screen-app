package sa.org.talmaarifa.tvsignage.core

import kotlin.math.min
import kotlin.math.pow

/**
 * Backoff for network calls that fail (heartbeat, manifest fetch, pairing poll, media download).
 * The *happy path* interval always comes from the server (`poll_interval_seconds` in the heartbeat
 * response, docs/signage-module.md §3.2.3 — "the server decides the cadence, not the device"); this
 * is only for when a call fails outright (device offline, server unreachable) and the app must keep
 * retrying on its own without hammering the network.
 */
object RetrySchedule {
    /** Exponential backoff, base 2, capped, with a deterministic (seed-free) jitter fraction for tests. */
    fun delaySeconds(attempt: Int, baseSeconds: Int = 5, capSeconds: Int = 300, jitterFraction: Double = 0.0): Long {
        require(attempt >= 0)
        val raw = baseSeconds * 2.0.pow(attempt)
        val capped = min(raw, capSeconds.toDouble())
        val jitter = capped * jitterFraction
        return (capped + jitter).toLong().coerceAtLeast(baseSeconds.toLong())
    }
}

/** Whether the HTTP server for media downloads advertised byte-range resumption on this response. */
data class DownloadServerCapability(val acceptsRanges: Boolean, val contentLength: Long?)

enum class DownloadStrategy { START_FROM_ZERO, RESUME_FROM_OFFSET }

data class DownloadDecision(val strategy: DownloadStrategy, val resumeFromByte: Long)

/**
 * Decides how to continue a download that was interrupted mid-transfer (connection dropped while
 * writing the temp file). As implemented today, `openPrivateFile` (src/lib/services/files.ts) never
 * sends `Accept-Ranges`/handles `Range`, so [DownloadServerCapability.acceptsRanges] is always false
 * against the current backend — the only correct strategy is to discard the partial temp file and
 * restart the whole transfer. The range-aware branch is kept so the client degrades gracefully (and
 * automatically starts resuming) the day the backend adds Range support, without an app update.
 */
object DownloadResumption {
    fun decide(bytesWrittenSoFar: Long, capability: DownloadServerCapability): DownloadDecision =
        if (bytesWrittenSoFar > 0 && capability.acceptsRanges) {
            DownloadDecision(DownloadStrategy.RESUME_FROM_OFFSET, bytesWrittenSoFar)
        } else {
            DownloadDecision(DownloadStrategy.START_FROM_ZERO, 0L)
        }
}
