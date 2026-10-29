package com.scamguard.app.collectors

/** What a messaging app's call notification says about a video call. */
enum class VideoCallPhase { INCOMING, ONGOING }

/**
 * A messaging-app call seen in a notification. [caller] is the raw title, kept in memory only.
 * [video] is false for a voice-only call (see [VideoCallParser.parseVoice]).
 */
data class VideoCallNotice(val phase: VideoCallPhase, val caller: String?, val video: Boolean = true)

/**
 * Reads the call notifications of WhatsApp, Skype, Telegram and similar apps. Pure Kotlin
 * (fields copied out of the Notification first) so the rules are unit tested.
 *
 * Android 12+ CallStyle notifications carry the call type, which is used when present;
 * otherwise the notification text ("Incoming video call", "Ongoing video call") decides.
 * [parse] only returns video calls; voice-only calls come from [parseVoice].
 */
object VideoCallParser {

    val PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.skype.raider",
        "com.skype.m2",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "com.google.android.apps.tachyon",
        "com.facebook.orca",
        "com.imo.android.imoim",
        "us.zoom.videomeetings",
    )

    /** Notification.CallStyle call types (Notification.EXTRA_CALL_TYPE). */
    const val CALL_TYPE_INCOMING = 1
    const val CALL_TYPE_ONGOING = 2

    private val VIDEO = listOf("video call", "video calling", "videocall", "वीडियो कॉल")
    private val ONGOING = listOf("ongoing", "in progress", "on call", "connected", "चालू")
    private val INCOMING = listOf("incoming", "ringing", "आ रही", "इनकमिंग")
    private val VOICE = listOf("voice call", "audio call", "voice calling", "वॉइस कॉल", "ऑडियो कॉल")
    /** A call that is already over is history, not a call in progress. */
    private val MISSED = listOf("missed", "मिस्ड", "declined", "call ended")

    /**
     * Returns the call this notification describes, or null when it is not a video call.
     * [isCallCategory] is true for Notification.CATEGORY_CALL; [usesChronometer] is true
     * when the app shows a running call timer.
     */
    fun parse(
        pkg: String,
        isCallCategory: Boolean,
        callType: Int?,
        title: String?,
        text: String?,
        subText: String?,
        usesChronometer: Boolean,
    ): VideoCallNotice? {
        if (pkg !in PACKAGES) return null
        val body = listOfNotNull(text, subText, title).joinToString(" ").lowercase()
        if (VIDEO.none { it in body }) return null

        val phase = when {
            callType == CALL_TYPE_ONGOING -> VideoCallPhase.ONGOING
            callType == CALL_TYPE_INCOMING -> VideoCallPhase.INCOMING
            usesChronometer || ONGOING.any { it in body } -> VideoCallPhase.ONGOING
            INCOMING.any { it in body } -> VideoCallPhase.INCOMING
            isCallCategory -> VideoCallPhase.INCOMING
            else -> return null
        }
        return VideoCallNotice(phase, title?.trim()?.takeIf { it.isNotEmpty() && VIDEO.none { v -> v in it.lowercase() } })
    }

    /**
     * Returns the voice-only call this notification describes, or null. Asked only after
     * [parse] found no video call. Needs a call notification (CATEGORY_CALL / CallStyle) or the
     * words "voice call"; missed-call notices are ignored.
     */
    fun parseVoice(
        pkg: String,
        isCallCategory: Boolean,
        callType: Int?,
        title: String?,
        text: String?,
        subText: String?,
        usesChronometer: Boolean,
    ): VideoCallNotice? {
        if (pkg !in PACKAGES) return null
        val body = listOfNotNull(text, subText, title).joinToString(" ").lowercase()
        if (VIDEO.any { it in body } || MISSED.any { it in body }) return null
        val mentionsVoice = VOICE.any { it in body }
        if (!mentionsVoice && !isCallCategory && callType == null) return null

        val phase = when {
            callType == CALL_TYPE_ONGOING -> VideoCallPhase.ONGOING
            callType == CALL_TYPE_INCOMING -> VideoCallPhase.INCOMING
            usesChronometer || ONGOING.any { it in body } -> VideoCallPhase.ONGOING
            INCOMING.any { it in body } -> VideoCallPhase.INCOMING
            isCallCategory -> VideoCallPhase.INCOMING
            else -> return null
        }
        val caller = title?.trim()?.takeIf { t -> t.isNotEmpty() && VOICE.none { it in t.lowercase() } }
        return VideoCallNotice(phase, caller, video = false)
    }

    /** True when [label] looks like a phone number rather than a contact or display name. */
    fun looksLikeNumber(label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        val digits = label.count { it.isDigit() }
        val other = label.count { !it.isDigit() && it !in " +-()." && !it.isWhitespace() }
        return digits >= 7 && other == 0
    }
}
