package com.scamguard.app.voice

import android.content.Context
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.service.CallSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Watches the live call window and offers call listening through [ListenPrompt] per [ListenGate]. */
class VoiceGuard(
    private val context: Context,
    private val sessions: CallSessionManager,
    private val settings: SettingsRepository,
) {
    private var offeredFor: String? = null

    fun start(scope: CoroutineScope) = scope.launch {
        sessions.live.collect { live ->
            if (live == null || live.window.callEnded) {
                if (offeredFor != null) ListenPrompt.cancel(context)
                if (live == null) offeredFor = null
                return@collect
            }
            val s = settings.current()
            if (ListenGate.shouldOffer(live.window, live.decision, s.voiceLayer && s.protectionEnabled, offeredFor == live.window.id)) {
                offeredFor = live.window.id
                ListenPrompt.show(context)
            }
        }
    }
}
