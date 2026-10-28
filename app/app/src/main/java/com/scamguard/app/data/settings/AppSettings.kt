package com.scamguard.app.data.settings

/** Whether the carrier answered the call-forwarding status check. */
enum class ForwardingStatus { NOT_CHECKED, AVAILABLE, UNAVAILABLE }

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
    val riskOverrides: Map<String, Int> = emptyMap(),
    val forwardingBaseline: Boolean? = null,
    val forwardingStatus: ForwardingStatus = ForwardingStatus.NOT_CHECKED,
    val forwardingCheckedAt: Long = 0,
)
