package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WindowTrackerTest {
    private val config = RiskConfig()
    private var ids = 0
    private lateinit var tracker: WindowTracker

    @Before fun setUp() {
        ids = 0
        tracker = WindowTracker { "w${++ids}" }
    }

    @Test fun `bank OTP with no call belongs to no window and stays safe`() {
        assertNull(tracker.onEvent(otp(T0), config))
        assertNull(tracker.current)
    }

    @Test fun `state machine goes ringing, active, ended`() {
        assertEquals(WindowState.RINGING, tracker.onEvent(ringing(), config)!!.state)
        assertEquals(WindowState.ACTIVE, tracker.onEvent(SignalEvent.CallActive(T0 + 1000), config)!!.state)
        val ended = tracker.onEvent(SignalEvent.CallEnded(T0 + 2 * MIN, 2 * MIN), config)!!
        assertEquals(WindowState.ENDED, ended.state)
        assertEquals(2 * MIN, ended.durationMs)
    }

    @Test fun `window stays open 10 minutes after the call ends, then expires`() {
        tracker.onEvent(ringing(), config)
        tracker.onEvent(SignalEvent.CallEnded(T0 + MIN, MIN), config)
        val inside = tracker.onEvent(debit(T0 + 11 * MIN), config)!!
        assertEquals(RiskLevel.WARNING, evaluate(inside).level)
        assertNull(tracker.onEvent(debit(T0 + 11 * MIN + 1), config))
        assertNull(tracker.current)
    }

    @Test fun `expired window drops late debit from scoring`() {
        val w = window(ringing(), SignalEvent.CallEnded(T0 + MIN, MIN), debit(T0 + 12 * MIN))
        assertEquals(WindowState.CLOSED, w.state)
        assertEquals(10, evaluate(w, now = T0 + 12 * MIN).score)
    }

    @Test fun `new unknown call closes the previous window`() {
        val first = tracker.onEvent(ringing(), config)!!
        tracker.onEvent(SignalEvent.CallEnded(T0 + MIN, 0), config)
        val second = tracker.onEvent(ringing(ts = T0 + 2 * MIN, hash = "other"), config)!!
        assertNotEquals(first.id, second.id)
        assertEquals("other", tracker.current!!.numberHash)
    }

    @Test fun `known call does not replace an open unknown window`() {
        val first = tracker.onEvent(ringing(), config)!!
        tracker.onEvent(SignalEvent.CallEnded(T0 + MIN, 0), config)
        assertNull(tracker.onEvent(ringing(ts = T0 + 2 * MIN, unknown = false), config))
        assertEquals(first.id, tracker.onEvent(debit(T0 + 3 * MIN), config)!!.id)
    }

    @Test fun `second call ringing during an active call keeps the first window`() {
        val first = tracker.onEvent(ringing(), config)!!
        tracker.onEvent(SignalEvent.CallActive(T0 + 1000), config)
        assertNull(tracker.onEvent(ringing(ts = T0 + MIN, hash = "merge"), config))
        assertEquals(first.id, tracker.current!!.id)
    }

    @Test fun `known call with nothing open gets a gated window`() {
        val w = tracker.onEvent(ringing(unknown = false), config)!!
        val d = evaluate(tracker.onEvent(otp(T0 + MIN), config)!!)
        assertEquals(false, w.isUnknown)
        assertEquals(RiskLevel.SAFE, d.level)
        assertEquals(0, d.score)
    }
}
