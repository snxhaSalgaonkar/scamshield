package com.scamguard.app.collectors

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.util.Permissions

/**
 * Watches for remote-access apps being opened and new apps being installed, only while a
 * call window is open ([isWindowOpen]). Usage access is optional: without it, opened apps
 * are not seen. Only the known remote-access packages are looked up in usage events, and
 * only the packages Android lets this app see (the manifest's scoped queries) produce an
 * install broadcast, so not every sideloaded app can be detected.
 */
class AppMonitor(
    private val context: Context,
    private val bus: EventBus,
    private val isWindowOpen: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var receiver: BroadcastReceiver? = null
    private var lastPoll = 0L
    private val reported = mutableSetOf<String>()

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = onPackageAdded(intent)
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    /** Forgets which apps were already reported (a new window starts). */
    fun newWindow() {
        reported.clear()
        lastPoll = clock() - LOOKBACK_MS
    }

    /** Looks for remote-access apps brought to the foreground since the last poll. */
    fun poll() {
        if (!isWindowOpen() || !Permissions.hasUsageAccess(context)) return
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = clock()
        val from = maxOf(lastPoll, now - LOOKBACK_MS)
        lastPoll = now
        val events = try { usm.queryEvents(from, now) } catch (_: RuntimeException) { return }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName in KnownApps.remoteAccess && isForeground(e.eventType) && reported.add(e.packageName)) {
                bus.emit(SignalEvent.RemoteAppOpened(e.timeStamp, e.packageName))
            }
        }
    }

    private fun onPackageAdded(intent: Intent) {
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) || !isWindowOpen()) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val now = clock()
        bus.emit(SignalEvent.ApkInstalled(now, pkg, KnownApps.isSideloaded(installerOf(pkg))))
        if (pkg in KnownApps.remoteAccess && reported.add(pkg)) bus.emit(SignalEvent.RemoteAppOpened(now, pkg))
    }

    private fun installerOf(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(pkg)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun isForeground(type: Int) =
        type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED)

    private companion object {
        const val LOOKBACK_MS = 60_000L
    }
}
