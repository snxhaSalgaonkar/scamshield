package com.scamguard.app.actions

import android.content.Context
import com.scamguard.app.voice.CallListenService
import com.scamguard.app.voice.ShizukuAudio

/**
 * "Freeze phone" on the red-flag screen: the reversible stand-in for switching the phone off.
 * Ends the call if one is still going, then locks the screen at once, so the caller cannot keep
 * talking the user through a payment. The user presses the power button and unlocks as usual
 * to carry on; nothing is changed or switched off.
 *
 * Runs only after the user's tap. Uses Shizuku when it is running (no extra setup), otherwise
 * the screen-lock device admin the user approved.
 */
object PhoneFreeze {
    enum class Result { LOCKED, NEEDS_SETUP }

    fun available(context: Context): Boolean =
        GuardDeviceAdmin.isActive(context) || ShizukuAudio.status() == ShizukuAudio.Status.READY

    /** [endCall] is false for the sample test alert, so a test never hangs up a real call. */
    suspend fun freeze(context: Context, endCall: Boolean): Result {
        if (endCall && CallListenService.isInCall(context)) {
            if (!CallActions.endCall(context)) ShizukuAudio.endCall(context)
        }
        if (ShizukuAudio.status() == ShizukuAudio.Status.READY && ShizukuAudio.sleep(context) == null) return Result.LOCKED
        if (GuardDeviceAdmin.lockNow(context)) return Result.LOCKED
        return Result.NEEDS_SETUP
    }
}
