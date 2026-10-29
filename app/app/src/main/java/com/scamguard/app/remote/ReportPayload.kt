package com.scamguard.app.remote

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.Layer
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.ScamType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Builds one `reports` row of the authority dashboard (website/supabase/001_schema.sql) from a
 * flagged call: the caller's number, scores, scam type, detected tactics and reasons. Never audio,
 * transcript, SMS text or the user's own details. `device_id` is added by [AuthorityServer].
 */
object ReportPayload {
    const val WARN_SCORE = 40
    const val HIGH_SCORE = 75
    private const val MAX_PHRASES = 10
    private const val MAX_REASONS = 8

    /** What a report says about one call, from the live window or from the saved decision. */
    data class Call(
        val startedAt: Long,
        val level: RiskLevel,
        val score: Int,
        val l1: Int,
        val l2: Int,
        val l3: Int,
        val scamType: ScamType,
        val reasonCodes: List<ReasonCode>,
        val phrases: List<String> = emptyList(),
        /** The user tapped "Report scam". */
        val userReported: Boolean = false,
    )

    fun call(window: CallWindow, peak: Decision, userReported: Boolean) = Call(
        window.startedAt, peak.level, peak.score, peak.l1, peak.l2, peak.l3, peak.scamType, peak.reasonCodes,
        phrases(window), userReported,
    )

    /** A call the user reported after it ended: only the saved decision is left. */
    fun call(e: DecisionEntity) = Call(
        startedAt = e.startedAt,
        level = enumOr(e.peakLevel, RiskLevel.SAFE),
        score = e.peakScore,
        l1 = e.l1, l2 = e.l2, l3 = e.l3,
        scamType = enumOr(e.scamType, ScamType.SUSPICIOUS_CALL),
        reasonCodes = e.reasonCodes.split(',').mapNotNull { c -> ReasonCode.entries.firstOrNull { it.name == c.trim() } },
        userReported = true,
    )

    fun build(number: String, c: Call, reasonText: (ReasonCode) -> String): JSONObject {
        val reasons = buildList {
            if (c.userReported) add(USER_REPORTED)
            addAll(c.reasonCodes.map(reasonText))
        }.distinct().take(MAX_REASONS)
        return JSONObject()
            .put("caller_number", number)
            .put("risk_score", riskScore(c))
            .put("l1_score", c.l1.coerceIn(0, 100))
            .put("l2_score", c.l2.coerceIn(0, 100))
            .put("l3_score", c.l3.coerceIn(0, 100))
            .put("scam_type", scamType(c.scamType))
            .put("matched_phrases", JSONArray(c.phrases.take(MAX_PHRASES)))
            .put("reasons", JSONArray(reasons))
            .put("detected_via", JSONArray(detectedVia(c.reasonCodes, c.userReported)))
            .put("action_taken", "warn")
            .put("call_started_at", Instant.ofEpochMilli(c.startedAt).toString())
    }

    /**
     * The dashboard bands scores (40 warning, 75 high); keep the score inside the band the phone
     * decided. A call the user reported as a scam is at least a red flag.
     */
    fun riskScore(c: Call): Int = when {
        c.level == RiskLevel.RED_FLAG || c.userReported -> c.score.coerceIn(HIGH_SCORE, 100)
        c.level == RiskLevel.WARNING -> c.score.coerceIn(WARN_SCORE, HIGH_SCORE - 1)
        else -> c.score.coerceIn(0, WARN_SCORE - 1)
    }

    fun scamType(t: ScamType): String = when (t) {
        ScamType.DIGITAL_ARREST -> "digital_arrest"
        ScamType.OTP_THEFT, ScamType.CALL_FORWARDING_HIJACK, ScamType.CALL_MERGE_FRAUD -> "otp_theft"
        ScamType.REMOTE_ACCESS_FRAUD -> "remote_access"
        ScamType.MONEY_TRANSFER_PRESSURE, ScamType.SUSPICIOUS_CALL, ScamType.NONE -> "other"
    }

    /** The few words that matched each scam phrase heard on the call (never the transcript). */
    fun phrases(w: CallWindow): List<String> =
        w.events.filterIsInstance<SignalEvent.VoiceKeyword>().mapNotNull { it.phrase?.trim()?.takeIf(String::isNotEmpty) }
            .distinct().take(MAX_PHRASES)

    fun detectedVia(codes: List<ReasonCode>, userReported: Boolean = false): List<String> = buildList {
        codes.forEach { code ->
            add(
                when {
                    code.layer == Layer.L1_VOICE -> "voice"
                    code == ReasonCode.REPORTED_NUMBER -> "reputation"
                    code in SMS -> "sms"
                    else -> "correlation"
                }
            )
        }
        if (userReported) add("manual_report")
    }.distinct()

    private const val USER_REPORTED = "Reported as a scam by the user"

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private val SMS = setOf(
        ReasonCode.OTP_DURING_CALL, ReasonCode.DEBIT_DURING_CALL, ReasonCode.LINK_RECEIVED, ReasonCode.SCAM_MESSAGE,
        ReasonCode.SMS_IMPERSONATION, ReasonCode.SMS_CREDENTIAL_REQUEST, ReasonCode.SMS_PAYMENT_REQUEST,
        ReasonCode.SMS_REMOTE_ACCESS, ReasonCode.SUSPICIOUS_LINK,
    )
}
