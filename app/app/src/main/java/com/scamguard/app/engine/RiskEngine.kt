package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory

/**
 * Deterministic on-device risk scoring. Pure Kotlin: the same input always gives the
 * same [Decision]. Stays silent (score 0) unless the caller is unknown.
 */
object RiskEngine {

    fun evaluate(input: RiskInput, config: RiskConfig = RiskConfig()): Decision {
        val w = input.window
        if (!w.isUnknown) {
            return Decision(w.id, input.now, 0, RiskLevel.SAFE, 0, 0, 0, ScamType.NONE, emptyList(), comboFired = false, gated = true)
        }

        val l1Hits = Rules.voice(input, config)
        val l2Hits = Rules.caller(input, config)
        val l3Hits = Rules.correlation(input, config)
        val l1 = l1Hits.sumOf { it.points }
        val l2 = l2Hits.sumOf { it.points }
        val l3 = l3Hits.sumOf { it.points }

        val combos = if (Rules.userSaidSafe(w)) emptyList() else Rules.combos(w, config)
        var score = (l1 + l2 + l3).coerceIn(0, 100)
        if (combos.isNotEmpty()) score = maxOf(score, config.comboFloor.coerceIn(0, 100))

        val level = levelFor(score, config)
        val allHits = l1Hits + l2Hits + l3Hits
        val reasons = combos +
            allHits.filter { it.points > 0 }.sortedByDescending { it.points }.map { it.code } +
            allHits.filter { it.points < 0 }.map { it.code }

        return Decision(
            windowId = w.id,
            evaluatedAt = input.now,
            score = score,
            level = level,
            l1 = l1.coerceIn(0, 100),
            l2 = l2.coerceIn(0, 100),
            l3 = l3.coerceIn(0, 100),
            scamType = scamTypeFor(w, input.voiceLayerEnabled, level),
            reasonCodes = reasons,
            comboFired = combos.isNotEmpty(),
            gated = false,
        )
    }

    /** Under the warning threshold is silent, then warning, then red flag. */
    fun levelFor(score: Int, config: RiskConfig = RiskConfig()): RiskLevel = when {
        score >= config.redFlagThreshold -> RiskLevel.RED_FLAG
        score >= config.warningThreshold -> RiskLevel.WARNING
        else -> RiskLevel.SAFE
    }

    /** Picks the most specific scam pattern present, by priority. */
    fun scamTypeFor(w: CallWindow, voiceLayerEnabled: Boolean, level: RiskLevel): ScamType {
        val e = w.events
        return when {
            voiceLayerEnabled && e.any { it is SignalEvent.VoiceKeyword && it.category == VoiceCategory.ARREST_THREAT } ->
                ScamType.DIGITAL_ARREST
            e.any { it is SignalEvent.ForwardingChanged && it.enabled } -> ScamType.CALL_FORWARDING_HIJACK
            e.any { it is SignalEvent.SecondCallConnected } -> ScamType.CALL_MERGE_FRAUD
            e.any { it is SignalEvent.RemoteAppOpened || (it is SignalEvent.ApkInstalled && it.sideloaded) } ->
                ScamType.REMOTE_ACCESS_FRAUD
            e.any { it is SignalEvent.OtpReceived } -> ScamType.OTP_THEFT
            e.any { it is SignalEvent.DebitReceived } -> ScamType.MONEY_TRANSFER_PRESSURE
            level != RiskLevel.SAFE -> ScamType.SUSPICIOUS_CALL
            else -> ScamType.NONE
        }
    }
}
