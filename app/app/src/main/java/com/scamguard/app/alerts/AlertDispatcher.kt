package com.scamguard.app.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scamguard.app.MainActivity
import com.scamguard.app.R
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ui.Labels
import com.scamguard.app.util.LatencyLog
import com.scamguard.app.util.Permissions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Turns decisions into user-visible alerts. Silent decisions are only stored; warnings
 * raise a heads-up notification (and the Home banner); red flags use a full-screen intent
 * when allowed, otherwise a high-priority heads-up. Alerts again only when the level
 * escalates within a window.
 */
class AlertDispatcher(private val context: Context) {
    private val lastLevel = LinkedHashMap<String, RiskLevel>()

    private val _redFlags = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Window ids of new red flags, so a foreground activity can open the alert at once. */
    val redFlags: SharedFlow<String> = _redFlags.asSharedFlow()

    fun onDecision(window: CallWindow, decision: Decision, event: SignalEvent?) {
        if (event is SignalEvent.UserFeedback) {
            if (event.kind == FeedbackKind.THIS_WAS_ME) cancel(window.id)
            return
        }
        if (decision.gated || decision.level == RiskLevel.SAFE) return
        val last = lastLevel[window.id]
        if (last != null && decision.level <= last) return
        lastLevel[window.id] = decision.level
        while (lastLevel.size > MAX_TRACKED) lastLevel.remove(lastLevel.keys.first())

        // Measurement only: times the alert against the spoken phrase that led to it.
        LatencyLog.alertShown(window.id, System.currentTimeMillis())
        when (decision.level) {
            RiskLevel.WARNING -> notifyWarning(window.id, decision)
            RiskLevel.RED_FLAG -> {
                notifyRedFlag(window.id, decision)
                _redFlags.tryEmit(window.id)
            }
            RiskLevel.SAFE -> Unit
        }
    }

    /** Sends a sample red-flag alert so the user can check that alerts reach them. */
    fun sendTestAlert() {
        val intent = FullScreenAlertActivity.testIntent(context)
        val pi = PendingIntent.getActivity(context, TEST_ID, intent, PI_FLAGS)
        val n = NotificationCompat.Builder(context, NotificationChannels.RED_FLAG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.test_alert_title))
            .setContentText(context.getString(R.string.test_alert_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .apply { if (Permissions.canUseFullScreenIntent(context)) setFullScreenIntent(pi, true) }
            .build()
        notify(TEST_ID, n)
        _redFlags.tryEmit(FullScreenAlertActivity.TEST_WINDOW)
    }

    /** Heads-up for a suspicious SMS. Worded as a possibility, never as a verdict. */
    fun notifyMessage(checkId: Long, p: MessagePrediction, simulated: Boolean) {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_MESSAGE_ID, checkId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = (checkId.toInt() and 0x0fff) or 0x2000
        val pi = PendingIntent.getActivity(context, id, open, PI_FLAGS)
        val title = context.getString(if (simulated) R.string.message_alert_title_simulated else R.string.message_alert_title)
        val body = buildString {
            p.topCategory?.let { append(context.getString(Labels.category(it))).append(". ") }
            append(context.getString(R.string.message_alert_body))
        }
        val n = NotificationCompat.Builder(context, NotificationChannels.WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notify(id, n)
    }

    fun cancel(windowId: String) {
        NotificationManagerCompat.from(context).cancel(idFor(windowId))
    }

    fun reset() {
        lastLevel.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun notifyWarning(windowId: String, d: Decision) {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WINDOW_ID, windowId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, idFor(windowId), open, PI_FLAGS)
        val n = NotificationCompat.Builder(context, NotificationChannels.WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.warning_title, context.getString(Labels.scamType(d.scamType))))
            .setContentText(reasonsText(d))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonsText(d)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notify(idFor(windowId), n)
    }

    private fun notifyRedFlag(windowId: String, d: Decision) {
        val pi = PendingIntent.getActivity(
            context, idFor(windowId), FullScreenAlertActivity.intent(context, windowId), PI_FLAGS,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.RED_FLAG)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.alert_red))
            .setContentTitle(context.getString(R.string.red_flag_title, d.score))
            .setContentText(context.getString(Labels.scamType(d.scamType)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonsText(d)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setAutoCancel(true)
        // Without the full-screen grant (Android 14+) this stays a high-priority heads-up.
        if (Permissions.canUseFullScreenIntent(context)) builder.setFullScreenIntent(pi, true)
        notify(idFor(windowId), builder.build())
    }

    private fun reasonsText(d: Decision): String =
        d.reasonCodes.take(3).joinToString("\n") { "• " + context.getString(Labels.reason(it)) }

    private fun notify(id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            Permissions.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        if (!allowed) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were revoked between the check and the call; the in-app banner remains.
        }
    }

    private fun idFor(windowId: String) = windowId.hashCode() and 0x7fffffff or 0x1000

    private companion object {
        const val MAX_TRACKED = 20
        const val TEST_ID = 42
        const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
