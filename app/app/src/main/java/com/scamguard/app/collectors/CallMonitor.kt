package com.scamguard.app.collectors

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.util.Permissions

/**
 * Listens to the phone's call state (TelephonyCallback on Android 12+, PhoneStateListener
 * before) for the default SIM and publishes call events through [MergeDetector].
 *
 * The number never comes from here. With the call-screening role, [CallScreener] opens the
 * window with the number's hash; without it (Android 8 and 9, or role declined) this
 * monitor opens a window for every incoming call with no number, treated as unknown.
 */
class CallMonitor(
    private val context: Context,
    private val bus: EventBus,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val detector = MergeDetector()
    private var callback: Any? = null

    /** Starts listening. Returns false if READ_PHONE_STATE is missing or telephony is absent. */
    fun start(): Boolean {
        if (callback != null) return true
        if (!Permissions.isGranted(context, Manifest.permission.READ_PHONE_STATE)) return false
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        return try {
            callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerModern(tm) else registerLegacy(tm)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        when (val cb = callback) {
            null -> return
            is PhoneStateListener -> @Suppress("DEPRECATION") tm.listen(cb, PhoneStateListener.LISTEN_NONE)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tm.unregisterTelephonyCallback(cb as TelephonyCallback)
        }
        callback = null
    }

    @Synchronized
    private fun onCallState(state: Int) {
        val mapped = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> PhoneCallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> PhoneCallState.OFFHOOK
            else -> PhoneCallState.IDLE
        }
        val now = clock()
        val events = detector.onState(mapped, now)
        if (detector.firstRingStarted && !Permissions.hasScreeningRole(context)) {
            bus.emit(SignalEvent.CallRinging(now, numberHash = null, prefix = "", isUnknown = true, isInternational = false, isHidden = false))
        }
        events.forEach { bus.emit(it) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModern(tm: TelephonyManager): TelephonyCallback {
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onCallState(state)
        }
        tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
        return cb
    }

    @Suppress("DEPRECATION")
    private fun registerLegacy(tm: TelephonyManager): PhoneStateListener {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
        }
        tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        return listener
    }
}
