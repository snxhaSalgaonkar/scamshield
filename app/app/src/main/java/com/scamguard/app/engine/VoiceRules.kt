package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory

/**
 * Spoken-content evidence (L1) for audio calls and the audio of video calls, from the
 * categories the transcript matcher found. Layered on top of the call model's decision,
 * which has no voice features, the same way [DigitalArrestRules] adds video evidence.
 *
 * A digital-arrest script moves through stages: someone claims to be police or an agency,
 * accuses the victim of a crime, threatens arrest and keeps them isolated on the call, then
 * demands money or codes. Each stage seen in the same call raises the floor:
 *
 * | Stages seen together                                           | Floor           |
 * |----------------------------------------------------------------|-----------------|
 * | authority + accusation                                         | warning (40)    |
 * | (authority or accusation) + (arrest or isolation); arrest + pay | red flag (75)   |
 * | credential request + (authority or a payment demand)           | red flag (75)   |
 * | (authority or accusation) + (arrest or isolation) + (money or codes) | scam (90) |
 *
 * A red flag never rests on one category: every red-flag combination needs two or more. The one
 * exception is the caller saying the words "digital arrest" (or डिजिटल अरेस्ट / गिरफ्तारी): no real
 * agency uses them, so hearing them on a call from an unknown number is a red flag (75) on its own.
 */
object VoiceRules {

    /** How far a digital-arrest script has got, for the live panel. */
    enum class Stage { NONE, AUTHORITY, ACCUSATION, THREAT, DEMAND }

    fun categories(w: CallWindow): Set<VoiceCategory> =
        w.events.filterIsInstance<SignalEvent.VoiceKeyword>().map { it.category }.toSet()

    private val DIGITAL_ARREST = Regex("digital(ly)? arrest|डिजिटल (अरेस्ट|गिरफ्तार)", RegexOption.IGNORE_CASE)

    /** True when the words "digital arrest" were heard in this call. */
    fun heardDigitalArrest(w: CallWindow): Boolean = w.events.filterIsInstance<SignalEvent.VoiceKeyword>()
        .any { it.category == VoiceCategory.ARREST_THREAT && it.phrase?.let(DIGITAL_ARREST::containsMatchIn) == true }

    fun stage(cats: Set<VoiceCategory>): Stage = when {
        cats.any { it in DEMAND || it in CREDENTIAL } && cats.any { it in OFFICIAL || it in THREAT } -> Stage.DEMAND
        cats.any { it in THREAT } -> Stage.THREAT
        VoiceCategory.ACCUSATION in cats -> Stage.ACCUSATION
        VoiceCategory.AUTHORITY_CLAIM in cats -> Stage.AUTHORITY
        else -> Stage.NONE
    }

    fun hits(cats: Set<VoiceCategory>, c: RiskConfig): List<Hit> = cats.map {
        when (it) {
            VoiceCategory.OTP_REQUEST -> Hit(ReasonCode.VOICE_OTP_REQUEST, c.voiceOtpRequest)
            VoiceCategory.ARREST_THREAT -> Hit(ReasonCode.VOICE_ARREST_THREAT, c.voiceArrestThreat)
            VoiceCategory.PAYMENT_PRESSURE -> Hit(ReasonCode.VOICE_PAYMENT_PRESSURE, c.voicePaymentPressure)
            VoiceCategory.REMOTE_APP_REQUEST -> Hit(ReasonCode.VOICE_REMOTE_APP, c.voiceRemoteApp)
            VoiceCategory.AUTHORITY_CLAIM -> Hit(ReasonCode.VOICE_AUTHORITY_CLAIM, c.voiceAuthority)
            VoiceCategory.ACCUSATION -> Hit(ReasonCode.VOICE_ACCUSATION, c.voiceAccusation)
            VoiceCategory.ISOLATION -> Hit(ReasonCode.VOICE_ISOLATION, c.voiceIsolation)
            VoiceCategory.CARD_DETAILS -> Hit(ReasonCode.VOICE_CARD_DETAILS, c.voiceCardDetails)
            VoiceCategory.LINK_REQUEST -> Hit(ReasonCode.VOICE_LINK_REQUEST, c.voiceLink)
        }
    }.sortedByDescending { it.points }

