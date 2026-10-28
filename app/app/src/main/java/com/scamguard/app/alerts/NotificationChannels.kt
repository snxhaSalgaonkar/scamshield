package com.scamguard.app.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.scamguard.app.R

/** Notification channels: quiet status, heads-up warnings, red-flag alerts. */
object NotificationChannels {
    const val STATUS = "guard_status"
    const val WARNING = "scam_warning"
    const val RED_FLAG = "scam_red_flag"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(WARNING, context.getString(R.string.channel_warning), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(RED_FLAG, context.getString(R.string.channel_red_flag), NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                },
            )
        )
    }
}
