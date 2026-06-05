package com.smarttracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttracker.data.local.dao.UsageLogDao
import com.smarttracker.data.local.dao.WebVisitDao
import com.smarttracker.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyBarEntry(
    val dateLabel: String,   // "Mon", "Tue" …
    val totalHours: Float,
    val socialHours: Float
)

data class TopApp(
    val appName: String,
    val packageName: String,
    val totalMs: Long,
    val percentage: Float   // % of total usage
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val weeklyBars: List<DailyBarEntry> = emptyList(),
    val topApps: List<TopApp> = emptyList(),
    val peakUsageHour: Int = 0,           // 0-23
    val avgDailyHours: Float = 0f,
    val appSwitchingFreq: Float = 0f,     // avg switches per day
    val socialMediaPct: Float = 0f,       // % of total time on social
    val topWebsites: List<Pair<String, Long>> = emptyList()
)

// Social media package prefixes
private val SOCIAL_PACKAGES = setOf(
    "com.instagram.android", "com.facebook.katana", "com.twitter.android",
    "com.zhiliaoapp.musically", "com.snapchat.android", "com.linkedin.android",
    "com.pinterest", "com.reddit.frontpage", "com.whatsapp", "org.telegram.messenger",
    "com.youtube.android", "com.google.android.youtube"
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val usageLogDao: UsageLogDao,
    private val webVisitDao: WebVisitDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadWeeklyData()
    }

    fun loadWeeklyData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val dates = TimeUtils.lastNDates(7)                          // last 7 days
            val from  = dates.first()
            val to    = dates.last()

            val allLogs = usageLogDao.getRange(from, to)

            // ── Weekly bar entries ─────────────────────────────────────────────
            val weeklyBars = dates.map { date ->
                val dayLogs    = allLogs.filter { it.date == date }
                val totalMs    = dayLogs.sumOf { it.usageDurationMs }
                val socialMs   = dayLogs
                    .filter { it.packageName in SOCIAL_PACKAGES }
                    .sumOf { it.usageDurationMs }

                DailyBarEntry(
                    dateLabel  = TimeUtils.toDisplayDate(date).take(3), // "Mon"
                    totalHours = TimeUtils.msToHours(totalMs),
                    socialHours= TimeUtils.msToHours(socialMs)
                )
            }

            // ── Top apps (7-day aggregate) ─────────────────────────────────────
            val totalMsAllTime = allLogs.sumOf { it.usageDurationMs }.toFloat()
            val topApps = allLogs
                .groupBy { it.packageName }
                .map { (pkg, logs) ->
                    val ms  = logs.sumOf { it.usageDurationMs }
                    TopApp(
                        appName    = logs.first().appName,
                        packageName= pkg,
                        totalMs    = ms,
                        percentage = if (totalMsAllTime > 0) ms / totalMsAllTime * 100f else 0f
                    )
                }
                .sortedByDescending { it.totalMs }
                .take(10)

            // ── Aggregate stats ────────────────────────────────────────────────
            val avgDailyHours = weeklyBars.map { it.totalHours }.average().toFloat()
            val socialMs      = allLogs.filter { it.packageName in SOCIAL_PACKAGES }
                .sumOf { it.usageDurationMs }
            val socialPct     = if (totalMsAllTime > 0) socialMs / totalMsAllTime * 100f else 0f
            val avgSwitches   = allLogs
                .groupBy { it.date }
                .map { (_, logs) -> logs.size.toFloat() * 3 }   // approx switches
                .average().toFloat()

            // ── Top websites from in-app browser ──────────────────────────────
            val topSites = webVisitDao.getTopSitesByDate(TimeUtils.today)
                .map { it.url to it.totalMs }

            _uiState.update {
                it.copy(
                    isLoading        = false,
                    weeklyBars       = weeklyBars,
                    topApps          = topApps,
                    avgDailyHours    = avgDailyHours,
                    socialMediaPct   = socialPct,
                    appSwitchingFreq = avgSwitches,
                    topWebsites      = topSites
                )
            }
        }
    }
}
