package com.scamguard.app.ui.screens.simulator

import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskConfig
import com.scamguard.app.engine.RiskEngine
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.T0
import com.scamguard.app.engine.WindowTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Plays every simulator scenario through the same routing and scoring the session manager
 * uses for the simulator lane, so a scenario's expected level can never drift from reality.
 */
class ScenariosTest {
    private val config = RiskConfig()

    /** Mirrors CallSessionManager.process for the simulator lane; returns the last decision. */
    private fun play(scenario: Scenario): Decision? {
        val tracker = WindowTracker { "sim" }
        var last: Decision? = null
        scenario.events(T0).forEach { e ->
            val w = tracker.onEvent(e, config)
            last = if (w == null) {
                if (tracker.current == null) null else last
            } else {
                RiskEngine.evaluate(RiskInput(w, voiceLayerEnabled = false, now = e.ts, zone = ZoneOffset.UTC), config)
            }
        }
        return last
    }

    @Test fun `all seven scenarios reach their expected level`() {
        assertEquals(7, Scenarios.all.size)
        Scenarios.all.forEach { sc ->
            val level = play(sc)?.level ?: RiskLevel.SAFE
            assertEquals("scenario ${sc.id}", sc.expected, level)
        }
    }

    @Test fun `silent OTP theft warns but is not a red flag`() {
        val d = play(Scenarios.all.first { it.id == 1 })!!
        assertEquals(RiskLevel.WARNING, d.level)
        assertTrue(!d.comboFired)
    }

    @Test fun `combo scenarios fire a combo`() {
        listOf(2, 3, 4, 5).forEach { id ->
            assertTrue("scenario $id", play(Scenarios.all.first { it.id == id })!!.comboFired)
        }
    }

    @Test fun `saved contact scores zero`() {
        assertEquals(0, play(Scenarios.all.first { it.id == 6 })!!.score)
    }

    @Test fun `bank OTP without a call has no window at all`() {
        assertNull(play(Scenarios.all.first { it.id == 7 }))
    }
}
