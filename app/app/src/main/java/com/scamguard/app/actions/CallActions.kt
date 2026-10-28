package com.scamguard.app.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import com.scamguard.app.util.Permissions

/** Call controls offered to the user. Nothing here runs without a user tap (except Demo Mode). */
object CallActions {
    const val HELPLINE = "1930"

    /** TelecomManager.endCall exists from Android 9. */
    fun endCallSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun canEndCall(context: Context): Boolean =
        endCallSupported() && Permissions.isGranted(context, Manifest.permission.ANSWER_PHONE_CALLS)

    /** Ends the current call. Returns false if not possible on this phone. */
    fun endCall(context: Context): Boolean {
        if (!canEndCall(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            @Suppress("DEPRECATION")
            telecom.endCall()
        } catch (_: SecurityException) {
            false
        }
    }

    /** Opens the dialer with the national cybercrime helpline filled in. */
    fun dialHelplineIntent(): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$HELPLINE"))
}
