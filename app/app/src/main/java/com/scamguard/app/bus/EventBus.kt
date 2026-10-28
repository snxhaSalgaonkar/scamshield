package com.scamguard.app.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * An event on the bus. [simulated] events come from the Scenario Simulator and are scored
 * in their own lane; [quiet] suppresses alerts (used by "Run all").
 */
data class Envelope(
    val event: SignalEvent,
    val simulated: Boolean,
    val quiet: Boolean,
    val seq: Long,
)

/** In-process event bus between collectors and the session manager. */
class EventBus {
    private val seq = AtomicLong()
    private val _events = MutableSharedFlow<Envelope>(extraBufferCapacity = 256)
    val events: SharedFlow<Envelope> = _events.asSharedFlow()

    /** Publishes [event] and returns its sequence number. Never suspends. */
    fun emit(event: SignalEvent, simulated: Boolean = false, quiet: Boolean = false): Long {
        val n = seq.incrementAndGet()
        _events.tryEmit(Envelope(event, simulated, quiet, n))
        return n
    }
}
