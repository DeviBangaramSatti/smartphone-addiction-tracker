package com.smarttracker.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smarttracker.data.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * FirebaseSyncWorker
 *
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  FIREBASE SYNC INTEGRATION POINT                            ║
 * ║                                                              ║
 * ║  Scheduled in SmartTrackerApp every 15 minutes.             ║
 * ║  Only runs when CONNECTED to network.                        ║
 * ║                                                              ║
 * ║  Flow:                                                       ║
 * ║  1. Room DB → getUnsynced() → UsageLog list                 ║
 * ║  2. FirestoreService.pushUsageLogs(list)                     ║
 * ║     → Firestore: users/{uid}/usage_logs/{date_pkg}           ║
 * ║  3. Room DB → markSynced(ids) → synced = true               ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * ENABLE/DISABLE via SettingsFragment toggle:
 *   if (cloudEnabled) WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(...)
 *   else WorkManager.getInstance(ctx).cancelUniqueWork("firebase_sync")
 */
@HiltWorker
class FirebaseSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.syncToFirestore()
            Result.success()
        } catch (e: Exception) {
            // Retry up to 3 times with exponential backoff (configured in SmartTrackerApp)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
