package sa.org.talmaarifa.tvsignage.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/** One locally downloaded & checksum-verified (or in-progress) media file. */
@Entity(tableName = "media_cache")
data class MediaCacheEntity(
    @PrimaryKey val mediaId: String,
    val checksum: String,
    val type: String, // "IMAGE" | "VIDEO"
    val localPath: String,
    val sizeBytes: Long,
    val verified: Boolean,
    val updatedAtEpochMillis: Long,
)

/**
 * Singleton row (always id = "active") describing the publication the player is *currently*
 * showing. Only ever replaced in one atomic transaction (see ActivePublicationDao.replaceActive) —
 * never partially updated — so a reader never observes a half-switched state (FR-25).
 */
@Entity(tableName = "active_publication")
data class ActivePublicationEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val publicationId: String,
    val version: Int,
    val generatedAt: String?,
) {
    companion object {
        const val SINGLETON_ID = "active"
    }
}

@Entity(tableName = "active_publication_items")
data class ActivePublicationItemEntity(
    @PrimaryKey val mediaId: String,
    /** Always [ActivePublicationEntity.SINGLETON_ID] today; exists only so @Relation has a join key. */
    val ownerId: String = ActivePublicationEntity.SINGLETON_ID,
    val checksum: String,
    val type: String,
    val order: Int,
    val seconds: Int,
    val localPath: String,
)

data class ActivePublicationWithItems(
    @Embedded val publication: ActivePublicationEntity,
    @Relation(parentColumn = "id", entityColumn = "ownerId", entity = ActivePublicationItemEntity::class)
    val items: List<ActivePublicationItemEntity>,
)

/** Local playback log row queued for batch upload via POST /api/v1/tv/device/events. */
@Entity(tableName = "playback_events")
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val occurredAtEpochMillis: Long,
    val confirmed: Boolean,
    val detailsJson: String,
    val uploaded: Boolean = false,
)
