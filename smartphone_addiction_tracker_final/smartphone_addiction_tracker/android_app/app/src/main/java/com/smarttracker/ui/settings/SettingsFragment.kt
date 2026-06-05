package com.smarttracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.smarttracker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * SettingsFragment
 *
 * ┌────────────────────────────────────────────────────────────────┐
 * │  CLOUD SYNC TOGGLE — INTEGRATION POINT                        │
 * │                                                                │
 * │  Switch ON  → WorkManager schedules FirebaseSyncWorker every  │
 * │               15 min. Preference saved to Firestore settings.  │
 * │                                                                │
 * │  Switch OFF → WorkManager cancels "firebase_sync" work.       │
 * │               Preference saved locally (SharedPrefs).          │
 * └────────────────────────────────────────────────────────────────┘
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
        setupCloudToggle()
        setupAlertSlider()
        setupAuthSection()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.switchCloudSync.isChecked = state.cloudSyncEnabled
                binding.sliderAlertHours.value = state.alertThresholdHours.toFloat()
                binding.tvAlertValue.text = "${state.alertThresholdHours}h"
                binding.tvSignedInAs.text = state.userEmail ?: "Not signed in"
                binding.btnSignOut.isEnabled = state.userEmail != null
            }
        }
    }

    private fun setupCloudToggle() {
        binding.switchCloudSync.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCloudSyncEnabled(isChecked)

            if (isChecked) {
                // WorkManager will auto-start via SmartTrackerApp.scheduleFirebaseSync()
                viewModel.scheduleSync(requireContext())
            } else {
                // Cancel the periodic sync job
                WorkManager.getInstance(requireContext())
                    .cancelUniqueWork("firebase_sync")
            }
        }
    }

    private fun setupAlertSlider() {
        binding.sliderAlertHours.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvAlertValue.text = "${value.toInt()}h"
                viewModel.setAlertThreshold(value.toInt())
            }
        }
    }

    private fun setupAuthSection() {
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val pass  = binding.etPassword.text.toString()
            viewModel.signIn(email, pass)
        }

        binding.btnSignUp.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val pass  = binding.etPassword.text.toString()
            viewModel.signUp(email, pass)
        }

        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
