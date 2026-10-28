package com.scamguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scamguard.app.appContainer
import kotlinx.coroutines.launch

/**
 * Restarts [GuardService] after a reboot or app update, if the user finished onboarding
 * and left protection on. Some OEM battery managers block boot broadcasts until the app
 * is opened once; the Health screen links to the battery setting for that case.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                val s = container.settings.current()
                if (s.onboardingDone && s.protectionEnabled) GuardService.start(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
