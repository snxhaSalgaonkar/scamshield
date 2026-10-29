package com.scamguard.app.engine

import com.scamguard.app.bus.CallChannel
import com.scamguard.app.bus.SignalEvent

/**
 * Window lifecycle. Having no window at all is the IDLE state:
 * IDLE -> RINGING -> ACTIVE -> ENDED (open for [RiskConfig.windowAfterEndMs]) -> CLOSED.
 */
enum class WindowState { RINGING, ACTIVE, ENDED, CLOSED }

/**
 * The events around one incoming call. Immutable: [accept] returns the next window.
 * Debit SMS often arrive after a scammer hangs up, so the window stays open for a while
 * after the call ends.
 */
data class CallWindow(
    val id: String,
    val state: WindowState,
    val numberHash: String?,
    val prefix: String,
    val mask: String,
    val isUnknown: Boolean,
    val isInternational: Boolean,
    val isHidden: Boolean,
    val startedAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val durationMs: Long? = null,
    val events: List<SignalEvent> = emptyList(),
    val channel: CallChannel = CallChannel.CELLULAR,
    val authorityName: Boolean = false,
) {
    val isVideoCall: Boolean get() = channel == CallChannel.VIDEO_APP

    /** Whether an event at [ts] still belongs to this window. */
    fun isOpenAt(ts: Long, config: RiskConfig): Boolean =
        state != WindowState.CLOSED && (endedAt == null || ts - endedAt <= config.windowAfterEndMs)

    val callEnded: Boolean get() = state == WindowState.ENDED || state == WindowState.CLOSED

    /** Talk time so far, or the final duration once the call has ended. */
    fun talkTimeAt(now: Long): Long = durationMs ?: answeredAt?.let { (now - it).coerceAtLeast(0) } ?: 0

    /**
     * Applies [event]. Returns a CLOSED window (without the event) if the window has
     * expired. New ringing calls are handled by the session manager, not here.
     */
    fun accept(event: SignalEvent, config: RiskConfig): CallWindow {
        if (!isOpenAt(event.ts, config)) return copy(state = WindowState.CLOSED)
        val next = copy(events = events + event)
        return when (event) {
            is SignalEvent.CallActive ->
                if (state == WindowState.RINGING) next.copy(state = WindowState.ACTIVE, answeredAt = event.ts) else next
            is SignalEvent.CallEnded ->
                if (state == WindowState.ENDED) next
                else next.copy(state = WindowState.ENDED, endedAt = event.ts, durationMs = event.durationMs)
            else -> next
        }
    }

    /** Marks the window closed, e.g. when a new unknown call starts. */
    fun close(): CallWindow = copy(state = WindowState.CLOSED)

    companion object {
        /** Opens a window for a ringing call. */
        fun open(id: String, ringing: SignalEvent.CallRinging) = CallWindow(
            id = id,
            state = WindowState.RINGING,
            numberHash = ringing.numberHash,
            prefix = ringing.prefix,
            mask = ringing.mask,
            isUnknown = ringing.isUnknown || ringing.isHidden,
            isInternational = ringing.isInternational,
            isHidden = ringing.isHidden,
            startedAt = ringing.ts,
            events = listOf(ringing),
            channel = ringing.channel,
            authorityName = ringing.authorityName,
        )
    }
}
