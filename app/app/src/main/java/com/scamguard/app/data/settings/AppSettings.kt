package com.scamguard.app.data.settings

/** Whether the carrier answered the call-forwarding status check. */
enum class ForwardingStatus { NOT_CHECKED, AVAILABLE, UNAVAILABLE }

/**
 * How the app responds to a red flag. PROTECTED acts by itself (HIGH: 5-second countdown, then
 * end the call, freeze payment apps and lock; SCAM: at once). STANDARD freezes payment apps and
 * shows a big "End call" button but leaves hanging up to the user.
 */
enum class ResponseMode { PROTECTED, STANDARD }

/** User settings and small pieces of app state kept in DataStore. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val protectionEnabled: Boolean = true,
    /** events_only is mandatory and therefore always true. */
    val voiceLayer: Boolean = false,
    val trainingUpload: Boolean = false,
    val caseSharing: Boolean = false,
    /** "system", "en" or "hi". */
    val language: String = "system",
    val demoMode: Boolean = false,
    /** Testing only: calls from saved contacts are treated like unknown callers (team demos between saved numbers). */
    val contactsAsUnknown: Boolean = false,
    val riskOverrides: Map<String, Int> = emptyMap(),
    val forwardingBaseline: Boolean? = null,
    val forwardingStatus: ForwardingStatus = ForwardingStatus.NOT_CHECKED,
    val forwardingCheckedAt: Long = 0,
    // Real-time response. Every action needs its own consent; all are off until the user turns them on.
    val responseMode: ResponseMode = ResponseMode.STANDARD,
    /** End scam calls and lock the phone automatically (Protected mode). */
    val autoProtect: Boolean = false,
    /** Freeze payment and banking apps during a flagged call. */
    val paymentGuard: Boolean = false,
    /** Switch the phone off after a scam call is reported (needs Shizuku). */
    val switchOff: Boolean = false,
    /** Send anonymised scam reports (number, score, tactics; never audio or text) to the authority server. */
    val sendReports: Boolean = false,
    /** Check WhatsApp / Telegram / Signal message notifications that arrive during a call. */
    val messagesDuringCalls: Boolean = false,
    /** Call-listening transcription language: "en-IN", "hi-IN", or "auto" (Hindi + English). */
    val listenLanguage: String = "en-IN",
)
