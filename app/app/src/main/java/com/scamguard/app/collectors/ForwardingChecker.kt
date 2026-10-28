package com.scamguard.app.collectors

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.settings.ForwardingStatus
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.util.Permissions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Asks the carrier for the unconditional call-forwarding status (*#21#) on the default
 * voice SIM with TelephonyManager.sendUssdRequest. Carriers answer in free text or not at
 * all, so a check is either a reliable on/off reading or UNAVAILABLE; it never guesses.
 */
class ForwardingChecker(
    private val context: Context,
    private val bus: EventBus,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Runs one check and emits ForwardingChanged(true) on a reliable off -> on change. */
    suspend fun check() {
        val now = clock()
        val result = query()
        val s = settings.current()
        val outcome = ForwardingBaseline.compare(s.forwardingBaseline, result)
        val status = if (result == null) ForwardingStatus.UNAVAILABLE else ForwardingStatus.AVAILABLE
        settings.setForwardingResult(status, outcome.newBaseline, now)
        if (outcome.emitEnabled) bus.emit(SignalEvent.ForwardingChanged(now, enabled = true))
    }

    /** True/false for a clear answer; null on no permission, no SIM, error, timeout or unclear text. */
    private suspend fun query(): Boolean? {
        if (!Permissions.isGranted(context, Manifest.permission.CALL_PHONE)) return null
        if (!Permissions.isGranted(context, Manifest.permission.READ_PHONE_STATE)) return null
        val base = context.getSystemService(TelephonyManager::class.java) ?: return null
        val subId = SubscriptionManager.getDefaultVoiceSubscriptionId()
        val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(t: TelephonyManager, request: String, response: CharSequence) {
                        if (cont.isActive) cont.resume(ForwardingResponseParser.parse(response))
                    }

                    override fun onReceiveUssdResponseFailed(t: TelephonyManager, request: String, failureCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                try {
                    tm.sendUssdRequest(CODE, callback, Handler(Looper.getMainLooper()))
                } catch (_: SecurityException) {
                    cont.resume(null)
                } catch (_: RuntimeException) {
                    cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val CODE = "*#21#"
        const val TIMEOUT_MS = 20_000L
    }
}
