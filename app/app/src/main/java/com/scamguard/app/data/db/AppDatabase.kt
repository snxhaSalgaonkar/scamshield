package com.scamguard.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The app's only database. Contains events, decisions and hashed numbers, never text. */
@Database(
    entities = [
        CallEventEntity::class,
        SignalEventEntity::class,
        DecisionEntity::class,
        ReputationEntity::class,
        TrustedNumberEntity::class,
        MessageCheckEntity::class,
        PredictionFeedbackEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEvents(): CallEventDao
    abstract fun signalEvents(): SignalEventDao
    abstract fun decisions(): DecisionDao
    abstract fun reputation(): ReputationDao
    abstract fun trustedNumbers(): TrustedNumberDao
    abstract fun messageChecks(): MessageCheckDao
    abstract fun feedback(): PredictionFeedbackDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scamguard.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: ML columns on decisions, message checks and the feedback table. Keeps all v1 data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }

        /** v3: flagged SMS keep their text, sender and rule reasons for the message history. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_checks ADD COLUMN body TEXT")
                db.execSQL("ALTER TABLE message_checks ADD COLUMN sender TEXT")
                db.execSQL("ALTER TABLE message_checks ADD COLUMN reasons TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE decisions ADD COLUMN scoreSource TEXT NOT NULL DEFAULT 'RULE_FALLBACK'",
            "ALTER TABLE decisions ADD COLUMN probability REAL",
            "ALTER TABLE decisions ADD COLUMN modelVersion TEXT",
            "ALTER TABLE decisions ADD COLUMN experimental INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE decisions ADD COLUMN factors TEXT",
            "ALTER TABLE decisions ADD COLUMN features TEXT",
            "CREATE TABLE IF NOT EXISTS `message_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ts` INTEGER NOT NULL, `source` TEXT NOT NULL, `verdict` TEXT NOT NULL, `scamProbability` REAL, " +
                "`category` TEXT, `categoryProbability` REAL, `indicators` TEXT NOT NULL, `linkCount` INTEGER NOT NULL, " +
                "`maxUrlProbability` REAL, `senderTrusted` INTEGER NOT NULL, `windowId` TEXT, `modelVersion` TEXT, " +
                "`simulated` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_message_checks_ts` ON `message_checks` (`ts`)",
            "CREATE TABLE IF NOT EXISTS `prediction_feedback` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ts` INTEGER NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `verdict` TEXT NOT NULL, " +
                "`predictedLevel` TEXT, `predictedType` TEXT, `probability` REAL, `modelVersion` TEXT, `features` TEXT, " +
                "`simulated` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_prediction_feedback_targetId` ON `prediction_feedback` (`targetId`)",
        )
    }
}
