package com.smarttracker.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.smarttracker.R
import com.smarttracker.data.repository.UsageRepository
import com.smarttracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * UsageTrackerService
 *
 * Foreground service that collects app usage every 60 seconds via
 * UsageStatsManager and stores it in Room DB (offline-first).
 *
 * Started from DashboardFragment when Usage Access Permission is granted.
 * Stopped when user navigates away or disables tracking.
 *
 * NOTE: This service respects user consent — it never runs in background
 * without the user explicitly enabling it (constraint from project spec).
 */
@AndroidEntryPoint
class UsageTrackerService : Service() {

    @Inject lateinit var repository: UsageRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "usage_tracker_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startTracking()
    }

    private fun startTracking() {
        serviceScope.launch {
            while (isActive) {
                try {
                    repository.refreshUsageData()
                } catch (e: Exception) {
                    // Log error — do not crash the service
                }
                delay(60_000L) // poll every 60 seconds
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartTracker Active")
            .setContentText("Monitoring app usage in background")
            .setSmallIcon(R.drawable.ic_phone_usage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks smartphone usage statistics"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
