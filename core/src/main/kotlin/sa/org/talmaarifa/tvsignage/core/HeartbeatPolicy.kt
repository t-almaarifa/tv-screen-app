package sa.org.talmaarifa.tvsignage.core

/**
 * The server decides the heartbeat cadence (`poll_interval_seconds`, docs/signage-module.md §3.2.3) —
 * this only clamps it to a sane range so a misconfigured or compromised server can't make the device
 * hammer the network (too small) or go silent for absurd stretches (too large), and decides whether a
 * manifest re-fetch is needed from the heartbeat's `publication_changed` flag.
 */
object HeartbeatPolicy {
    const val MIN_INTERVAL_SECONDS = 5
    const val MAX_INTERVAL_SECONDS = 3600
    const val FALLBACK_INTERVAL_SECONDS = 30 // matches DEFAULT_SETTINGS.heartbeat_interval_seconds server-side

    fun clampIntervalSeconds(serverValue: Int?): Int {
        val v = serverValue ?: FALLBACK_INTERVAL_SECONDS
        return v.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
    }

    /** Should the app fetch a fresh manifest right now, given the latest heartbeat response? */
    fun shouldRefetchManifest(publicationChanged: Boolean, hasAnyLocalPublication: Boolean): Boolean =
        publicationChanged || !hasAnyLocalPublication
}
