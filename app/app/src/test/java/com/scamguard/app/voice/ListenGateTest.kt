package com.scamguard.app.voice

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.RiskEngine
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.T0
import com.scamguard.app.engine.ringing
import com.scamguard.app.engine.window
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenGateTest {
    private val active = SignalEvent.CallActive(T0 + 5_000)
    private fun offer(w: com.scamguard.app.engine.CallWindow, consent: Boolean = true, offered: Boolean = false) =
        ListenGate.shouldOffer(w, RiskEngine.evaluate(RiskInput(w)), consent, offered)

    @Test fun `an answered unknown call is offered`() = assertTrue(offer(window(ringing(), active)))

    @Test fun `not while it is still ringing`() = assertFalse(offer(window(ringing())))

    @Test fun `never for known callers`() = assertFalse(offer(window(ringing(unknown = false), active)))

    @Test fun `never without consent`() = assertFalse(offer(window(ringing(), active), consent = false))

    @Test fun `only once per call`() = assertFalse(offer(window(ringing(), active), offered = true))

    @Test fun `not after the call ended`() =
        assertFalse(offer(window(ringing(), active, SignalEvent.CallEnded(T0 + 60_000, 55_000))))
}
