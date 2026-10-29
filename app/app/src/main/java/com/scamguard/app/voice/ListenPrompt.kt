package com.scamguard.app.voice

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scamguard.app.R
import com.scamguard.app.alerts.NotificationChannels
import com.scamguard.app.util.Permissions

/**
 * The heads-up asking whether to check this call by voice. Listening starts only from the
 * Allow tap ([ListenActivity]); there is no timer and nothing starts on its own.
 */
object ListenPrompt {
    private const val ID = 0x4001

    fun show(context: Context) {
        if (!Permissions.notificationsAllowed(context)) return
        val allow = PendingIntent.getActivity(context, ID, ListenActivity.intent(context, ListenActivity.Mode.CALL), FLAGS)
        val decline = PendingIntent.getActivity(context, ID + 1, ListenActivity.intent(context, ListenActivity.Mode.DECLINE), FLAGS)
        // With Shizuku the call itself is recorded, so there is no need for the speaker.
        val body = context.getString(if (ShizukuAudio.usable()) R.string.listen_prompt_body_shizuku else R.string.listen_prompt_body)
        val n = NotificationCompat.Builder(context, NotificationChannels.WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.listen_prompt_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(allow)
            .addAction(0, context.getString(R.string.listen_prompt_allow), allow)
            .addAction(0, context.getString(R.string.listen_prompt_decline), decline)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
