package com.scamguard.app.engine

private const val MINUTE = 60_000L

/**
 * Every weight and threshold the engine uses. Point weights can be overridden from
 * DataStore through [withOverrides]; the keys are listed in [WEIGHT_KEYS].
 */
data class RiskConfig(
    val unknownCaller: Int = 10,
    val otpDuringWindow: Int = 25,
    val debitDuringWindow: Int = 30,
    val forwardingOn: Int = 35,
    val callMerge: Int = 25,
    val txnFarAboveNormal: Int = 10,
    val txnFirstTimePayee: Int = 5,
    val txnMultipleDebits: Int = 5,
    val txnBalanceNearZero: Int = 5,
    val txnCap: Int = 20,
    val internationalOrVoip: Int = 10,
    val repeatedCalls: Int = 10,
    val reportedNumber: Int = 15,
    val longCall: Int = 5,
    val oddHours: Int = 5,
    val remoteAppOpened: Int = 20,
    val sideloadedApk: Int = 10,
    val linkThenInstall: Int = 15,
    val networkLoss: Int = 5,
    val knownBankNumber: Int = -30,
    val userMarkedSafe: Int = -30,
    val voiceOtpRequest: Int = 15,
    val voiceArrestThreat: Int = 25,
    val voicePaymentPressure: Int = 15,
    val voiceRemoteApp: Int = 15,
    val voiceCap: Int = 40,
    val voiceAuthority: Int = 15,
    val voiceAccusation: Int = 15,
    val voiceIsolation: Int = 20,
    val voiceCardDetails: Int = 20,
    val voiceLink: Int = 10,
    val voiceTotalCap: Int = 60,
    val videoUnknown: Int = 15,
    val videoLong: Int = 20,
    val videoVeryLong: Int = 15,
    val authorityName: Int = 25,
    val screenLawEnforcement: Int = 30,
    val screenLegalThreat: Int = 25,
    val screenPaymentDemand: Int = 15,
    val screenCap: Int = 50,
    val comboFloor: Int = 75,
    /** Floor for a complete digital-arrest script (authority, threat or isolation, then money). */
    val scamFloor: Int = 90,
    val warningThreshold: Int = 40,
    val redFlagThreshold: Int = 75,

    val windowAfterEndMs: Long = 10 * MINUTE,
    val comboOtpDebitMs: Long = 5 * MINUTE,
    val multipleDebitsMs: Long = 10 * MINUTE,
    val longCallMs: Long = 5 * MINUTE,
    val videoLongMs: Long = 20 * MINUTE,
    val videoVeryLongMs: Long = 60 * MINUTE,
    val repeatedCallsCount: Int = 3,
    val farAboveMultiplier: Int = 5,
    val farAboveMinPaise: Long = 5_000_00,
    val nearZeroBalancePaise: Long = 500_00,
    val oddHourStart: Int = 23,
    val oddHourEnd: Int = 5,
) {
    /** Current point weights by key, for the developer editor. */
    fun weights(): Map<String, Int> = WEIGHT_KEYS.associateWith { weight(it) }

    private fun weight(key: String): Int = when (key) {
        "unknownCaller" -> unknownCaller
        "otpDuringWindow" -> otpDuringWindow
        "debitDuringWindow" -> debitDuringWindow
        "forwardingOn" -> forwardingOn
        "callMerge" -> callMerge
        "txnFarAboveNormal" -> txnFarAboveNormal
        "txnFirstTimePayee" -> txnFirstTimePayee
        "txnMultipleDebits" -> txnMultipleDebits
        "txnBalanceNearZero" -> txnBalanceNearZero
        "txnCap" -> txnCap
        "internationalOrVoip" -> internationalOrVoip
        "repeatedCalls" -> repeatedCalls
        "reportedNumber" -> reportedNumber
        "longCall" -> longCall
        "oddHours" -> oddHours
        "remoteAppOpened" -> remoteAppOpened
        "sideloadedApk" -> sideloadedApk
        "linkThenInstall" -> linkThenInstall
        "networkLoss" -> networkLoss
        "knownBankNumber" -> knownBankNumber
        "userMarkedSafe" -> userMarkedSafe
        "voiceOtpRequest" -> voiceOtpRequest
        "voiceArrestThreat" -> voiceArrestThreat
        "voicePaymentPressure" -> voicePaymentPressure
        "voiceRemoteApp" -> voiceRemoteApp
        "voiceCap" -> voiceCap
        "voiceAuthority" -> voiceAuthority
        "voiceAccusation" -> voiceAccusation
        "voiceIsolation" -> voiceIsolation
        "voiceCardDetails" -> voiceCardDetails
        "voiceLink" -> voiceLink
        "voiceTotalCap" -> voiceTotalCap
        "videoUnknown" -> videoUnknown
        "videoLong" -> videoLong
        "videoVeryLong" -> videoVeryLong
        "authorityName" -> authorityName
        "screenLawEnforcement" -> screenLawEnforcement
        "screenLegalThreat" -> screenLegalThreat
        "screenPaymentDemand" -> screenPaymentDemand
        "screenCap" -> screenCap
        "comboFloor" -> comboFloor
        "scamFloor" -> scamFloor
        "warningThreshold" -> warningThreshold
        "redFlagThreshold" -> redFlagThreshold
        else -> error("Unknown weight $key")
    }

    /** Returns a copy with known keys replaced; unknown keys are ignored. */
    fun withOverrides(overrides: Map<String, Int>): RiskConfig =
        overrides.entries.fold(this) { c, (key, v) ->
            when (key) {
                "unknownCaller" -> c.copy(unknownCaller = v)
                "otpDuringWindow" -> c.copy(otpDuringWindow = v)
                "debitDuringWindow" -> c.copy(debitDuringWindow = v)
                "forwardingOn" -> c.copy(forwardingOn = v)
                "callMerge" -> c.copy(callMerge = v)
                "txnFarAboveNormal" -> c.copy(txnFarAboveNormal = v)
                "txnFirstTimePayee" -> c.copy(txnFirstTimePayee = v)
                "txnMultipleDebits" -> c.copy(txnMultipleDebits = v)
                "txnBalanceNearZero" -> c.copy(txnBalanceNearZero = v)
                "txnCap" -> c.copy(txnCap = v)
                "internationalOrVoip" -> c.copy(internationalOrVoip = v)
                "repeatedCalls" -> c.copy(repeatedCalls = v)
                "reportedNumber" -> c.copy(reportedNumber = v)
                "longCall" -> c.copy(longCall = v)
                "oddHours" -> c.copy(oddHours = v)
                "remoteAppOpened" -> c.copy(remoteAppOpened = v)
                "sideloadedApk" -> c.copy(sideloadedApk = v)
                "linkThenInstall" -> c.copy(linkThenInstall = v)
                "networkLoss" -> c.copy(networkLoss = v)
                "knownBankNumber" -> c.copy(knownBankNumber = v)
                "userMarkedSafe" -> c.copy(userMarkedSafe = v)
                "voiceOtpRequest" -> c.copy(voiceOtpRequest = v)
                "voiceArrestThreat" -> c.copy(voiceArrestThreat = v)
                "voicePaymentPressure" -> c.copy(voicePaymentPressure = v)
                "voiceRemoteApp" -> c.copy(voiceRemoteApp = v)
                "voiceCap" -> c.copy(voiceCap = v)
                "voiceAuthority" -> c.copy(voiceAuthority = v)
                "voiceAccusation" -> c.copy(voiceAccusation = v)
                "voiceIsolation" -> c.copy(voiceIsolation = v)
                "voiceCardDetails" -> c.copy(voiceCardDetails = v)
                "voiceLink" -> c.copy(voiceLink = v)
                "voiceTotalCap" -> c.copy(voiceTotalCap = v)
                "videoUnknown" -> c.copy(videoUnknown = v)
                "videoLong" -> c.copy(videoLong = v)
                "videoVeryLong" -> c.copy(videoVeryLong = v)
                "authorityName" -> c.copy(authorityName = v)
                "screenLawEnforcement" -> c.copy(screenLawEnforcement = v)
                "screenLegalThreat" -> c.copy(screenLegalThreat = v)
                "screenPaymentDemand" -> c.copy(screenPaymentDemand = v)
                "screenCap" -> c.copy(screenCap = v)
                "comboFloor" -> c.copy(comboFloor = v)
                "scamFloor" -> c.copy(scamFloor = v)
                "warningThreshold" -> c.copy(warningThreshold = v)
                "redFlagThreshold" -> c.copy(redFlagThreshold = v)
                else -> c
            }
        }

    companion object {
        val WEIGHT_KEYS = listOf(
            "unknownCaller", "otpDuringWindow", "debitDuringWindow", "forwardingOn", "callMerge",
            "txnFarAboveNormal", "txnFirstTimePayee", "txnMultipleDebits", "txnBalanceNearZero", "txnCap",
            "internationalOrVoip", "repeatedCalls", "reportedNumber", "longCall", "oddHours",
            "remoteAppOpened", "sideloadedApk", "linkThenInstall", "networkLoss",
            "knownBankNumber", "userMarkedSafe",
            "voiceOtpRequest", "voiceArrestThreat", "voicePaymentPressure", "voiceRemoteApp", "voiceCap",
            "voiceAuthority", "voiceAccusation", "voiceIsolation", "voiceCardDetails", "voiceLink", "voiceTotalCap",
            "videoUnknown", "videoLong", "videoVeryLong", "authorityName",
            "screenLawEnforcement", "screenLegalThreat", "screenPaymentDemand", "screenCap",
            "comboFloor", "scamFloor", "warningThreshold", "redFlagThreshold",
        )
    }
}
