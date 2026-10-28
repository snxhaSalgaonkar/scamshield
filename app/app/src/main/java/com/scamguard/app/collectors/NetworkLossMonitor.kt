package com.scamguard.app.collectors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.scamguard.app.bus.EventBus

/**
 * Watches mobile data and reports a long, sudden loss during an open call window. Mobile
 * data loss can come from a SIM swap, but far more often from coverage, so the engine
 * treats it as weak evidence. If mobile data is off, nothing is reported.
 */
class NetworkLossMonitor(
    private val context: Context,
    private val bus: EventBus,
    private val isWindowOpen: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tracker = NetworkLossTracker()
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = synchronized(tracker) {
                if (isWindowOpen()) tracker.onLost(clock())
            }

            override fun onAvailable(network: Network) = synchronized(tracker) {
                tracker.onAvailable(clock())?.let { if (isWindowOpen()) bus.emit(it) }
                Unit
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build()
        try {
            cm.registerNetworkCallback(request, cb)
            callback = cb
        } catch (_: RuntimeException) {
            // SecurityException without ACCESS_NETWORK_STATE, or too many callbacks: stay off.
        }
    }

    /** Reports a loss that has lasted long enough while the network is still down. */
    fun tick() = synchronized(tracker) {
        tracker.tick(clock())?.let { if (isWindowOpen()) bus.emit(it) }
        Unit
    }

    fun stop() {
        val cb = callback ?: return
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        callback = null
    }
}
