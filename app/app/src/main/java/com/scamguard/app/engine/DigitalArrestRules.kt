package com.scamguard.app.engine

import com.scamguard.app.bus.ScreenCategory
import com.scamguard.app.bus.SignalEvent

/**
 * Extra evidence for video-call windows (WhatsApp, Skype and similar), where "digital
 * arrest" scams happen: a fake officer keeps the victim on a long video call, shows
 * warrants or court orders on screen, and pushes a transfer to a "verification account".
 *
 * The trained call model has no video features yet, so these rules are layered on top of
 * its decision. A red flag still needs two independent kinds of evidence: something
 * official-looking (caller name or on-screen text) plus money moving or a long call.
 * A long unknown video call on its own can reach a warning, never a red flag.
 */
object DigitalArrestRules {

    fun hits(input: RiskInput, c: RiskConfig): List<Hit> {
        val w = input.window
        if (!w.isVideoCall) return emptyList()
        return buildList {
            add(Hit(ReasonCode.VIDEO_CALL_UNKNOWN, c.videoUnknown))
            val talk = w.talkTimeAt(input.now)
            if (talk >= c.videoVeryLongMs) add(Hit(ReasonCode.VIDEO_CALL_LONG, c.videoLong + c.videoVeryLong))
            else if (talk >= c.videoLongMs) add(Hit(ReasonCode.VIDEO_CALL_LONG, c.videoLong))
            if (w.authorityName) add(Hit(ReasonCode.AUTHORITY_CALLER_NAME, c.authorityName))
            addAll(screen(w, c))
        }
    }

    /** On-screen evidence, capped at [RiskConfig.screenCap]. */
    private fun screen(w: CallWindow, c: RiskConfig): List<Hit> {
        val seen = w.events.filterIsInstance<SignalEvent.ScreenEvidence>().flatMap { it.categories }.toSet()
        var left = c.screenCap
        return seen.map {
            when (it) {
                ScreenCategory.LAW_ENFORCEMENT -> Hit(ReasonCode.SCREEN_LAW_ENFORCEMENT, c.screenLawEnforcement)
                ScreenCategory.LEGAL_THREAT -> Hit(ReasonCode.SCREEN_LEGAL_THREAT, c.screenLegalThreat)
                ScreenCategory.PAYMENT_DEMAND -> Hit(ReasonCode.SCREEN_PAYMENT_DEMAND, c.screenPaymentDemand)
            }
        }.sortedByDescending { it.points }.mapNotNull { hit ->
            val points = minOf(hit.points, left)
            left -= points
            if (points > 0) hit.copy(points = points) else null
        }
    }

    /** Official-looking caller and money or a long call: the digital-arrest pattern. */
    fun combo(w: CallWindow, hits: List<Hit>): Boolean {
        val authority = hits.any { it.code in AUTHORITY }
        val pressure = hits.any { it.code == ReasonCode.VIDEO_CALL_LONG } ||
            w.events.any { it is SignalEvent.DebitReceived || it is SignalEvent.OtpReceived || it is SignalEvent.RemoteAppOpened }
        return authority && pressure
    }

    /** Folds video-call evidence into [base]. Known callers and non-video windows are unchanged. */
    fun apply(input: RiskInput, base: Decision, c: RiskConfig = RiskConfig()): Decision {
        val w = input.window
        if (base.gated || !w.isVideoCall || Rules.userSaidSafe(w)) return base
        val hits = hits(input, c)
        if (hits.isEmpty()) return base

        val combo = combo(w, hits)
        var score = (base.score + hits.sumOf { it.points }).coerceIn(0, 100)
        if (combo) score = maxOf(score, c.comboFloor.coerceIn(0, 100))
        var level = RiskEngine.levelFor(score, c)
        // Never a red flag from video evidence alone unless the official-looking pattern is there.
        if (level == RiskLevel.RED_FLAG && !combo && base.level != RiskLevel.RED_FLAG) level = RiskLevel.WARNING

        val official = hits.any { it.code in AUTHORITY || it.code == ReasonCode.VIDEO_CALL_LONG }
        val scamType = when {
            level == RiskLevel.SAFE -> base.scamType
            official -> ScamType.DIGITAL_ARREST
            base.scamType == ScamType.NONE -> ScamType.SUSPICIOUS_CALL
            else -> base.scamType
        }
        val reasons = buildList {
            if (combo) add(ReasonCode.COMBO_VIDEO_AUTHORITY)
            addAll(hits.sortedByDescending { it.points }.map { it.code })
            addAll(base.reasonCodes)
        }.distinct()

        fun layer(l: Layer, before: Int) = (before + hits.filter { it.code.layer == l }.sumOf { it.points }).coerceIn(0, 100)
        return base.copy(
            score = score,
            level = level,
            l1 = layer(Layer.L1_VOICE, base.l1),
            l2 = layer(Layer.L2_CALLER, base.l2),
            l3 = layer(Layer.L3_CORRELATION, base.l3),
            scamType = scamType,
            reasonCodes = reasons,
            comboFired = base.comboFired || combo,
        )
    }

    private val AUTHORITY = setOf(
        ReasonCode.AUTHORITY_CALLER_NAME,
        ReasonCode.SCREEN_LAW_ENFORCEMENT,
        ReasonCode.SCREEN_LEGAL_THREAT,
        ReasonCode.SCREEN_PAYMENT_DEMAND,
    )
}
