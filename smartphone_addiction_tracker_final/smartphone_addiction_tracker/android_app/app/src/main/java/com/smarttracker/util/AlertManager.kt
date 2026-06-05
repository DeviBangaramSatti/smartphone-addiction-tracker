package com.smarttracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.smarttracker.R
import com.smarttracker.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlertManager
 *
 * Posts usage-exceeded notifications when screen time crosses the
 * threshold set in SettingsFragment (default 4 hours).
 *
 * Called from UsageRepository.refreshUsageData() after each poll.
 *
 * Usage:
 *   alertManager.checkAndNotify(totalUsageHours = 5.2, thresholdHours = 4)
 *   // → Posts "You've used your phone for 5h 12m today" notification
 */
@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val CHANNEL_ID    = "usage_alert_channel"
    private val NOTIFICATION_ID = 2001

    private var lastAlertHour = -1   // avoid repeated alerts for same hour

    init {
        createChannel()
    }

    /**
     * Posts a notification if usage exceeds threshold and we haven't
     * already alerted at this whole-hour mark.
     */
    fun checkAndNotify(totalUsageHours: Float, thresholdHours: Int) {
        if (totalUsageHours < thresholdHours) return

        val currentHour = totalUsageHours.toInt()
        if (currentHour <= lastAlertHour) return   // already notified this hour
        lastAlertHour = currentHour

        val hours   = totalUsageHours.toInt()
        val minutes = ((totalUsageHours - hours) * 60).toInt()

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_usage)
            .setContentTitle("Screen time alert ⚠️")
            .setContentText("You've used your phone for ${hours}h ${minutes}m today.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "You've used your phone for ${hours}h ${minutes}m today — " +
                        "above your ${thresholdHours}h limit. " +
                        "Consider taking a break!"
                    )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // ── Excessive usage alert (called from DashboardViewModel after prediction) ──

    /**
     * Special alert when ML model returns "High" addiction level.
     */
    fun notifyHighAddiction() {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_usage)
            .setContentTitle("High addiction risk detected 🚨")
            .setContentText("Your usage patterns suggest a high addiction level. Tap to see insights.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFF44336.toInt())   // red accent
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when screen time exceeds your set limit"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
