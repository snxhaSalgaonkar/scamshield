package com.scamguard.app.bus

/**
 * Everything the collectors can observe, as timestamped events. Pure Kotlin so the risk
 * engine can consume it. No event carries message text, OTP values, URLs or raw numbers.
 */
sealed interface SignalEvent {
    val ts: Long

    /** An incoming call started ringing. [mask] is display-only, e.g. "+91 98•••••21". */
    data class CallRinging(
        override val ts: Long,
        val numberHash: String?,
        val prefix: String,
        val isUnknown: Boolean,
        val isInternational: Boolean,
        val isHidden: Boolean,
        val mask: String = "",
    ) : SignalEvent

    data class CallActive(override val ts: Long) : SignalEvent
    data class CallEnded(override val ts: Long, val durationMs: Long) : SignalEvent

    /** A second call was screened or connected while the first was active (merge signal). */
    data class SecondCallConnected(override val ts: Long) : SignalEvent

    data class OtpReceived(override val ts: Long, val senderTrusted: Boolean) : SignalEvent
    data class DebitReceived(
        override val ts: Long,
        val amountPaise: Long,
        val payeeHash: String? = null,
        val balancePaise: Long? = null,
    ) : SignalEvent

    data class LinkReceived(override val ts: Long) : SignalEvent
    data class ForwardingChanged(override val ts: Long, val enabled: Boolean) : SignalEvent
    data class RemoteAppOpened(override val ts: Long, val pkg: String) : SignalEvent
    data class ApkInstalled(override val ts: Long, val pkg: String, val sideloaded: Boolean) : SignalEvent
    data class NetworkLost(override val ts: Long, val durationMs: Long) : SignalEvent
    data class UserFeedback(override val ts: Long, val kind: FeedbackKind) : SignalEvent

    /** Experimental voice layer: only the category of a matched phrase, never a transcript. */
    data class VoiceKeyword(override val ts: Long, val category: VoiceCategory) : SignalEvent

    /**
     * The on-device message model's output for one SMS: probabilities only, never text.
     * Category scores are P(scam) x P(category | scam); [maxUrlProbability] is null when
     * the message had no link or the link model was unavailable.
     */
    data class SmsAnalyzed(
        override val ts: Long,
        val scamProbability: Double,
        val impersonation: Double = 0.0,
        val credential: Double = 0.0,
        val payment: Double = 0.0,
        val remoteAccess: Double = 0.0,
        val topCategory: String? = null,
        val linkCount: Int = 0,
        val maxUrlProbability: Double? = null,
        val outOfDistribution: Boolean = false,
        /** The message model's own verdict was "potential scam" or "suspicious" (validated threshold). */
        val flagged: Boolean = false,
    ) : SignalEvent
}

/** What the user told us about a call window. */
enum class FeedbackKind { THIS_WAS_ME, CONFIRMED_SCAM }

/** Categories of scam phrases the voice layer can recognise. */
enum class VoiceCategory { OTP_REQUEST, ARREST_THREAT, PAYMENT_PRESSURE, REMOTE_APP_REQUEST }
