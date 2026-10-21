package sa.org.talmaarifa.tvsignage.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaCacheEntity::class,
        ActivePublicationEntity::class,
        ActivePublicationItemEntity::class,
        PlaybackEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun activePublicationDao(): ActivePublicationDao
    abstract fun playbackEventDao(): PlaybackEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tv_signage.db")
                .build()
                .also { instance = it }
        }
    }
}
