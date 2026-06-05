package com.smarttracker

import android.app.Application
import androidx.work.*
import com.smarttracker.service.FirebaseSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

/**
 * SmartTrackerApp
 *
 * Hilt entry point. On creation we schedule the periodic Firebase sync
 * worker (runs every 15 minutes when cloud upload is enabled).
 *
 * ┌─────────────────────────────────────────────────────┐
 * │  FIREBASE INTEGRATION POINT  →  FirebaseSyncWorker  │
 * │  WorkManager schedules it; the worker itself reads  │
 * │  Room DB and pushes to Firestore.                   │
 * └─────────────────────────────────────────────────────┘
 */
@HiltAndroidApp
class SmartTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleFirebaseSync()
    }

    private fun scheduleFirebaseSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<FirebaseSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "firebase_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
