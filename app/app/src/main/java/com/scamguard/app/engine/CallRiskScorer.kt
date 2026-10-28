package com.scamguard.app.engine

import com.scamguard.app.ml.CallFeatures
import com.scamguard.app.ml.ScamPredictionService
import kotlin.math.roundToInt

/**
 * Scores a call window with the trained on-device call model. This is the primary
 * detector. [RiskEngine]'s fixed-weight rules are used only when the model cannot be
 * loaded, and such decisions are marked [ScoreSource.RULE_FALLBACK] everywhere they are shown.
 *
 * Conventional code here only gates and words the result: known callers stay silent, a
 * red flag must not rest on a single signal (if removing the strongest piece of evidence
 * would drop the model's estimate below the warning threshold, the level is capped at
 * warning), and "This was me" silences the window.
 */
class CallRiskScorer(private val predictions: ScamPredictionService) {

    fun evaluate(input: RiskInput, config: RiskConfig = RiskConfig()): Decision {
        val w = input.window
        if (!w.isUnknown) return RiskEngine.evaluate(input, config)
        val x = CallFeatures.build(input, config)
        val p = predictions.scoreCall(x) ?: return RiskEngine.evaluate(input, config)

        val factors = p.factors.map { (name, delta) -> Factor(name, CallFeatures.REASONS[name], delta) }
        val prob = p.scamProbability
        var level = when {
            prob >= p.redFlagThreshold -> RiskLevel.RED_FLAG
            prob >= p.warningThreshold -> RiskLevel.WARNING
            else -> RiskLevel.SAFE
        }
        val strongest = factors.maxOfOrNull { it.delta } ?: 0.0
        if (level == RiskLevel.RED_FLAG && prob - strongest < p.warningThreshold) level = RiskLevel.WARNING
        if (Rules.userSaidSafe(w)) level = RiskLevel.SAFE

        val raising = factors.filter { it.delta > MIN_REASON_DELTA }.mapNotNull { it.code }
        val lowering = factors.filter { it.delta < -MIN_REASON_DELTA }.mapNotNull { it.code }
        val reasons = (raising + lowering).distinct()

        return Decision(
            windowId = w.id,
            evaluatedAt = input.now,
            score = (prob * 100).roundToInt().coerceIn(0, 100),
            level = level,
            l1 = layerPoints(factors, Layer.L1_VOICE),
            l2 = layerPoints(factors, Layer.L2_CALLER),
            l3 = layerPoints(factors, Layer.L3_CORRELATION),
            scamType = scamType(p.classProbabilities, level),
            reasonCodes = reasons,
            comboFired = raising.any { it.name.startsWith("COMBO_") },
            gated = false,
            source = ScoreSource.MODEL,
            probability = prob,
            modelVersion = p.modelVersion,
            experimental = p.experimental,
            factors = factors,
            features = x.toList(),
        )
    }

    /** Percentage points of scam probability each evidence layer added. */
    private fun layerPoints(factors: List<Factor>, layer: Layer): Int =
        (factors.filter { it.code?.layer == layer && it.delta > 0 }.sumOf { it.delta } * 100).roundToInt().coerceIn(0, 100)

    /** The model's most likely scam type, if it accounts for at least half the scam probability. */
    private fun scamType(probs: Map<String, Double>, level: RiskLevel): ScamType {
        if (level == RiskLevel.SAFE) return ScamType.NONE
        val scam = probs.filterKeys { it != "LEGIT" }
        val total = scam.values.sum()
        val top = scam.maxByOrNull { it.value } ?: return ScamType.SUSPICIOUS_CALL
        if (total <= 0 || top.value / total < TYPE_SHARE) return ScamType.SUSPICIOUS_CALL
        return ScamType.entries.firstOrNull { it.name == top.key } ?: ScamType.SUSPICIOUS_CALL
    }

    private companion object {
        const val MIN_REASON_DELTA = 0.01
        const val TYPE_SHARE = 0.5
    }
}
