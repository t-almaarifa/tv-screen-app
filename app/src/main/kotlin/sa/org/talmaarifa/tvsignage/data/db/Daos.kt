package sa.org.talmaarifa.tvsignage.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaCacheDao {
    @Upsert
    suspend fun upsert(entity: MediaCacheEntity)

    @Query("SELECT * FROM media_cache WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: String): MediaCacheEntity?

    @Query("SELECT * FROM media_cache")
    suspend fun getAll(): List<MediaCacheEntity>

    @Query("SELECT * FROM media_cache WHERE verified = 1")
    suspend fun getAllVerified(): List<MediaCacheEntity>

    @Query("DELETE FROM media_cache WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)
}

@Dao
interface ActivePublicationDao {
    @Transaction
    @Query("SELECT * FROM active_publication WHERE id = :id")
    fun observe(id: String = ActivePublicationEntity.SINGLETON_ID): Flow<ActivePublicationWithItems?>

    @Transaction
    @Query("SELECT * FROM active_publication WHERE id = :id")
    suspend fun getOnce(id: String = ActivePublicationEntity.SINGLETON_ID): ActivePublicationWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPublication(entity: ActivePublicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ActivePublicationItemEntity>)

    @Query("DELETE FROM active_publication_items WHERE ownerId = :ownerId")
    suspend fun clearItems(ownerId: String = ActivePublicationEntity.SINGLETON_ID)

    /**
     * The only way the active publication ever changes: clear the previous item set and insert the
     * new publication + its items in one transaction. A reader (the player) observing [observe]
     * never sees a partially-replaced set (FR-25's "atomic switch").
     */
    @Transaction
    suspend fun replaceActive(publication: ActivePublicationEntity, items: List<ActivePublicationItemEntity>) {
        clearItems(publication.id)
        insertPublication(publication)
        insertItems(items)
    }
}

@Dao
interface PlaybackEventDao {
    @Insert
    suspend fun insert(event: PlaybackEventEntity): Long

    @Query("SELECT * FROM playback_events WHERE uploaded = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getUnuploaded(limit: Int): List<PlaybackEventEntity>

    @Query("UPDATE playback_events SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM playback_events WHERE uploaded = 0")
    suspend fun countUnuploaded(): Int
}
