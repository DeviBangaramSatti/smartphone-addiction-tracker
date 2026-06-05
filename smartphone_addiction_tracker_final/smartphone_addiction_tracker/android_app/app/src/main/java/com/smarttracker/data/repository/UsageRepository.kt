package com.smarttracker.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.smarttracker.data.local.dao.UsageLogDao
import com.smarttracker.data.local.dao.WebVisitDao
import com.smarttracker.data.local.entity.UsageLog
import com.smarttracker.data.remote.FirestoreService
import com.smarttracker.data.remote.MlApiService
import com.smarttracker.data.remote.PredictRequest
import com.smarttracker.data.remote.PredictResponse
import com.smarttracker.util.AlertManager
import com.smarttracker.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UsageRepository — single source of truth.
 *
 * Coordinates:
 *   UsageStatsManager  → system API (foreground stats + events)
 *   Room DB            → offline-first local cache
 *   FirestoreService   → cloud sync (when enabled)
 *   MlApiService       → addiction prediction via FastAPI
 *   AlertManager       → usage-exceeded notifications
 */
@Singleton
class UsageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: UsageLogDao,
    private val webVisitDao: WebVisitDao,
    private val firestoreService: FirestoreService,
    private val mlApiService: MlApiService,
    private val alertManager: AlertManager
) {
    companion object {
        private const val PREFS_NAME    = "smarttracker_prefs"
        private const val KEY_THRESHOLD = "alert_threshold_hours"
        private const val KEY_CLOUD_ON  = "cloud_sync_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today get() = dateFormat.format(Date())

    private val socialPackages = setOf(
        "com.instagram.android", "com.facebook.katana", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.linkedin.android",
        "com.pinterest", "com.reddit.frontpage", "com.whatsapp", "org.telegram.messenger",
        "com.youtube.android", "com.google.android.youtube"
    )

    // ── Settings (persisted in SharedPrefs) ───────────────────────────────────

    var alertThresholdHours: Int
        get()  = prefs.getInt(KEY_THRESHOLD, 4)
        set(v) = prefs.edit().putInt(KEY_THRESHOLD, v).apply()

    var cloudSyncEnabled: Boolean
        get()  = prefs.getBoolean(KEY_CLOUD_ON, false)
        set(v) = prefs.edit().putBoolean(KEY_CLOUD_ON, v).apply()

    // ── Live Data ─────────────────────────────────────────────────────────────

    fun observeTodayUsage(): Flow<List<UsageLog>> = dao.observeByDate(today)

    // ── Data Collection ───────────────────────────────────────────────────────

    /**
     * Called every 60 s by UsageTrackerService.
     * Reads UsageStatsManager → Room DB → checks alert threshold.
     */
    suspend fun refreshUsageData() {
        val stats = collectUsageStats() ?: return
        dao.insertAll(stats)

        val totalMs = stats.sumOf { it.usageDurationMs }
        alertManager.checkAndNotify(TimeUtils.msToHours(totalMs), alertThresholdHours)
    }

    private fun collectUsageStats(): List<UsageLog>? {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null
        val pm = context.packageManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }

        return usageManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            ?.filter { it.totalTimeInForeground > 0 }
            ?.map { stat ->
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    stat.packageName
                }
                UsageLog(
                    packageName     = stat.packageName,
                    appName         = appName,
                    usageDurationMs = stat.totalTimeInForeground,
                    launchCount     = stat.appLaunchCount,
                    lastForegroundMs= stat.lastTimeUsed,
                    date            = today,
                    synced          = false
                )
            }
    }

    /**
     * Calculates night-time usage (22:00–06:00) via UsageEvents for ML accuracy.
     */
    fun calculateNightUsageHours(): Float {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return 0f

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }

        val events  = usageManager.queryEvents(cal.timeInMillis, System.currentTimeMillis())
        val event   = UsageEvents.Event()
        var nightMs = 0L
        var lastStart = 0L
        var lastPkg   = ""

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastStart = event.timeStamp; lastPkg = event.packageName
                }
                UsageEvents.Event.ACTIVITY_PAUSED  -> {
                    if (lastPkg == event.packageName && lastStart > 0) {
                        nightMs += countNightMs(lastStart, event.timeStamp)
                        lastStart = 0L
                    }
                }
            }
        }
        return TimeUtils.msToHours(nightMs)
    }

    private fun countNightMs(start: Long, end: Long): Long {
        var counted = 0L
        var cursor  = start
        val step    = 60_000L
        while (cursor < end) {
            val next = minOf(cursor + step, end)
            if (TimeUtils.isNightTime(cursor)) counted += next - cursor
            cursor = next
        }
        return counted
    }

    // ── ML Prediction ─────────────────────────────────────────────────────────

    /**
     * Builds feature vector → calls FastAPI /predict → saves to Firestore.
     * Called from DashboardViewModel.fetchPrediction().
     */
    suspend fun getPrediction(): Result<PredictResponse> {
        return try {
            val logs          = dao.getRange(today, today)
            val totalMs       = logs.sumOf { it.usageDurationMs }
            val totalHours    = TimeUtils.msToHours(totalMs)
            val socialMs      = logs.filter { it.packageName in socialPackages }.sumOf { it.usageDurationMs }
            val socialPct     = if (totalMs > 0) socialMs.toFloat() / totalMs * 100f else 0f
            val totalSessions = logs.sumOf { it.launchCount }
            val avgSessionMin = if (totalSessions > 0) (totalMs / 1000f / 60f) / totalSessions else 0f
            val nightHours    = calculateNightUsageHours()

            val response = mlApiService.predict(
                PredictRequest(
                    daily_usage_hours        = totalHours,
                    night_usage_hours        = nightHours,
                    app_switching_frequency  = logs.size * 3,
                    social_media_percentage  = socialPct,
                    total_sessions           = totalSessions,
                    avg_session_duration_mins= avgSessionMin
                )
            )

            // Save to Firestore if cloud enabled
            if (cloudSyncEnabled) {
                runCatching { firestoreService.savePrediction(today, response.addiction_level, response.confidence) }
            }

            // Alert if High risk
            if (response.addiction_level == "High") alertManager.notifyHighAddiction()

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Firebase Sync ─────────────────────────────────────────────────────────

    /**
     * Called by FirebaseSyncWorker (WorkManager, every 15 min when online).
     * Pushes unsynced app-usage logs AND web visits to Firestore.
     *
     * INTEGRATION: FirebaseSyncWorker → repository.syncToFirestore()
     *              → FirestoreService.pushUsageLogs() → users/{uid}/usage_logs/
     *              → FirestoreService.pushWebVisits() → users/{uid}/usage_logs/web_*
     */
    suspend fun syncToFirestore() {
        if (!cloudSyncEnabled) return

        val unsyncedLogs = dao.getUnsynced()
        if (unsyncedLogs.isNotEmpty()) {
            firestoreService.pushUsageLogs(unsyncedLogs)
            dao.markSynced(unsyncedLogs.map { it.id })
        }

        val unsyncedVisits = webVisitDao.getUnsynced()
        if (unsyncedVisits.isNotEmpty()) {
            firestoreService.pushWebVisits(unsyncedVisits)
            webVisitDao.markSynced(unsyncedVisits.map { it.id })
        }
    }
}
