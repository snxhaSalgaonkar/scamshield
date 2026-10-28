package com.scamguard.app.collectors

import com.scamguard.app.bus.SignalEvent

/** The three call states public Android APIs report for the whole phone. */
enum class PhoneCallState { IDLE, RINGING, OFFHOOK }

/**
 * Turns the phone's coarse call state into call events, including an approximation of
 * call merging. Pure Kotlin so the transitions are unit tested.
 *
 * Public APIs only say IDLE, RINGING or OFFHOOK for the whole phone. A second incoming
 * call while one is active shows up as OFFHOOK -> RINGING -> OFFHOOK. Android does not
 * say whether that waiting call was answered, merged or declined, so any waiting call
 * that rang for at least [minRingMs] and did not end the first call is reported as
 * [SignalEvent.SecondCallConnected]. This is an approximation: outgoing second calls,
 * carrier conferences and the second SIM on dual-SIM phones are not visible.
 */
class MergeDetector(private val minRingMs: Long = 1_000) {
    private var state = PhoneCallState.IDLE
    private var incoming = false
    private var answeredAt: Long? = null
    private var waitingSince: Long? = null

    /** True while the first call of a session is ringing, so callers can open a window. */
    var firstRingStarted = false
        private set

    /** Applies a state change and returns the events it implies. */
    fun onState(next: PhoneCallState, ts: Long): List<SignalEvent> {
        val prev = state
        if (next == prev) return emptyList()
        state = next
        firstRingStarted = false
        return when {
            prev == PhoneCallState.IDLE && next == PhoneCallState.RINGING -> {
                incoming = true
                firstRingStarted = true
                emptyList()
            }
            prev == PhoneCallState.IDLE && next == PhoneCallState.OFFHOOK -> {
                incoming = false // Outgoing call: not scored.
                emptyList()
            }
            prev == PhoneCallState.RINGING && next == PhoneCallState.OFFHOOK -> onRingToOffhook(ts)
            prev == PhoneCallState.OFFHOOK && next == PhoneCallState.RINGING -> {
                waitingSince = ts
                emptyList()
            }
            next == PhoneCallState.IDLE -> onIdle(ts)
            else -> emptyList()
        }
    }

    private fun onRingToOffhook(ts: Long): List<SignalEvent> {
        val since = waitingSince
        if (since != null) {
            // A waiting call finished ringing and the first call is still up.
            waitingSince = null
            return if (incoming && ts - since >= minRingMs) listOf(SignalEvent.SecondCallConnected(ts)) else emptyList()
        }
        if (!incoming) return emptyList()
        answeredAt = ts
        return listOf(SignalEvent.CallActive(ts))
    }

    private fun onIdle(ts: Long): List<SignalEvent> {
        val wasIncoming = incoming
        val duration = answeredAt?.let { ts - it } ?: 0
        incoming = false
        answeredAt = null
        waitingSince = null
        return if (wasIncoming) listOf(SignalEvent.CallEnded(ts, duration)) else emptyList()
    }
}
