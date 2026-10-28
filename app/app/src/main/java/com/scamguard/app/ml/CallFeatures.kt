package com.scamguard.app.ml

import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.RiskConfig
import com.scamguard.app.engine.RiskInput
import java.time.Instant
import kotlin.math.abs

/**
 * Builds the call model's feature vector from a call window, in the order of
 * ml/scamml/callfeat.py FEATURES. Only signals the phone actually observed are used;
 * anything not observed is the feature's "absent" value. No call audio exists here.
 */
object CallFeatures {
    val NAMES = listOf(
        "is_hidden", "is_international", "number_unavailable", "calls_last_hour", "reported_number",
        "known_bank_number", "user_marked_safe", "user_reported_scam", "answered", "talk_minutes", "odd_hour",
        "otp_count", "otp_untrusted_sender", "debit_count", "debit_amount_ratio", "history_available",
        "first_time_payee", "balance_near_zero", "forwarding_enabled", "second_call_merged", "remote_app_opened",
        "sideloaded_install", "link_received", "link_then_install", "network_lost", "otp_then_debit_5min",
        "sms_analyzed", "max_sms_scam_prob", "sms_impersonation", "sms_credential", "sms_payment",
        "sms_remote_access", "max_url_prob",
    )

    /** Evidence a feature stands for, for the "Why" list; null for context-only features. */
    val REASONS: Map<String, ReasonCode> = mapOf(
        "is_hidden" to ReasonCode.HIDDEN_NUMBER,
        "is_international" to ReasonCode.INTERNATIONAL_OR_VOIP,
        "calls_last_hour" to ReasonCode.REPEATED_CALLS,
        "reported_number" to ReasonCode.REPORTED_NUMBER,
        "user_reported_scam" to ReasonCode.REPORTED_NUMBER,
        "known_bank_number" to ReasonCode.KNOWN_BANK_NUMBER,
        "user_marked_safe" to ReasonCode.USER_MARKED_SAFE,
        "talk_minutes" to ReasonCode.LONG_CALL,
        "odd_hour" to ReasonCode.ODD_HOURS,
        "otp_count" to ReasonCode.OTP_DURING_CALL,
        "otp_untrusted_sender" to ReasonCode.OTP_DURING_CALL,
        "debit_count" to ReasonCode.DEBIT_DURING_CALL,
        "debit_amount_ratio" to ReasonCode.AMOUNT_FAR_ABOVE_NORMAL,
        "first_time_payee" to ReasonCode.FIRST_TIME_PAYEE,
        "balance_near_zero" to ReasonCode.BALANCE_NEAR_ZERO,
        "forwarding_enabled" to ReasonCode.FORWARDING_ENABLED,
        "second_call_merged" to ReasonCode.CALL_MERGED,
        "remote_app_opened" to ReasonCode.REMOTE_ACCESS_APP,
        "sideloaded_install" to ReasonCode.SIDELOADED_APK,
        "link_received" to ReasonCode.LINK_RECEIVED,
        "link_then_install" to ReasonCode.LINK_THEN_INSTALL,
        "network_lost" to ReasonCode.NETWORK_LOST,
        "otp_then_debit_5min" to ReasonCode.COMBO_OTP_DEBIT,
        "max_sms_scam_prob" to ReasonCode.SCAM_MESSAGE,
        "sms_impersonation" to ReasonCode.SMS_IMPERSONATION,
        "sms_credential" to ReasonCode.SMS_CREDENTIAL_REQUEST,
        "sms_payment" to ReasonCode.SMS_PAYMENT_REQUEST,
        "sms_remote_access" to ReasonCode.SMS_REMOTE_ACCESS,
        "max_url_prob" to ReasonCode.SUSPICIOUS_LINK,
    )

    private const val MINUTE = 60_000.0
    private const val MAX_TALK_MINUTES = 240.0

