package com.lifesaver.cardiac.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EscalationSnapshotEntity::class,
        EscalationTransitionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NotTodayDatabase : RoomDatabase() {
    abstract fun escalationDao(): EscalationDao

    companion object {
        private const val DATABASE_NAME = "nottoday-safety.db"

        @Volatile
        private var instance: NotTodayDatabase? = null

        fun getInstance(context: Context): NotTodayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotTodayDatabase::class.java,
                    DATABASE_NAME,
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
