package com.scamguard.app.service

import android.content.Context
import android.provider.Telephony
import com.scamguard.app.collectors.SmsParser
import com.scamguard.app.data.db.MessageCheckDao
import com.scamguard.app.reputation.TrustedSenders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans SMS already in the inbox when the user asks: each message goes through the same
 * model, OTP and rule checks as a new SMS, and flagged ones are added to the message history
 * with their original time. Quiet (no notifications) and never fed to the live call pipeline.
 * Messages already in the history are skipped, so a rescan only adds new ones.
 */
class InboxScanner(
    private val context: Context,
    private val guard: MessageGuard,
    private val trustedSenders: () -> TrustedSenders,
    private val checks: MessageCheckDao,
) {
    data class Result(val scanned: Int, val flagged: Int)

    /** Needs READ_SMS; throws SecurityException without it. [onProgress] gets (done, total). */
    suspend fun scan(onProgress: (Int, Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - MAX_AGE_MS
        val rows = buildList {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && size < MAX_MESSAGES) {
                    val body = c.getString(1) ?: continue
                    add(Triple(c.getString(0), body, c.getLong(2)))
                }
            }
        }
        var flagged = 0
        val trusted = trustedSenders()
        rows.forEachIndexed { i, (sender, body, ts) ->
            if (checks.countAt(ts, sender) == 0) {
                val id = guard.onSms(
                    body, trusted.isTrusted(sender), ts, quiet = true,
                    isOtp = SmsParser.classify(body).isOtp, sender = sender, fromInbox = true,
                )
                if (id != null) flagged++
            }
            onProgress(i + 1, rows.size)
        }
        Result(rows.size, flagged)
    }

    private companion object {
        const val MAX_MESSAGES = 1_000
        const val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000
    }
}
