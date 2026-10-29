package com.scamguard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Access to call state events. */
@Dao
interface CallEventDao {
    @Insert
    suspend fun insert(event: CallEventEntity)

    @Query("SELECT * FROM call_events WHERE windowId = :windowId ORDER BY ts")
    suspend fun byWindow(windowId: String): List<CallEventEntity>

    @Query(
        "SELECT COUNT(*) FROM call_events WHERE type = 'RINGING' AND numberHash = :hash " +
            "AND ts >= :since AND simulated = 0"
    )
    suspend fun ringsSince(hash: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM call_events WHERE type = 'RINGING' AND simulated = 0")
    fun observeCallsChecked(): Flow<Int>
}

/** Access to SMS and phone signal events. */
@Dao
interface SignalEventDao {
    @Insert
    suspend fun insert(event: SignalEventEntity)

    @Query("SELECT * FROM signal_events WHERE windowId = :windowId ORDER BY ts")
    suspend fun byWindow(windowId: String): List<SignalEventEntity>

    @Query(
        "SELECT amountPaise FROM signal_events WHERE type = 'DEBIT' AND amountPaise > 0 " +
            "AND ts < :before AND simulated = 0 ORDER BY ts DESC LIMIT :limit"
    )
    suspend fun recentDebitAmounts(before: Long, limit: Int): List<Long>

    @Query(
        "SELECT DISTINCT payeeHash FROM signal_events WHERE type = 'DEBIT' " +
            "AND payeeHash IS NOT NULL AND ts < :before AND simulated = 0"
    )
    suspend fun knownPayees(before: Long): List<String>
}

/** Access to scored call windows. */
@Dao
interface DecisionDao {
    @Upsert
    suspend fun upsert(decision: DecisionEntity)

    @Query("SELECT * FROM decisions WHERE windowId = :windowId")
    suspend fun get(windowId: String): DecisionEntity?

    @Query("SELECT * FROM decisions WHERE windowId = :windowId")
    fun observe(windowId: String): Flow<DecisionEntity?>

    @Query("SELECT * FROM decisions ORDER BY startedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE peakLevel != 'SAFE' AND simulated = 0 ORDER BY ts DESC LIMIT 1")
    fun observeLatestAlert(): Flow<DecisionEntity?>

    @Query("SELECT COUNT(*) FROM decisions WHERE peakLevel != 'SAFE' AND simulated = 0 AND ts >= :since")
    fun observeAlertsSince(since: Long): Flow<Int>

    @Query("UPDATE decisions SET feedback = :feedback WHERE windowId = :windowId")
    suspend fun setFeedback(windowId: String, feedback: String)

    @Query("SELECT * FROM decisions ORDER BY startedAt DESC")
    suspend fun all(): List<DecisionEntity>
}

/** Access to stored message verdicts. */
@Dao
interface MessageCheckDao {
    @Insert
    suspend fun insert(check: MessageCheckEntity): Long

    @Query("SELECT * FROM message_checks ORDER BY ts DESC LIMIT 500")
    fun observeAll(): Flow<List<MessageCheckEntity>>

    @Query("SELECT * FROM message_checks WHERE id = :id")
    fun observe(id: Long): Flow<MessageCheckEntity?>

    @Query("SELECT * FROM message_checks WHERE simulated = 0 AND verdict IN ('LIKELY_SCAM', 'SUSPICIOUS') ORDER BY ts DESC LIMIT 1")
    fun observeLatestSuspicious(): Flow<MessageCheckEntity?>

    @Query("SELECT * FROM message_checks ORDER BY ts DESC")
    suspend fun all(): List<MessageCheckEntity>

    @Query("SELECT COUNT(*) FROM message_checks WHERE ts = :ts AND sender IS :sender")
    suspend fun countAt(ts: Long, sender: String?): Int
}

/** Access to the user's verdicts on predictions. */
@Dao
interface PredictionFeedbackDao {
    @Insert
    suspend fun insert(feedback: PredictionFeedbackEntity): Long

    @Query("SELECT * FROM prediction_feedback WHERE targetId = :targetId ORDER BY ts DESC LIMIT 1")
    fun observeLatest(targetId: String): Flow<PredictionFeedbackEntity?>

    @Query("SELECT * FROM prediction_feedback ORDER BY ts DESC")
    suspend fun all(): List<PredictionFeedbackEntity>

    @Query("SELECT COUNT(*) FROM prediction_feedback")
    fun observeCount(): Flow<Int>
}

/** Access to the local reputation cache. */
@Dao
interface ReputationDao {
    @Query("SELECT * FROM reputation WHERE numberHash = :hash")
    suspend fun get(hash: String): ReputationEntity?

    @Upsert
    suspend fun upsert(entry: ReputationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entries: List<ReputationEntity>)

    @Query("SELECT * FROM reputation WHERE source = 'LOCAL' AND localReports > 0 ORDER BY updatedAt DESC")
    fun observeReported(): Flow<List<ReputationEntity>>

    @Query("DELETE FROM reputation WHERE numberHash = :hash AND source = 'LOCAL'")
    suspend fun deleteLocal(hash: String)

    @Query("DELETE FROM reputation WHERE source = 'SEED'")
    suspend fun deleteSeed()

    @Query("DELETE FROM reputation WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)
}

/** Access to the user's trusted numbers. */
@Dao
interface TrustedNumberDao {
    @Query("SELECT * FROM trusted_numbers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrustedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_numbers WHERE numberHash = :hash)")
    suspend fun isTrusted(hash: String): Boolean

    @Upsert
    suspend fun upsert(entry: TrustedNumberEntity)

    @Query("DELETE FROM trusted_numbers WHERE numberHash = :hash")
    suspend fun delete(hash: String)
}
