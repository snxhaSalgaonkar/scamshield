package com.scamguard.app.voice

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The call-audio helper. Android lets an app with an enabled accessibility service keep
 * capturing the microphone during a phone call, which ordinary apps cannot. This service
 * does nothing else: it asks for no events it uses, reads no screen content and performs no
 * actions. The user enables it in Settings → Accessibility.
 */
class CallAudioHelper : AccessibilityService() {
    override fun onServiceConnected() {
        connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var connected = false
            private set

        /** Whether the user has the helper switched on in system settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, CallAudioHelper::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
