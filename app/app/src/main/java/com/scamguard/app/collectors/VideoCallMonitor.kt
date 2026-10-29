package com.scamguard.app.collectors

import android.Manifest
import android.app.Notification
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.scamguard.app.BuildConfig
import com.scamguard.app.alerts.ScreenCheckPrompt
import com.scamguard.app.appContainer
import com.scamguard.app.bus.CallChannel
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.remote.CallerNumbers
import com.scamguard.app.util.Permissions
import com.scamguard.app.util.PhoneNumbers
import kotlinx.coroutines.launch

/**
 * Sees video and voice calls in WhatsApp, Skype, Telegram and similar apps through their call
 * notifications (needs notification access, granted by the user in system settings).
 * Android gives no app access to another app's call audio or video, so this is the
 * metadata layer: who is calling (hashed, or "unknown name"), when the call connects and
 * how long it lasts. Every other notification is ignored and nothing is stored from it.
 *
 * An unknown messaging-app call becomes a call window like a SIM call. When a video call
 * connects, the user is offered the on-demand screen check ([ScreenCheckPrompt]); every
 * unknown call (voice or video) is offered call listening, whose Shizuku recorder can capture
 * the app's call audio.
 */
class VideoCallMonitor : NotificationListenerService() {

    private data class Tracked(val startedAt: Long, var answeredAt: Long?, val unknown: Boolean, val video: Boolean)

    private val calls = HashMap<String, Tracked>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in VideoCallParser.PACKAGES) return
        val n = sbn.notification
        val extras = n.extras
        val parse = { video: Boolean ->
            (if (video) VideoCallParser::parse else VideoCallParser::parseVoice)(
            sbn.packageName,
            n.category == Notification.CATEGORY_CALL,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
                extras.getInt(Notification.EXTRA_CALL_TYPE)
            } else {
                null
            },
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            )
        }
        // A call already tracked as video stays video; otherwise voice-only calls are recognised too.
        val notice = parse(true) ?: parse(false)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "pkg=${sbn.packageName} cat=${n.category} parsed=${notice?.phase} chrono=${extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)}")
        }
        notice ?: return
        val now = System.currentTimeMillis()
        val key = sbn.key
        val tracked = calls[key]
        val bus = applicationContext.appContainer.eventBus

        if (tracked == null) {
            val ringing = ringingEvent(now, notice.caller, if (notice.video) CallChannel.VIDEO_APP else CallChannel.VOICE_APP)
            calls[key] = Tracked(now, null, ringing.isUnknown, notice.video)
            bus.emit(ringing)
        }
        val t = calls.getValue(key)
        if (notice.phase == VideoCallPhase.ONGOING && t.answeredAt == null) {
            t.answeredAt = now
            bus.emit(SignalEvent.CallActive(now))
            // The screen check is for video calls; a voice call is offered call listening by VoiceGuard.
            if (t.unknown && t.video) offerScreenCheck()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val t = calls.remove(sbn.key) ?: return
        val now = System.currentTimeMillis()
        val duration = t.answeredAt?.let { now - it } ?: 0L
        applicationContext.appContainer.eventBus.emit(SignalEvent.CallEnded(now, duration))
        ScreenCheckPrompt.cancel(this)
    }

    override fun onListenerDisconnected() {
        calls.clear()
    }

    /** Hashes a number title at once; a name title is only checked against contacts and dropped. */
    private fun ringingEvent(ts: Long, caller: String?, channel: CallChannel): SignalEvent.CallRinging {
        val parsed = if (VideoCallParser.looksLikeNumber(caller)) PhoneNumbers.parse(caller) else null
        CallerNumbers.remember(parsed)
        val known = when {
            caller == null -> false
            parsed != null -> CallScreener.isInContacts(this, caller)
            else -> isContactName(caller)
        }
        return SignalEvent.CallRinging(
            ts = ts,
            numberHash = parsed?.hash,
            prefix = parsed?.prefix ?: VIDEO_PREFIX,
            isUnknown = !known,
            isInternational = parsed?.isInternational ?: false,
            isHidden = false,
            mask = parsed?.mask ?: caller?.firstOrNull()?.let { "$it•••" }.orEmpty(),
            channel = channel,
            authorityName = parsed == null && AuthorityText.isAuthorityName(caller),
        )
    }

    /** True when a saved contact has exactly this display name; only "found or not" is kept. */
    private fun isContactName(name: String): Boolean {
        if (!Permissions.isGranted(this, Manifest.permission.READ_CONTACTS)) return false
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
        return try {
            contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0) else null }.any { it.equals(name, ignoreCase = true) }
            } ?: false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun offerScreenCheck() {
        val c = applicationContext.appContainer
        c.appScope.launch {
            if (c.settings.current().protectionEnabled) ScreenCheckPrompt.show(applicationContext)
        }
    }

    companion object {
        private const val TAG = "VideoCallMonitor"
        const val VIDEO_PREFIX = "video"
    }
}
