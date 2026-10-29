package com.scamguard.app.data

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.CallEventEntity
import com.scamguard.app.data.db.SignalEventEntity
import com.scamguard.app.engine.CallWindow

/** Converts bus events to Room rows. Only types, amounts, hashes and package names survive. */
object EventMapping {

    /** "p=0.912;cat=IMPERSONATION_THREAT;url=0.810;ood=0;f=1": model outputs only. */
    fun smsDetail(e: SignalEvent.SmsAnalyzed): String = buildString {
        append("p=").append("%.3f".format(java.util.Locale.ROOT, e.scamProbability))
        e.topCategory?.let { append(";cat=").append(it) }
        e.maxUrlProbability?.let { append(";url=").append("%.3f".format(java.util.Locale.ROOT, it)) }
        append(";ood=").append(if (e.outOfDistribution) 1 else 0)
        append(";f=").append(if (e.flagged) 1 else 0)
    }

    fun parseSmsDetail(detail: String?): Map<String, String> =
        detail.orEmpty().split(";").mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

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
            is SignalEvent.VoiceKeyword -> base.copy(
                type = "VOICE_KEYWORD",
                detail = event.category.name + (event.phrase?.let { "|" + it.take(MAX_PHRASE) } ?: ""),
            )
            is SignalEvent.ScreenEvidence -> base.copy(type = "SCREEN", detail = event.categories.sorted().joinToString(","))
            is SignalEvent.SmsAnalyzed -> base.copy(
                type = "SMS_ML",
                flag = event.linkCount > 0,
                detail = smsDetail(event),
            )
            else -> null
        }
    }

    /** Longest matched phrase kept with a voice event. */
    private const val MAX_PHRASE = 40
}
