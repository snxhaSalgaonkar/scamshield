package com.scamguard.app.alerts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scamguard.app.R
import com.scamguard.app.screencheck.ScreenCheckActivity
import com.scamguard.app.util.Permissions

/**
 * The heads-up offered when a video call from an unknown caller connects: one tap opens
 * Android's screen-sharing consent for the on-demand screen check. Android requires that
 * consent every time, so the check can never start on its own.
 */
object ScreenCheckPrompt {
    private const val ID = 0x3001

    fun show(context: Context) {
        if (!Permissions.notificationsAllowed(context)) return
        val open = Intent(context, ScreenCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = context.getString(R.string.screen_check_prompt_body)
        val n = NotificationCompat.Builder(context, NotificationChannels.WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.screen_check_prompt_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pi)
            .addAction(0, context.getString(R.string.screen_check_prompt_action), pi)
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
}
