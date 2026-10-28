package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent

/**
 * Routes events to the current [CallWindow]. Pure Kotlin so the routing rules are unit
 * tested: a new unknown call closes the previous window; a known caller never replaces an
 * open window; a call that rings while another is live is left to the merge detector; and
 * events outside any window are ignored by the engine.
 */
class WindowTracker(private val newId: () -> String) {
    var current: CallWindow? = null
        private set

    /** Applies [event]; returns the window it belongs to, or null when it is outside any window. */
    fun onEvent(event: SignalEvent, config: RiskConfig): CallWindow? {
        val open = current?.takeIf { it.isOpenAt(event.ts, config) }
        if (open == null) current = null

        if (event is SignalEvent.CallRinging) {
            return when {
                open == null -> CallWindow.open(newId(), event).also { current = it }
                open.state == WindowState.RINGING || open.state == WindowState.ACTIVE -> null
                event.isUnknown || event.isHidden -> CallWindow.open(newId(), event).also { current = it }
                else -> null
            }
        }
        val next = (open ?: return null).accept(event, config)
        current = next
        return next
    }

    /** Forgets the current window (used by "delete all data" and the simulator). */
    fun reset() {
        current = null
    }
}
