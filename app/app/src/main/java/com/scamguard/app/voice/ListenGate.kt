package com.scamguard.app.voice

import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.WindowState

/**
 * Decides when to offer call listening. Pure Kotlin so it is unit tested.
 *
 * Offered once per call, when a call from an unknown or hidden number is connected and the
 * user has switched on "Ask to listen to suspicious calls". Known callers are never offered.
 * A first-time scam caller usually has no earlier signal, so every unknown call is offered
 * rather than waiting for a score that may never come without the caller's words.
 */
object ListenGate {
    fun shouldOffer(window: CallWindow, decision: Decision, voiceConsent: Boolean, alreadyOffered: Boolean): Boolean =
        voiceConsent && !alreadyOffered && window.isUnknown && !decision.gated && window.state == WindowState.ACTIVE
}
