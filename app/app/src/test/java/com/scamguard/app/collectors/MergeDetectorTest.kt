package com.scamguard.app.collectors

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.PhoneCallState.IDLE
import com.scamguard.app.collectors.PhoneCallState.OFFHOOK
import com.scamguard.app.collectors.PhoneCallState.RINGING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeDetectorTest {
    private val d = MergeDetector(minRingMs = 1_000)

    @Test fun `answered incoming call gives active then ended with talk time`() {
        assertTrue(d.onState(RINGING, 0).isEmpty())
        assertTrue(d.firstRingStarted)
        assertEquals(listOf(SignalEvent.CallActive(5_000)), d.onState(OFFHOOK, 5_000))
        assertEquals(listOf(SignalEvent.CallEnded(65_000, 60_000)), d.onState(IDLE, 65_000))
    }

    @Test fun `missed call ends with zero duration`() {
        d.onState(RINGING, 0)
        assertEquals(listOf(SignalEvent.CallEnded(20_000, 0)), d.onState(IDLE, 20_000))
    }

    @Test fun `waiting call during an active call is reported as a second call`() {
        d.onState(RINGING, 0)
        d.onState(OFFHOOK, 1_000)
        assertTrue(d.onState(RINGING, 30_000).isEmpty())
        assertTrue(!d.firstRingStarted)
        assertEquals(listOf(SignalEvent.SecondCallConnected(35_000)), d.onState(OFFHOOK, 35_000))
    }

    @Test fun `a blip shorter than the minimum ring is ignored`() {
        d.onState(RINGING, 0)
        d.onState(OFFHOOK, 1_000)
        d.onState(RINGING, 2_000)
        assertTrue(d.onState(OFFHOOK, 2_500).isEmpty())
    }

    @Test fun `outgoing calls produce no events`() {
        assertTrue(d.onState(OFFHOOK, 0).isEmpty())
        assertTrue(d.onState(RINGING, 5_000).isEmpty())
        assertTrue(d.onState(OFFHOOK, 9_000).isEmpty())
        assertTrue(d.onState(IDLE, 20_000).isEmpty())
    }

    @Test fun `repeated states are ignored`() {
        d.onState(RINGING, 0)
        assertTrue(d.onState(RINGING, 10).isEmpty())
    }
}
