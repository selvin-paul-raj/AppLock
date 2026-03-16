package dev.pranav.applock.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.pranav.applock.data.dao.IntruderDao
import dev.pranav.applock.data.model.IntruderLog

/**
 * Room database that stores [IntruderLog] entries captured by the
 * Dual Password Intruder Monitoring System.
 *
 * Access the singleton instance via [IntruderDatabase.getInstance].
 */
@Database(entities = [IntruderLog::class], version = 1, exportSchema = false)
abstract class IntruderDatabase : RoomDatabase() {

    abstract fun intruderDao(): IntruderDao

    companion object {
        @Volatile
        private var INSTANCE: IntruderDatabase? = null

        fun getInstance(context: Context): IntruderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IntruderDatabase::class.java,
                    "intruder_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
