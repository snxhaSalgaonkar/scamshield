package com.scamguard.app.ui.screens.simulator

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.engine.CallRiskScorer
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskConfig
import com.scamguard.app.engine.RiskEngine
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.ScamType
import com.scamguard.app.engine.ScoreSource
import com.scamguard.app.engine.T0
import com.scamguard.app.engine.WindowTracker
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.shippedModels
import com.scamguard.app.service.MessageGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Plays every simulator scenario through the same routing, message model and call model
 * the session manager uses for the simulator lane, so a scenario's expected level can
 * never drift from what the shipped models actually do.
 */
class ScenariosTest {
    private val config = RiskConfig()
    private val predictions = shippedModels()
    private val scorer = CallRiskScorer(predictions)

    private fun steps(scenario: Scenario): List<SignalEvent> {
        val messages = scenario.messages(T0).mapNotNull { (ts, text) ->
            MessageGuard.toEvent(runBlocking { predictions.analyzeMessage(text) }, ts)
        }
        val speech = scenario.speech(T0).flatMap { (ts, line) -> SpeechText.events(ts, line) }
        return (scenario.events(T0) + messages + speech).sortedBy { it.ts }
    }

    /** Mirrors CallSessionManager.process for the simulator lane; returns the last decision. */
    private fun play(scenario: Scenario, score: (RiskInput) -> Decision = { scorer.evaluate(it, config) }): Decision? {
        val tracker = WindowTracker { "sim" }
        var last: Decision? = null
        steps(scenario).forEach { e ->
            val w = tracker.onEvent(e, config)
            last = if (w == null) {
                if (tracker.current == null) null else last
            } else {
                score(RiskInput(w, voiceLayerEnabled = true, now = e.ts, zone = ZoneOffset.UTC))
            }
        }
        return last
    }

    @Test fun `all scenarios reach their expected level with the shipped models`() {
        assertEquals(16, Scenarios.all.size)
        Scenarios.all.forEach { sc ->
            val d = play(sc)
            assertEquals("scenario ${sc.id} (p=${d?.probability})", sc.expected, d?.level ?: RiskLevel.SAFE)
        }
    }

    @Test fun `scam scenarios are scored by the call model`() {
        listOf(1, 2, 3, 4, 5, 8, 9).forEach { id ->
            assertEquals("scenario $id", ScoreSource.MODEL, play(Scenarios.all.first { it.id == id })!!.source)
        }
    }

    @Test fun `demo messages go through the real message model`() = runBlocking {
        assertEquals(MessageVerdict.LIKELY_SCAM, predictions.analyzeMessage(Scenarios.DIGITAL_ARREST_SMS).verdict)
        assertEquals(MessageVerdict.LIKELY_SCAM, predictions.analyzeMessage(Scenarios.KYC_SMS).verdict)
        assertEquals(MessageVerdict.NO_STRONG_SIGNAL, predictions.analyzeMessage(Scenarios.DELIVERY_SMS).verdict)
    }

    @Test fun `forwarding and merge scenarios get their scam type`() {
        assertEquals(ScamType.CALL_FORWARDING_HIJACK, play(Scenarios.all.first { it.id == 3 })!!.scamType)
        assertEquals(ScamType.CALL_MERGE_FRAUD, play(Scenarios.all.first { it.id == 4 })!!.scamType)
        assertEquals(ScamType.DIGITAL_ARREST, play(Scenarios.all.first { it.id == 11 })!!.scamType)
    }

    @Test fun `spoken digital-arrest scripts reach the scam floor in both languages`() {
        listOf(12, 13).forEach { id ->
            val d = play(Scenarios.all.first { it.id == id })!!
            assertEquals("scenario $id", ScamType.DIGITAL_ARREST, d.scamType)
            assertTrue("scenario $id score ${d.score}", d.score >= 90)
        }
    }

    @Test fun `saved contact is gated and scores zero`() {
        val d = play(Scenarios.all.first { it.id == 6 })!!
        assertEquals(0, d.score)
        assertTrue(d.gated)
    }

    @Test fun `bank OTP without a call has no window at all`() {
        assertNull(play(Scenarios.all.first { it.id == 7 }))
    }

    @Test fun `rule fallback still gives its original outcomes for the first seven scenarios`() {
        val legacy = mapOf(1 to RiskLevel.WARNING, 2 to RiskLevel.RED_FLAG, 3 to RiskLevel.RED_FLAG, 4 to RiskLevel.RED_FLAG,
            5 to RiskLevel.RED_FLAG, 6 to RiskLevel.SAFE, 7 to RiskLevel.SAFE)
        legacy.forEach { (id, level) ->
            val d = play(Scenarios.all.first { it.id == id }) { RiskEngine.evaluate(it, config) }
            assertEquals("scenario $id", level, d?.level ?: RiskLevel.SAFE)
        }
    }
}