    /** Stage combinations present, most serious first, each with the score floor it sets. */
    fun combos(cats: Set<VoiceCategory>, c: RiskConfig): List<Pair<ReasonCode, Int>> {
        val official = cats.any { it in OFFICIAL }
        val threat = cats.any { it in THREAT }
        val demand = cats.any { it in DEMAND }
        val credential = cats.any { it in CREDENTIAL }
        return buildList {
            if (official && threat && (demand || credential)) add(ReasonCode.COMBO_VOICE_DIGITAL_ARREST to c.scamFloor)
            if ((official && threat) || (VoiceCategory.ARREST_THREAT in cats && demand)) {
                add(ReasonCode.COMBO_VOICE_THREAT to c.comboFloor)
            }
            if (credential && (VoiceCategory.AUTHORITY_CLAIM in cats || demand)) add(ReasonCode.COMBO_VOICE_IMPERSONATION to c.comboFloor)
            if (VoiceCategory.AUTHORITY_CLAIM in cats && VoiceCategory.ACCUSATION in cats) {
                add(ReasonCode.COMBO_VOICE_ACCUSED to c.warningThreshold)
            }
        }
    }

    /** Folds spoken evidence into [base]. Needs the voice layer; known callers stay silent. */
    fun apply(input: RiskInput, base: Decision, c: RiskConfig = RiskConfig()): Decision {
        val w = input.window
        if (!input.voiceLayerEnabled || base.gated || Rules.userSaidSafe(w)) return base
        val cats = categories(w)
        if (cats.isEmpty()) return base

        // The rule fallback already counted the original four categories; do not count them twice.
        val counted = Rules.voice(input, c).filter { it.code in base.reasonCodes }
        val fresh = hits(cats, c).filter { h -> counted.none { it.code == h.code } }
        var left = (c.voiceTotalCap - counted.sumOf { it.points }).coerceAtLeast(0)
        val added = fresh.mapNotNull { h ->
            val points = minOf(h.points, left)
            left -= points
            if (points > 0) h.copy(points = points) else null
        }
        val combos = combos(cats, c) +
            if (heardDigitalArrest(w)) listOf(ReasonCode.COMBO_VOICE_DIGITAL_ARREST to c.comboFloor) else emptyList()

        var score = (base.score + added.sumOf { it.points }).coerceIn(0, 100)
        combos.maxOfOrNull { it.second }?.let { score = maxOf(score, it.coerceIn(0, 100)) }
        var level = maxOf(base.level, RiskEngine.levelFor(score, c))
        val redCombo = combos.any { it.second >= c.comboFloor }
        if (level == RiskLevel.RED_FLAG && !redCombo && base.level != RiskLevel.RED_FLAG) level = RiskLevel.WARNING

        val codes = combos.map { it.first }.distinct()
        val scamType = when {
            level == RiskLevel.SAFE -> base.scamType
            ReasonCode.COMBO_VOICE_DIGITAL_ARREST in codes || ReasonCode.COMBO_VOICE_THREAT in codes ||
                ReasonCode.COMBO_VOICE_ACCUSED in codes -> ScamType.DIGITAL_ARREST
            ReasonCode.COMBO_VOICE_IMPERSONATION in codes && VoiceCategory.REMOTE_APP_REQUEST in cats -> ScamType.REMOTE_ACCESS_FRAUD
            ReasonCode.COMBO_VOICE_IMPERSONATION in codes -> ScamType.OTP_THEFT
            base.scamType == ScamType.NONE -> ScamType.SUSPICIOUS_CALL
            else -> base.scamType
        }
        return base.copy(
            score = score,
            level = level,
            l1 = (base.l1 + added.sumOf { it.points }).coerceIn(0, 100),
            scamType = scamType,
            reasonCodes = (codes + added.map { it.code } + base.reasonCodes).distinct(),
            comboFired = base.comboFired || codes.isNotEmpty(),
        )
    }

    private val OFFICIAL = setOf(VoiceCategory.AUTHORITY_CLAIM, VoiceCategory.ACCUSATION)
    private val THREAT = setOf(VoiceCategory.ARREST_THREAT, VoiceCategory.ISOLATION)
    private val DEMAND = setOf(VoiceCategory.PAYMENT_PRESSURE)
    private val CREDENTIAL = setOf(VoiceCategory.OTP_REQUEST, VoiceCategory.CARD_DETAILS, VoiceCategory.REMOTE_APP_REQUEST)
}
