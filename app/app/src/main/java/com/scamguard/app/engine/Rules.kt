package com.scamguard.app.engine

import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Everything the engine needs to score one window. */
data class RiskInput(
    val window: CallWindow,
    val caller: CallerContext = CallerContext(),
    val history: TransactionHistory = TransactionHistory(),
    val voiceLayerEnabled: Boolean = false,
    val now: Long = window.events.maxOfOrNull { it.ts } ?: window.startedAt,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** The individual scoring rules. Each returns the hits it produces for a window. */
internal object Rules {

    fun caller(input: RiskInput, c: RiskConfig): List<Hit> = buildList {
        val w = input.window
        add(Hit(ReasonCode.UNKNOWN_CALLER, c.unknownCaller))
        if (w.isInternational) add(Hit(ReasonCode.INTERNATIONAL_OR_VOIP, c.internationalOrVoip))
        if (input.caller.callsLastHour >= c.repeatedCallsCount) add(Hit(ReasonCode.REPEATED_CALLS, c.repeatedCalls))
        if (input.caller.reported || w.has<SignalEvent.UserFeedback> { it.kind == FeedbackKind.CONFIRMED_SCAM }) {
            add(Hit(ReasonCode.REPORTED_NUMBER, c.reportedNumber))
        }
        if (w.talkTimeAt(input.now) > c.longCallMs) add(Hit(ReasonCode.LONG_CALL, c.longCall))
        if (isOddHour(w.startedAt, input.zone, c)) add(Hit(ReasonCode.ODD_HOURS, c.oddHours))
        if (input.caller.knownBankNumber) add(Hit(ReasonCode.KNOWN_BANK_NUMBER, c.knownBankNumber))
        if (userSaidSafe(w)) add(Hit(ReasonCode.USER_MARKED_SAFE, c.userMarkedSafe))
    }

    fun correlation(input: RiskInput, c: RiskConfig): List<Hit> = buildList {
        val w = input.window
        if (w.has<SignalEvent.OtpReceived>()) add(Hit(ReasonCode.OTP_DURING_CALL, c.otpDuringWindow))
        if (w.has<SignalEvent.DebitReceived>()) add(Hit(ReasonCode.DEBIT_DURING_CALL, c.debitDuringWindow))
        if (w.has<SignalEvent.ForwardingChanged> { it.enabled }) add(Hit(ReasonCode.FORWARDING_ENABLED, c.forwardingOn))
        if (w.has<SignalEvent.SecondCallConnected>()) add(Hit(ReasonCode.CALL_MERGED, c.callMerge))
        if (w.has<SignalEvent.RemoteAppOpened>()) add(Hit(ReasonCode.REMOTE_ACCESS_APP, c.remoteAppOpened))
        if (w.has<SignalEvent.ApkInstalled> { it.sideloaded }) add(Hit(ReasonCode.SIDELOADED_APK, c.sideloadedApk))
        if (linkThenInstall(w)) add(Hit(ReasonCode.LINK_THEN_INSTALL, c.linkThenInstall))
        if (w.has<SignalEvent.NetworkLost>()) add(Hit(ReasonCode.NETWORK_LOST, c.networkLoss))
        addAll(transactions(input, c))
    }

    /** Transaction analysis, capped at [RiskConfig.txnCap] in total. */
    fun transactions(input: RiskInput, c: RiskConfig): List<Hit> {
        val debits = input.window.events.filterIsInstance<SignalEvent.DebitReceived>().sortedBy { it.ts }
        if (debits.isEmpty()) return emptyList()
        val raw = buildList {
            val median = input.history.medianDebitPaise
            if (median != null && debits.any { it.amountPaise > median * c.farAboveMultiplier && it.amountPaise > c.farAboveMinPaise }) {
                add(Hit(ReasonCode.AMOUNT_FAR_ABOVE_NORMAL, c.txnFarAboveNormal))
            }
            if (debits.any { it.payeeHash != null && it.payeeHash !in input.history.knownPayees }) {
                add(Hit(ReasonCode.FIRST_TIME_PAYEE, c.txnFirstTimePayee))
            }
            if (debits.zipWithNext().any { (a, b) -> b.ts - a.ts <= c.multipleDebitsMs }) {
                add(Hit(ReasonCode.MULTIPLE_DEBITS, c.txnMultipleDebits))
            }
            if (debits.any { it.balancePaise != null && it.balancePaise <= c.nearZeroBalancePaise }) {
                add(Hit(ReasonCode.BALANCE_NEAR_ZERO, c.txnBalanceNearZero))
            }
        }
        return capHits(raw, c.txnCap)
    }

    /** Voice keyword evidence (L1), only when the voice layer is on; capped. */
    fun voice(input: RiskInput, c: RiskConfig): List<Hit> {
        if (!input.voiceLayerEnabled) return emptyList()
        val categories = input.window.events.filterIsInstance<SignalEvent.VoiceKeyword>().map { it.category }.toSet()
        val raw = categories.map {
            when (it) {
                VoiceCategory.OTP_REQUEST -> Hit(ReasonCode.VOICE_OTP_REQUEST, c.voiceOtpRequest)
                VoiceCategory.ARREST_THREAT -> Hit(ReasonCode.VOICE_ARREST_THREAT, c.voiceArrestThreat)
                VoiceCategory.PAYMENT_PRESSURE -> Hit(ReasonCode.VOICE_PAYMENT_PRESSURE, c.voicePaymentPressure)
                VoiceCategory.REMOTE_APP_REQUEST -> Hit(ReasonCode.VOICE_REMOTE_APP, c.voiceRemoteApp)
            }
        }.sortedByDescending { it.points }
        return capHits(raw, c.voiceCap)
    }

    /** Combination rules; any one of them floors the score at [RiskConfig.comboFloor]. */
    fun combos(w: CallWindow, c: RiskConfig): List<ReasonCode> = buildList {
        val otps = w.events.filterIsInstance<SignalEvent.OtpReceived>()
        val debits = w.events.filterIsInstance<SignalEvent.DebitReceived>()
        if (otps.any { o -> debits.any { d -> abs(d.ts - o.ts) <= c.comboOtpDebitMs } }) add(ReasonCode.COMBO_OTP_DEBIT)
        if (otps.isNotEmpty() && w.has<SignalEvent.ForwardingChanged> { it.enabled }) add(ReasonCode.COMBO_FORWARDING_OTP)
        if (otps.isNotEmpty() && w.has<SignalEvent.SecondCallConnected>()) add(ReasonCode.COMBO_MERGE_OTP)
        if (debits.isNotEmpty() && w.has<SignalEvent.RemoteAppOpened>()) add(ReasonCode.COMBO_REMOTE_DEBIT)
    }

    fun userSaidSafe(w: CallWindow): Boolean =
        w.has<SignalEvent.UserFeedback> { it.kind == FeedbackKind.THIS_WAS_ME }

    private fun linkThenInstall(w: CallWindow): Boolean {
        val firstLink = w.events.filterIsInstance<SignalEvent.LinkReceived>().minOfOrNull { it.ts } ?: return false
        return w.events.filterIsInstance<SignalEvent.ApkInstalled>().any { it.ts >= firstLink }
    }

    private fun isOddHour(ts: Long, zone: ZoneId, c: RiskConfig): Boolean {
        val hour = Instant.ofEpochMilli(ts).atZone(zone).hour
        return hour >= c.oddHourStart || hour < c.oddHourEnd
    }

    /** Keeps hits in order until the cap is reached; the last one may be trimmed. */
    private fun capHits(hits: List<Hit>, cap: Int): List<Hit> {
        var left = cap
        return hits.mapNotNull { hit ->
            val points = minOf(hit.points, left)
            left -= points
            if (points > 0) hit.copy(points = points) else null
        }
    }

    private inline fun <reified T : SignalEvent> CallWindow.has(predicate: (T) -> Boolean = { true }): Boolean =
        events.any { it is T && predicate(it) }
}
