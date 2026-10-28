package com.scamguard.app.engine

import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.ml.CallFeatures
import com.scamguard.app.ml.CallPrediction
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ml.ModelHealth
import com.scamguard.app.ml.ScamPredictionService
import com.scamguard.app.ml.UrlPrediction
import com.scamguard.app.ml.shippedModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CallRiskScorerTest {
    private val scorer = CallRiskScorer(shippedModels())

    private fun score(w: CallWindow, caller: CallerContext = CallerContext(), now: Long? = null) =
        scorer.evaluate(RiskInput(w, caller, TransactionHistory(), false, now ?: w.events.maxOf { it.ts }, ZoneOffset.UTC))

    @Test fun `the trained model, not the rules, produces the score`() {
        val w = window(ringing(), SignalEvent.CallActive(T0 + 5_000), otp(T0 + 2 * MIN), debit(T0 + 3 * MIN, payee = "p1"))
        val d = score(w)
        assertEquals(ScoreSource.MODEL, d.source)
        val p = assertNotNull(d.probability).let { d.probability!! }
        assertEquals((p * 100).toInt().toDouble(), d.score.toDouble(), 1.0)
        assertNotNull(d.modelVersion)
        assertTrue(d.experimental)
        assertEquals(CallFeatures.NAMES.size, d.features!!.size)
        // Explanations come from the model: removing OTP or debit lowers the probability.
        assertTrue(d.factors.first { it.feature == "otp_count" }.delta > 0)
    }

    @Test fun `known callers stay silent and are not scored by the model`() {
        val d = score(window(ringing(unknown = false), SignalEvent.CallActive(T0 + 5_000), otp()))
        assertTrue(d.gated)
        assertEquals(ScoreSource.GATED, d.source)
        assertEquals(RiskLevel.SAFE, d.level)
        assertNull(d.probability)
    }

    @Test fun `a single report alone can warn but never raise a red flag`() {
        val d = score(window(ringing()), CallerContext(reported = true))
        assertTrue(d.level != RiskLevel.RED_FLAG)
        assertTrue(ReasonCode.REPORTED_NUMBER in d.reasonCodes)
    }

    @Test fun `several independent signals can raise a red flag`() {
        val w = window(
            ringing(), SignalEvent.CallActive(T0 + 5_000),
            SignalEvent.ForwardingChanged(T0 + MIN, enabled = true), otp(T0 + 2 * MIN), SignalEvent.CallEnded(T0 + 3 * MIN, 3 * MIN),
        )
        val d = score(w)
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertEquals(ScamType.CALL_FORWARDING_HIJACK, d.scamType)
        assertEquals(ReasonCode.FORWARDING_ENABLED, d.reasonCodes.first())
    }

    @Test fun `this was me silences the window`() {
        val w = window(
            ringing(), SignalEvent.CallActive(T0 + 5_000), otp(T0 + MIN), debit(T0 + 2 * MIN, payee = "p"),
            SignalEvent.UserFeedback(T0 + 3 * MIN, FeedbackKind.THIS_WAS_ME),
        )
        assertEquals(RiskLevel.SAFE, score(w).level)
    }

    @Test fun `scam message outputs raise the call estimate`() {
        val base = window(ringing(), SignalEvent.CallActive(T0 + 5_000), SignalEvent.CallEnded(T0 + 20 * MIN, 20 * MIN))
        val withSms = window(
            ringing(), SignalEvent.CallActive(T0 + 5_000),
            SignalEvent.SmsAnalyzed(T0 + 2 * MIN, scamProbability = 0.95, impersonation = 0.8, topCategory = "IMPERSONATION_THREAT", flagged = true),
            SignalEvent.CallEnded(T0 + 20 * MIN, 20 * MIN),
        )
        assertTrue(score(withSms).probability!! > score(base).probability!!)
    }

    @Test fun `without the call model the labelled rule fallback is used`() {
        val unavailable = object : ScamPredictionService {
            override val health: StateFlow<ModelHealth> = MutableStateFlow(ModelHealth())
            override suspend fun ensureLoaded() = Unit
            override suspend fun analyzeMessage(text: String): MessagePrediction = error("unused")
            override suspend fun analyzeUrl(url: String): UrlPrediction = error("unused")
            override fun scoreCall(features: DoubleArray): CallPrediction? = null
        }
        val w = window(ringing(), SignalEvent.CallActive(T0 + 5_000), otp(T0 + MIN), debit(T0 + 2 * MIN))
        val d = CallRiskScorer(unavailable).evaluate(RiskInput(w, now = T0 + 2 * MIN, zone = ZoneOffset.UTC))
        assertEquals(ScoreSource.RULE_FALLBACK, d.source)
        assertNull(d.probability)
        assertEquals(evaluate(w).score, d.score)
    }
}
