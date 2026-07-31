package haven.mobile.core.cache.mirror

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MediaMirrorEntity::class], version = 1, exportSchema = false)
abstract class HavenMirrorDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        fun databaseName(walletAddress: String): String = "haven-mirror-$walletAddress.db"
    }
}