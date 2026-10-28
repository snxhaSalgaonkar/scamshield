package com.scamguard.app.engine

/** How loudly the app reacts. SAFE means silent: the decision is only logged. */
enum class RiskLevel { SAFE, WARNING, RED_FLAG }

/** The kind of scam a call window most resembles. */
enum class ScamType {
    OTP_THEFT,
    MONEY_TRANSFER_PRESSURE,
    CALL_FORWARDING_HIJACK,
    CALL_MERGE_FRAUD,
    REMOTE_ACCESS_FRAUD,
    DIGITAL_ARREST,
    SUSPICIOUS_CALL,
    NONE,
}

/** Which evidence layer a rule belongs to. */
enum class Layer { L1_VOICE, L2_CALLER, L3_CORRELATION }

/** Why a score was given. Mapped to plain-language strings in the UI. */
enum class ReasonCode(val layer: Layer) {
    UNKNOWN_CALLER(Layer.L2_CALLER),
    INTERNATIONAL_OR_VOIP(Layer.L2_CALLER),
    REPEATED_CALLS(Layer.L2_CALLER),
    REPORTED_NUMBER(Layer.L2_CALLER),
    LONG_CALL(Layer.L2_CALLER),
    ODD_HOURS(Layer.L2_CALLER),
    KNOWN_BANK_NUMBER(Layer.L2_CALLER),
    USER_MARKED_SAFE(Layer.L2_CALLER),

    OTP_DURING_CALL(Layer.L3_CORRELATION),
    DEBIT_DURING_CALL(Layer.L3_CORRELATION),
    FORWARDING_ENABLED(Layer.L3_CORRELATION),
    CALL_MERGED(Layer.L3_CORRELATION),
    AMOUNT_FAR_ABOVE_NORMAL(Layer.L3_CORRELATION),
    FIRST_TIME_PAYEE(Layer.L3_CORRELATION),
    MULTIPLE_DEBITS(Layer.L3_CORRELATION),
    BALANCE_NEAR_ZERO(Layer.L3_CORRELATION),
    REMOTE_ACCESS_APP(Layer.L3_CORRELATION),
    SIDELOADED_APK(Layer.L3_CORRELATION),
    LINK_THEN_INSTALL(Layer.L3_CORRELATION),
    NETWORK_LOST(Layer.L3_CORRELATION),

    COMBO_OTP_DEBIT(Layer.L3_CORRELATION),
    COMBO_FORWARDING_OTP(Layer.L3_CORRELATION),
    COMBO_MERGE_OTP(Layer.L3_CORRELATION),
    COMBO_REMOTE_DEBIT(Layer.L3_CORRELATION),

    VOICE_OTP_REQUEST(Layer.L1_VOICE),
    VOICE_ARREST_THREAT(Layer.L1_VOICE),
    VOICE_PAYMENT_PRESSURE(Layer.L1_VOICE),
    VOICE_REMOTE_APP(Layer.L1_VOICE),
}

/** One rule that fired, with the points it contributed. */
data class Hit(val code: ReasonCode, val points: Int)

/** Facts about the caller looked up outside the engine (reputation, own call log). */
data class CallerContext(
    /** Rings from this number in the last hour, including the current call. */
    val callsLastHour: Int = 1,
    val reported: Boolean = false,
    val knownBankNumber: Boolean = false,
)

/** The user's normal debit pattern, from earlier debit events (never message text). */
data class TransactionHistory(
    val medianDebitPaise: Long? = null,
    val knownPayees: Set<String> = emptySet(),
) {
    companion object {
        /** Builds history from up to the last 20 debit amounts; needs [minSamples] for a median. */
        fun from(recentAmounts: List<Long>, knownPayees: Collection<String>, minSamples: Int = 3) =
            TransactionHistory(
                medianDebitPaise = if (recentAmounts.size >= minSamples) median(recentAmounts.take(20)) else null,
                knownPayees = knownPayees.toSet(),
            )

        fun median(values: List<Long>): Long? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}

/** The engine's verdict for one call window. */
data class Decision(
    val windowId: String,
    val evaluatedAt: Long,
    val score: Int,
    val level: RiskLevel,
    val l1: Int,
    val l2: Int,
    val l3: Int,
    val scamType: ScamType,
    /** Most important first: combos, then the largest contributions, then reducers. */
    val reasonCodes: List<ReasonCode>,
    val comboFired: Boolean,
    /** True when the caller was known, so the engine stayed silent. */
    val gated: Boolean,
)
