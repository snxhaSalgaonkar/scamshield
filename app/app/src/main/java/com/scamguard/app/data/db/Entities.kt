package com.scamguard.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A call state change inside a call window. Numbers are hashes only. */
@Entity(tableName = "call_events", indices = [Index("windowId"), Index("numberHash"), Index("ts")])
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowId: String,
    val ts: Long,
    val type: String,
    val numberHash: String?,
    val prefix: String,
    val isUnknown: Boolean,
    val isInternational: Boolean,
    val isHidden: Boolean,
    val durationMs: Long?,
    val simulated: Boolean,
)

/**
 * A non-call signal (OTP, debit, link, forwarding, app, network, feedback, voice keyword).
 * Only event types, amounts, hashes and package names are stored, never message text.
 */
@Entity(tableName = "signal_events", indices = [Index("windowId"), Index("ts")])
data class SignalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowId: String?,
    val ts: Long,
    val type: String,
    val amountPaise: Long? = null,
    val payeeHash: String? = null,
    val balancePaise: Long? = null,
    val pkg: String? = null,
    val flag: Boolean? = null,
    val detail: String? = null,
    val simulated: Boolean,
)

/** The latest risk decision for one call window, plus the peak level it reached. */
@Entity(tableName = "decisions", indices = [Index("ts")])
data class DecisionEntity(
    @PrimaryKey val windowId: String,
    val ts: Long,
    val startedAt: Long,
    val score: Int,
    val level: String,
    val peakScore: Int,
    val peakLevel: String,
    val l1: Int,
    val l2: Int,
    val l3: Int,
    val scamType: String,
    val reasonCodes: String,
    val comboFired: Boolean,
    val numberHash: String?,
    val prefix: String,
    val mask: String,
    val callEnded: Boolean,
    val simulated: Boolean,
    val feedback: String?,
    /** MODEL, RULE_FALLBACK or GATED (see ScoreSource). Rows from before ML are RULE_FALLBACK. */
    @ColumnInfo(defaultValue = "RULE_FALLBACK") val scoreSource: String = "RULE_FALLBACK",
    val probability: Double? = null,
    val modelVersion: String? = null,
    @ColumnInfo(defaultValue = "0") val experimental: Boolean = false,
    /** Comma-separated "feature:delta" pairs, largest first. */
    val factors: String? = null,
    /** Comma-separated model input vector; kept only for consented feedback export. */
    val features: String? = null,
)

/** Local reputation of a number: seed list entries and the user's own reports. */
@Entity(tableName = "reputation")
data class ReputationEntity(
    @PrimaryKey val numberHash: String,
    val prefix: String,
    val mask: String?,
    val localReports: Int,
    val status: String,
    val source: String,
    val expiresAt: Long?,
    val updatedAt: Long,
)

/** A number the user trusts; calls from it never open a scored window. */
@Entity(tableName = "trusted_numbers")
data class TrustedNumberEntity(
    @PrimaryKey val numberHash: String,
    val prefix: String,
    val mask: String,
    val source: String,
    val addedAt: Long,
)

/**
 * The message model's verdict on one SMS (or a simulated one). The text is never stored:
 * only probabilities, the experimental category and a few model-vocabulary words.
 */
@Entity(tableName = "message_checks", indices = [Index("ts")])
data class MessageCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** SMS or SIMULATED. Manual checks are not stored unless the user gives feedback. */
    val source: String,
    val verdict: String,
    val scamProbability: Double?,
    val category: String?,
    val categoryProbability: Double?,
    /** Comma-separated words from the model vocabulary that raised the score. */
    val indicators: String,
    val linkCount: Int,
    val maxUrlProbability: Double?,
    val senderTrusted: Boolean,
    /** Call window open when the message arrived, if any. */
    val windowId: String?,
    val modelVersion: String?,
    val simulated: Boolean,
)

/**
 * The user's verdict on a prediction. Kept apart from any training data: exports are
 * marked unreviewed and the training pipeline refuses unreviewed rows.
 */
@Entity(tableName = "prediction_feedback", indices = [Index("targetId")])
data class PredictionFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** CALL_WINDOW, MESSAGE or MANUAL_CHECK. */
    val targetType: String,
    val targetId: String,
    /** CORRECT, FALSE_POSITIVE, MISSED_SCAM or UNSURE. */
    val verdict: String,
    val predictedLevel: String?,
    val predictedType: String?,
    val probability: Double?,
    val modelVersion: String?,
    val features: String?,
    val simulated: Boolean,
)
