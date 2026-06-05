package com.smarttracker.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttracker.data.local.dao.WebVisitDao
import com.smarttracker.data.local.entity.WebVisit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * BrowserViewModel
 *
 * Persists web visits to Room DB.
 * FirebaseSyncWorker will pick up unsynced records and push to:
 *   Firestore: users/{uid}/usage_logs/web_{date}_{hash}
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val webVisitDao: WebVisitDao
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today get() = dateFormat.format(Date())

    /**
     * Called when a web page finishes loading.
     * Stores visit with time-spent for analytics.
     */
    fun logWebVisit(url: String, title: String, timeSpentMs: Long) {
        viewModelScope.launch {
            webVisitDao.insert(
                WebVisit(
                    url = url,
                    title = title,
                    timeSpentMs = timeSpentMs,
                    visitedAt = System.currentTimeMillis(),
                    date = today,
                    synced = false
                )
            )
        }
    }
}
