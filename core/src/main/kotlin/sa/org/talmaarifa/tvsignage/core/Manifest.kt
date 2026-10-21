package sa.org.talmaarifa.tvsignage.core

/**
 * Domain mirror of `ManifestView` returned by `GET /api/v1/tv/device/manifest`
 * (src/lib/tv/publications.ts). The server has already resolved priority, group membership and
 * schedule conflicts (docs/signage-module.md §7) — this app never re-implements that logic, it only
 * consumes the winning, pre-resolved manifest.
 */
data class ManifestItem(
    val mediaId: String,
    val checksum: String,
    val type: MediaType,
    val order: Int,
    val seconds: Int,
    val downloadPath: String,
)

enum class MediaType { IMAGE, VIDEO, UNKNOWN }

data class Manifest(
    val publicationId: String?,
    val version: Int,
    val generatedAt: String?,
    val items: List<ManifestItem>,
    val ttlSeconds: Int,
) {
    val isEmpty: Boolean get() = publicationId == null || items.isEmpty()

    /** Items in display order, as the playback loop should present them. */
    fun orderedItems(): List<ManifestItem> = items.sortedBy { it.order }
}

/** One item's local download/verification state, tracked by the sync engine (see [SyncPlanner]). */
data class LocalMediaState(
    val mediaId: String,
    val checksum: String,
    val verified: Boolean,
)
