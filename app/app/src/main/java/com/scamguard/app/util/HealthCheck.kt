package com.scamguard.app.util

import android.Manifest
import android.content.Context
import android.os.Build

/** Overall protection state shown on Home. */
enum class ProtectionStatus { ON, LIMITED, OFF }

/** A point-in-time view of every permission, role and special access the app uses. */
data class HealthSnapshot(
    val sms: Boolean,
    val phoneState: Boolean,
    val contacts: Boolean,
    val callPhone: Boolean,
    val answerCalls: Boolean,
    val notifications: Boolean,
    val fullScreenIntent: Boolean,
    val screeningRole: Boolean,
    val usageAccess: Boolean,
    val batteryUnrestricted: Boolean,
    val microphone: Boolean,
) {
    /** The minimum for scoring calls and SMS and warning the user. */
    val coreReady: Boolean
        get() = sms && phoneState && notifications && (screeningRole || !Permissions.screeningRoleSupported())

    fun status(protectionEnabled: Boolean): ProtectionStatus = when {
        !protectionEnabled -> ProtectionStatus.OFF
        coreReady -> ProtectionStatus.ON
        else -> ProtectionStatus.LIMITED
    }
}

/** Builds a [HealthSnapshot]. Cheap; call it whenever the UI resumes. */
object HealthCheck {
    fun snapshot(context: Context): HealthSnapshot {
        fun granted(p: String) = Permissions.isGranted(context, p)
        return HealthSnapshot(
            sms = granted(Manifest.permission.RECEIVE_SMS),
            phoneState = granted(Manifest.permission.READ_PHONE_STATE),
            contacts = granted(Manifest.permission.READ_CONTACTS),
            callPhone = granted(Manifest.permission.CALL_PHONE),
            answerCalls = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && granted(Manifest.permission.ANSWER_PHONE_CALLS),
            notifications = Permissions.notificationsAllowed(context),
            fullScreenIntent = Permissions.canUseFullScreenIntent(context),
            screeningRole = Permissions.hasScreeningRole(context),
            usageAccess = Permissions.hasUsageAccess(context),
            batteryUnrestricted = Permissions.isIgnoringBatteryOptimizations(context),
            microphone = granted(Manifest.permission.RECORD_AUDIO),
        )
    }
}
