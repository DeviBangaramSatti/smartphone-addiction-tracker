package com.smarttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smarttracker.data.local.dao.UsageLogDao
import com.smarttracker.data.local.dao.WebVisitDao
import com.smarttracker.data.local.entity.UsageLog
import com.smarttracker.data.local.entity.WebVisit

/**
 * AppDatabase — Room database (offline-first).
 *
 * Tables:
 *   usage_logs  → app usage from UsageStatsManager
 *   web_visits  → web URLs from in-app browser
 *
 * Both tables have a `synced` flag — FirebaseSyncWorker picks up
 * unsynced records and pushes them to Firestore, then marks synced = true.
 */
@Database(
    entities = [UsageLog::class, WebVisit::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageLogDao(): UsageLogDao
    abstract fun webVisitDao(): WebVisitDao
}
