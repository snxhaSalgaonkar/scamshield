package com.scamguard.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The app's only database. Contains events, decisions and hashed numbers, never text. */
@Database(
    entities = [
        CallEventEntity::class,
        SignalEventEntity::class,
        DecisionEntity::class,
        ReputationEntity::class,
        TrustedNumberEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEvents(): CallEventDao
    abstract fun signalEvents(): SignalEventDao
    abstract fun decisions(): DecisionDao
    abstract fun reputation(): ReputationDao
    abstract fun trustedNumbers(): TrustedNumberDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scamguard.db").build()
    }
}
