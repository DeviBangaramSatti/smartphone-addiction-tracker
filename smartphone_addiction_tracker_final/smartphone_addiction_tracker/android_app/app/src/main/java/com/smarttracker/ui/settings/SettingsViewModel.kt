package com.smarttracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.smarttracker.data.remote.FirestoreService
import com.smarttracker.service.FirebaseSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val cloudSyncEnabled: Boolean = false,
    val alertThresholdHours: Int = 4,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val firestoreService: FirestoreService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        val user = firestoreService.getCurrentUser()
        _uiState.update { it.copy(userEmail = user?.email) }
    }

    // ── Cloud Sync ────────────────────────────────────────────────────────────

    fun setCloudSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(cloudSyncEnabled = enabled) }
        viewModelScope.launch {
            try {
                firestoreService.saveSettings(
                    cloudEnabled = enabled,
                    alertThresholdHours = _uiState.value.alertThresholdHours
                )
            } catch (e: Exception) {
                // Silently fail — setting is saved locally via StateFlow
            }
        }
    }

    /**
     * Schedules the WorkManager periodic sync.
     * Called from SettingsFragment when toggle is turned ON.
     *
     * INTEGRATION POINT → FirebaseSyncWorker
     */
    fun scheduleSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FirebaseSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "firebase_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    // ── Alert Threshold ───────────────────────────────────────────────────────

    fun setAlertThreshold(hours: Int) {
        _uiState.update { it.copy(alertThresholdHours = hours) }
        viewModelScope.launch {
            firestoreService.saveSettings(
                cloudEnabled = _uiState.value.cloudSyncEnabled,
                alertThresholdHours = hours
            )
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                firestoreService.signInWithEmail(email, password)
                val user = firestoreService.getCurrentUser()
                _uiState.update { it.copy(isLoading = false, userEmail = user?.email) }

                // Restore saved settings from Firestore after sign-in
                val savedSettings = firestoreService.fetchSettings()
                savedSettings?.let { settings ->
                    _uiState.update {
                        it.copy(
                            cloudSyncEnabled = settings["cloudSyncEnabled"] as? Boolean ?: false,
                            alertThresholdHours = (settings["alertThresholdHours"] as? Long)?.toInt() ?: 4
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                firestoreService.signUp(email, password)
                val user = firestoreService.getCurrentUser()
                _uiState.update { it.copy(isLoading = false, userEmail = user?.email) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signOut() {
        firestoreService.signOut()
        _uiState.update { it.copy(userEmail = null, cloudSyncEnabled = false) }
    }
}
