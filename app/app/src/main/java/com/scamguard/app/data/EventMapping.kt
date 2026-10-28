package com.scamguard.app.data

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.CallEventEntity
import com.scamguard.app.data.db.SignalEventEntity
import com.scamguard.app.engine.CallWindow

/** Converts bus events to Room rows. Only types, amounts, hashes and package names survive. */
object EventMapping {

    fun callEvent(event: SignalEvent, window: CallWindow, simulated: Boolean): CallEventEntity? {
        val (type, duration) = when (event) {
            is SignalEvent.CallRinging -> "RINGING" to null
            is SignalEvent.CallActive -> "ACTIVE" to null
            is SignalEvent.CallEnded -> "ENDED" to event.durationMs
            is SignalEvent.SecondCallConnected -> "SECOND_CALL" to null
            else -> return null
        }
        return CallEventEntity(
            windowId = window.id,
            ts = event.ts,
            type = type,
            numberHash = window.numberHash,
            prefix = window.prefix,
            isUnknown = window.isUnknown,
            isInternational = window.isInternational,
            isHidden = window.isHidden,
            durationMs = duration,
            simulated = simulated,
        )
    }

    fun signalEvent(event: SignalEvent, windowId: String?, simulated: Boolean): SignalEventEntity? {
        val base = SignalEventEntity(windowId = windowId, ts = event.ts, type = "", simulated = simulated)
        return when (event) {
            is SignalEvent.OtpReceived -> base.copy(type = "OTP", flag = event.senderTrusted)
            is SignalEvent.DebitReceived -> base.copy(
                type = "DEBIT", amountPaise = event.amountPaise, payeeHash = event.payeeHash, balancePaise = event.balancePaise,
            )
            is SignalEvent.LinkReceived -> base.copy(type = "LINK")
            is SignalEvent.ForwardingChanged -> base.copy(type = "FORWARDING", flag = event.enabled)
            is SignalEvent.RemoteAppOpened -> base.copy(type = "REMOTE_APP", pkg = event.pkg)
            is SignalEvent.ApkInstalled -> base.copy(type = "APK_INSTALLED", pkg = event.pkg, flag = event.sideloaded)
            is SignalEvent.NetworkLost -> base.copy(type = "NETWORK_LOST", detail = event.durationMs.toString())
            is SignalEvent.UserFeedback -> base.copy(type = "FEEDBACK", detail = event.kind.name)
            is SignalEvent.VoiceKeyword -> base.copy(type = "VOICE_KEYWORD", detail = event.category.name)
            else -> null
        }
    }
}
