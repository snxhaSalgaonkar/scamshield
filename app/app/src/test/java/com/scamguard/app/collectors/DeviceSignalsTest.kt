package com.scamguard.app.collectors

import com.scamguard.app.bus.SignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSignalsTest {

    @Test fun `forwarding answers are read only when clear`() {
        assertEquals(true, ForwardingResponseParser.parse("Call forwarding: Voice: Activated. Forwarded to +91XXXXXXXX"))
        assertEquals(false, ForwardingResponseParser.parse("Call Forwarding Unconditional is not active"))
        assertEquals(false, ForwardingResponseParser.parse("Service has been deactivated"))
        assertNull(ForwardingResponseParser.parse("Connection problem or invalid MMI code."))
        assertNull(ForwardingResponseParser.parse(""))
        assertNull(ForwardingResponseParser.parse(null))
    }

    @Test fun `only a reliable off to on change is an event`() {
        assertTrue(ForwardingBaseline.compare(baseline = false, result = true).emitEnabled)
        assertFalse(ForwardingBaseline.compare(baseline = null, result = true).emitEnabled)
        assertFalse(ForwardingBaseline.compare(baseline = true, result = true).emitEnabled)
        assertEquals(ForwardingBaseline.Outcome(false, false), ForwardingBaseline.compare(false, null))
        assertEquals(false, ForwardingBaseline.compare(true, false).newBaseline)
    }

    @Test fun `store installs are not sideloaded`() {
        assertFalse(KnownApps.isSideloaded("com.android.vending"))
        assertTrue(KnownApps.isSideloaded(null))
        assertTrue(KnownApps.isSideloaded("com.google.android.packageinstaller"))
        assertTrue("com.anydesk.anydeskandroid" in KnownApps.remoteAccess)
    }

    @Test fun `short network blips are ignored`() {
        val t = NetworkLossTracker(minLossMs = 60_000)
        t.onLost(0)
        assertNull(t.tick(30_000))
        assertNull(t.onAvailable(40_000))
    }

    @Test fun `long loss is reported once`() {
        val t = NetworkLossTracker(minLossMs = 60_000)
        t.onLost(0)
        t.onLost(5_000)
        assertEquals(SignalEvent.NetworkLost(60_000, 60_000), t.tick(60_000))
        assertNull(t.tick(90_000))
        assertNull(t.onAvailable(120_000))
    }

    @Test fun `loss reported on return when no tick ran`() {
        val t = NetworkLossTracker(minLossMs = 60_000)
        t.onLost(0)
        assertEquals(SignalEvent.NetworkLost(90_000, 90_000), t.onAvailable(90_000))
    }
}
