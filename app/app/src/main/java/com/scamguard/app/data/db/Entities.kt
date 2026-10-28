package com.scamguard.app.data.db

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
