package com.smarttracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttracker.data.remote.PredictResponse
import com.smarttracker.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val totalScreenTimeMs: Long = 0L,
    val topApps: List<AppUsageSummary> = emptyList(),
    val prediction: PredictResponse? = null,
    val predictionError: String? = null
)

data class AppUsageSummary(
    val appName: String,
    val packageName: String,
    val usageMs: Long,
    val launchCount: Int
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeUsage()
    }

    private fun observeUsage() {
        repository.observeTodayUsage()
            .onEach { logs ->
                val totalMs = logs.sumOf { it.usageDurationMs }
                val topApps = logs
                    .sortedByDescending { it.usageDurationMs }
                    .take(10)
                    .map { log ->
                        AppUsageSummary(
                            appName = log.appName,
                            packageName = log.packageName,
                            usageMs = log.usageDurationMs,
                            launchCount = log.launchCount
                        )
                    }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalScreenTimeMs = totalMs,
                        topApps = topApps
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Fetch addiction prediction from FastAPI ML backend.
     *
     * INTEGRATION FLOW:
     *   DashboardFragment → fetchPrediction()
     *   → UsageRepository.getPrediction()
     *   → MlApiService.predict(features)        ← FastAPI /predict
     *   → FirestoreService.savePrediction(...)   ← Firestore predictions/{date}
     *   → UI shows color-coded badge (Green/Yellow/Red)
     */
    fun fetchPrediction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, predictionError = null) }
            repository.getPrediction()
                .onSuccess { prediction ->
                    _uiState.update {
                        it.copy(isLoading = false, prediction = prediction)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, predictionError = error.message)
                    }
                }
        }
    }
}
