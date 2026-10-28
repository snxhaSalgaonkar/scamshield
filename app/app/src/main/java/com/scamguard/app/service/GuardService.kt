package com.scamguard.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.scamguard.app.MainActivity
import com.scamguard.app.R
import com.scamguard.app.alerts.NotificationChannels
import com.scamguard.app.appContainer
import com.scamguard.app.collectors.AppMonitor
import com.scamguard.app.collectors.CallMonitor
import com.scamguard.app.collectors.ForwardingChecker
import com.scamguard.app.collectors.NetworkLossMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and therefore the call-state, network and
 * package listeners) alive, with the persistent "Scam Call Guard is protecting you"
 * notification. Restarted after reboot by [BootReceiver].
 *
 * While a call window is open it polls the app monitor (remote-access apps, needs usage
 * access) and the network-loss tracker. After an unknown call ends it asks the carrier
 * for the call-forwarding status once, so a switch to "on" during the call is caught.
 */
class GuardService : LifecycleService() {
    private lateinit var callMonitor: CallMonitor
    private lateinit var appMonitor: AppMonitor
    private lateinit var networkMonitor: NetworkLossMonitor
    private lateinit var forwardingChecker: ForwardingChecker

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val c = appContainer
        val windowOpen = { c.sessionManager.liveWindowId != null }
        callMonitor = CallMonitor(this, c.eventBus)
        appMonitor = AppMonitor(this, c.eventBus, windowOpen)
        networkMonitor = NetworkLossMonitor(this, c.eventBus, windowOpen)
        forwardingChecker = ForwardingChecker(this, c.eventBus, c.settings)
        appMonitor.start()
        networkMonitor.start()
        lifecycleScope.launch { watchWindows() }
    }

    private suspend fun watchWindows() {
        val c = appContainer
        if (System.currentTimeMillis() - c.settings.current().forwardingCheckedAt > FORWARDING_RECHECK_MS) {
            forwardingChecker.check()
        }
        var seenWindow: String? = null
        var forwardingCheckedFor: String? = null
        while (lifecycleScope.isActive) {
            val live = c.sessionManager.live.value
            val id = live?.window?.id
            if (id != null && id != seenWindow) { appMonitor.newWindow(); seenWindow = id }
            if (id != null) { appMonitor.poll(); networkMonitor.tick() }
            if (live != null && live.window.isUnknown && live.window.callEnded && forwardingCheckedFor != id) {
                forwardingCheckedFor = id
                forwardingChecker.check()
            }
            delay(POLL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Idempotent: also picks up permissions granted since the service started.
        callMonitor.start()
        return START_STICKY
    }

    override fun onDestroy() {
        callMonitor.stop()
        appMonitor.stop()
        networkMonitor.stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_body))
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 10_000L
        private const val FORWARDING_RECHECK_MS = 12 * 3_600_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (_: IllegalStateException) {
                // Background start not allowed right now; it starts next time the app is opened.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
