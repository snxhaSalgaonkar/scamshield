package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import kotlin.math.abs

/**
 * OTP-theft evidence (L3), layered on top of the call model's decision the same way as
 * [VoiceRules]. The model only sees that an OTP SMS arrived; it cannot know the caller asked
 * for it, so on its own an OTP during a call stays well below a warning.
 *
 * | Evidence in the same call                                   | Floor          |
 * |-------------------------------------------------------------|----------------|
 * | caller asked for an OTP (voice) + an OTP SMS arrived        | red flag (75)  |
 * | ... and the caller also claimed to be a bank or an official | scam (90)      |
 * | an OTP SMS and a debit SMS within 5 minutes                  | red flag (75)  |
 *
 * Each needs two independent kinds of evidence (what was said and what the phone received, or
 * two different SMS), so a red flag never rests on one signal.
 */
object OtpRules {

    fun apply(input: RiskInput, base: Decision, c: RiskConfig = RiskConfig()): Decision {
        val w = input.window
        if (base.gated || Rules.userSaidSafe(w)) return base

        val start = w.answeredAt ?: w.startedAt
        val otps = w.events.filterIsInstance<SignalEvent.OtpReceived>().filter { it.ts >= start }
        if (otps.isEmpty()) return base
        val voice = VoiceRules.categories(w)
        val asked = input.voiceLayerEnabled && (VoiceCategory.OTP_REQUEST in voice || VoiceCategory.CARD_DETAILS in voice)
        val official = VoiceCategory.AUTHORITY_CLAIM in voice || VoiceCategory.ACCUSATION in voice
        val debits = w.events.filterIsInstance<SignalEvent.DebitReceived>()
        val otpThenDebit = otps.any { o -> debits.any { d -> abs(d.ts - o.ts) <= c.comboOtpDebitMs } }

        val combos = buildList {
            if (asked) add(ReasonCode.COMBO_OTP_ASKED to if (official) c.scamFloor else c.comboFloor)
            if (otpThenDebit) add(ReasonCode.COMBO_OTP_DEBIT to c.comboFloor)
        }
        if (combos.isEmpty()) return base

        val floor = combos.maxOf { it.second }.coerceIn(0, 100)
        val score = maxOf(base.score, floor)
        val level = maxOf(base.level, RiskEngine.levelFor(score, c))
        val scamType = when (base.scamType) {
            ScamType.NONE, ScamType.SUSPICIOUS_CALL -> ScamType.OTP_THEFT
            else -> base.scamType
        }
        val codes = combos.map { it.first } + ReasonCode.OTP_DURING_CALL
        return base.copy(
            score = score,
            level = level,
            l3 = maxOf(base.l3, floor),
            scamType = scamType,
            reasonCodes = (codes + base.reasonCodes).distinct(),
            comboFired = true,
        )
    }
}
