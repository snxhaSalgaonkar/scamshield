package com.scamguard.app.remote

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.scamguard.app.util.Permissions
import com.scamguard.app.util.PhoneNumbers

/**
 * Fallback for the caller's number when the app does not hold the call-screening role (then the
 * call window has no number). Reads the one call-log entry for the incoming call that started at
 * [startedAt]; needs READ_CALL_LOG, and the entry exists only once the call has ended.
 */
object CallLogNumbers {
    private const val SLACK_MS = 60_000L

    fun find(context: Context, startedAt: Long): String? {
        if (!Permissions.isGranted(context, Manifest.permission.READ_CALL_LOG)) return null
        val types = listOf(CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE)
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
                arrayOf((startedAt - SLACK_MS).toString(), (startedAt + SLACK_MS).toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var best: Pair<String, Long>? = null
                while (c.moveToNext()) {
                    if (c.getInt(2) !in types) continue
                    val number = PhoneNumbers.parse(c.getString(0))?.normalized ?: continue
                    val gap = kotlin.math.abs(c.getLong(1) - startedAt)
                    if (best == null || gap < best.second) best = number to gap
                }
                best?.first
            }
        } catch (_: RuntimeException) {
            null
        }
    }
}
