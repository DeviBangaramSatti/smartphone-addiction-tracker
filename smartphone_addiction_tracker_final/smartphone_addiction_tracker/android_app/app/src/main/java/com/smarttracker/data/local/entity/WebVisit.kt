package com.smarttracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * WebVisit — stores each web page visit from the in-app browser.
 *
 * Firestore path: users/{uid}/usage_logs/web_{date}_{urlHash}
 *
 * Only tracked inside the in-app WebView — per project privacy constraint.
 */
@Entity(tableName = "web_visits")
data class WebVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Full URL of the page visited */
    val url: String,

    /** Page title from WebView */
    val title: String,

    /** Time spent on this page in milliseconds */
    val timeSpentMs: Long,

    /** Epoch millis when the page was visited */
    val visitedAt: Long,

    /** Date string "yyyy-MM-dd" for grouping */
    val date: String,

    /** Whether this record has been pushed to Firestore */
    val synced: Boolean = false
)
