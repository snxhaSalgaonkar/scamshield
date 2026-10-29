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
    HIDDEN_NUMBER(Layer.L2_CALLER),
    INTERNATIONAL_OR_VOIP(Layer.L2_CALLER),
    REPEATED_CALLS(Layer.L2_CALLER),
    REPORTED_NUMBER(Layer.L2_CALLER),
    LONG_CALL(Layer.L2_CALLER),
    ODD_HOURS(Layer.L2_CALLER),
    KNOWN_BANK_NUMBER(Layer.L2_CALLER),
    USER_MARKED_SAFE(Layer.L2_CALLER),
    VIDEO_CALL_UNKNOWN(Layer.L2_CALLER),
    VIDEO_CALL_LONG(Layer.L2_CALLER),
    AUTHORITY_CALLER_NAME(Layer.L2_CALLER),

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
    LINK_RECEIVED(Layer.L3_CORRELATION),

    // From the on-device message and link models.
    SCAM_MESSAGE(Layer.L3_CORRELATION),
    SMS_IMPERSONATION(Layer.L3_CORRELATION),
    SMS_CREDENTIAL_REQUEST(Layer.L3_CORRELATION),
    SMS_PAYMENT_REQUEST(Layer.L3_CORRELATION),
    SMS_REMOTE_ACCESS(Layer.L3_CORRELATION),
    SUSPICIOUS_LINK(Layer.L3_CORRELATION),

    COMBO_OTP_DEBIT(Layer.L3_CORRELATION),
    /** The caller asked for an OTP and an OTP SMS arrived during the same call. */
    COMBO_OTP_ASKED(Layer.L3_CORRELATION),
    COMBO_FORWARDING_OTP(Layer.L3_CORRELATION),
    COMBO_MERGE_OTP(Layer.L3_CORRELATION),
    COMBO_REMOTE_DEBIT(Layer.L3_CORRELATION),
    COMBO_VIDEO_AUTHORITY(Layer.L3_CORRELATION),

    // From the on-demand screen check during a video call (content of the call, like voice).
    SCREEN_LAW_ENFORCEMENT(Layer.L1_VOICE),
    SCREEN_LEGAL_THREAT(Layer.L1_VOICE),
    SCREEN_PAYMENT_DEMAND(Layer.L1_VOICE),

    VOICE_OTP_REQUEST(Layer.L1_VOICE),
    VOICE_ARREST_THREAT(Layer.L1_VOICE),
    VOICE_PAYMENT_PRESSURE(Layer.L1_VOICE),
    VOICE_REMOTE_APP(Layer.L1_VOICE),
    VOICE_AUTHORITY_CLAIM(Layer.L1_VOICE),
    VOICE_ACCUSATION(Layer.L1_VOICE),
    VOICE_ISOLATION(Layer.L1_VOICE),
    VOICE_CARD_DETAILS(Layer.L1_VOICE),
    VOICE_LINK_REQUEST(Layer.L1_VOICE),

    // Stages of a spoken script seen together in one call (see VoiceRules).
    COMBO_VOICE_ACCUSED(Layer.L1_VOICE),
    COMBO_VOICE_THREAT(Layer.L1_VOICE),
    COMBO_VOICE_IMPERSONATION(Layer.L1_VOICE),
    COMBO_VOICE_DIGITAL_ARREST(Layer.L1_VOICE),
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

/** What produced a decision's score. */
enum class ScoreSource {
    /** The trained on-device call model. */
    MODEL,
    /** The call model could not be loaded; the transparent rule baseline was used instead. */
    RULE_FALLBACK,
    /** Known caller: nothing was scored. */
    GATED,
}

/** One piece of evidence and how much it raised (or lowered) the model's scam probability. */
data class Factor(val feature: String, val code: ReasonCode?, val delta: Double)

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
    val source: ScoreSource = if (gated) ScoreSource.GATED else ScoreSource.RULE_FALLBACK,
    /** Model scam probability (0-1); null when the score came from rules. */
    val probability: Double? = null,
    val modelVersion: String? = null,
    val experimental: Boolean = false,
    val factors: List<Factor> = emptyList(),
    /** The feature vector the model scored, kept for consented feedback export. */
    val features: List<Double>? = null,
)
