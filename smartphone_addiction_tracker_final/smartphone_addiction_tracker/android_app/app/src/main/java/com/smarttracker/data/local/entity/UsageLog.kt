package com.smarttracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UsageLog — persisted locally in Room, synced to Firestore.
 *
 * Firestore path:  users/{uid}/usage_logs/{id}
 *
 * Fields mapped 1-to-1 with Firestore document keys so the
 * repository can push this directly without extra transformation.
 */
@Entity(tableName = "usage_logs")
data class UsageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Package name, e.g. "com.instagram.android" */
    val packageName: String,

    /** Human-readable app name, e.g. "Instagram" */
    val appName: String,

    /** Total foreground time today in milliseconds */
    val usageDurationMs: Long,

    /** Number of times the app was launched today */
    val launchCount: Int,

    /** Epoch millis of the last observed foreground event */
    val lastForegroundMs: Long,

    /** Date string "yyyy-MM-dd" used as a grouping key */
    val date: String,

    /** Whether this record has been pushed to Firestore */
    val synced: Boolean = false
)