    fun build(input: RiskInput, config: RiskConfig = RiskConfig()): DoubleArray {
        val w = input.window
        val e = w.events
        val f = HashMap<String, Double>()
        fun flag(name: String, on: Boolean) { f[name] = if (on) 1.0 else 0.0 }

        flag("is_hidden", w.isHidden)
        flag("is_international", w.isInternational)
        flag("number_unavailable", w.numberHash == null)
        f["calls_last_hour"] = if (w.numberHash == null) 1.0 else input.caller.callsLastHour.coerceIn(1, 10).toDouble()
        flag("reported_number", input.caller.reported)
        flag("known_bank_number", input.caller.knownBankNumber)
        flag("user_marked_safe", e.any { it is SignalEvent.UserFeedback && it.kind == FeedbackKind.THIS_WAS_ME })
        flag("user_reported_scam", e.any { it is SignalEvent.UserFeedback && it.kind == FeedbackKind.CONFIRMED_SCAM })
        flag("answered", w.answeredAt != null)
        f["talk_minutes"] = (w.talkTimeAt(input.now) / MINUTE).coerceAtMost(MAX_TALK_MINUTES)
        val hour = Instant.ofEpochMilli(w.startedAt).atZone(input.zone).hour
        flag("odd_hour", hour >= config.oddHourStart || hour < config.oddHourEnd)

        val otps = e.filterIsInstance<SignalEvent.OtpReceived>()
        val debits = e.filterIsInstance<SignalEvent.DebitReceived>()
        f["otp_count"] = otps.size.coerceAtMost(5).toDouble()
        flag("otp_untrusted_sender", otps.any { !it.senderTrusted })
        f["debit_count"] = debits.size.coerceAtMost(5).toDouble()
        val median = input.history.medianDebitPaise
        flag("history_available", debits.isNotEmpty() && median != null && median > 0)
        f["debit_amount_ratio"] = if (debits.isNotEmpty() && median != null && median > 0) {
            (debits.maxOf { it.amountPaise }.toDouble() / median).coerceAtMost(50.0)
        } else 0.0
        flag("first_time_payee", debits.any { it.payeeHash != null && it.payeeHash !in input.history.knownPayees })
        flag("balance_near_zero", debits.any { it.balancePaise != null && it.balancePaise <= config.nearZeroBalancePaise })
        flag("forwarding_enabled", e.any { it is SignalEvent.ForwardingChanged && it.enabled })
        flag("second_call_merged", e.any { it is SignalEvent.SecondCallConnected })
        flag("remote_app_opened", e.any { it is SignalEvent.RemoteAppOpened })
        flag("sideloaded_install", e.any { it is SignalEvent.ApkInstalled && it.sideloaded })
        val sms = e.filterIsInstance<SignalEvent.SmsAnalyzed>()
        val firstLink = (e.filterIsInstance<SignalEvent.LinkReceived>().map { it.ts } + sms.filter { it.linkCount > 0 }.map { it.ts }).minOrNull()
        flag("link_received", firstLink != null)
        flag("link_then_install", firstLink != null && e.any { it is SignalEvent.ApkInstalled && it.ts >= firstLink })
        flag("network_lost", e.any { it is SignalEvent.NetworkLost })
        flag("otp_then_debit_5min", otps.any { o -> debits.any { d -> abs(d.ts - o.ts) <= config.comboOtpDebitMs } })

        val scored = sms.filterNot { it.outOfDistribution }
        flag("sms_analyzed", sms.isNotEmpty())
        f["max_sms_scam_prob"] = scored.maxOfOrNull { it.scamProbability } ?: 0.0
        f["sms_impersonation"] = scored.maxOfOrNull { it.impersonation } ?: 0.0
        f["sms_credential"] = scored.maxOfOrNull { it.credential } ?: 0.0
        f["sms_payment"] = scored.maxOfOrNull { it.payment } ?: 0.0
        f["sms_remote_access"] = scored.maxOfOrNull { it.remoteAccess } ?: 0.0
        f["max_url_prob"] = sms.mapNotNull { it.maxUrlProbability }.maxOrNull() ?: 0.0

        return DoubleArray(NAMES.size) { f.getValue(NAMES[it]) }
    }
}
