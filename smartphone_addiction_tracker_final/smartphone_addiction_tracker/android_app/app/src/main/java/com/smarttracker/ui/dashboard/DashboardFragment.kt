package com.smarttracker.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.smarttracker.R
import com.smarttracker.databinding.FragmentDashboardBinding
import com.smarttracker.service.UsageTrackerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkUsageAccessPermission()
        observeState()
        setupClickListeners()
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private fun checkUsageAccessPermission() {
        if (!hasUsageAccess()) {
            binding.permissionBanner.visibility = View.VISIBLE
            binding.permissionBanner.setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else {
            binding.permissionBanner.visibility = View.GONE
            startUsageService()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = requireContext().getSystemService(android.app.AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun startUsageService() {
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), UsageTrackerService::class.java)
        )
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.tvTotalScreenTime.text = formatDuration(state.totalScreenTimeMs)
                updateChart(state.topApps)
                state.prediction?.let { updatePredictionBadge(it.addiction_level) }
                state.predictionError?.let { binding.tvPredictionError.text = it }
            }
        }
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private fun updateChart(apps: List<AppUsageSummary>) {
        if (apps.isEmpty()) return
        val entries = apps.take(7).mapIndexed { i, app ->
            BarEntry(i.toFloat(), app.usageMs / 60_000f) // minutes
        }
        val dataSet = BarDataSet(entries, "Minutes used").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
        }
        binding.barChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            animateY(500)
            invalidate()
        }
    }

    // ── Prediction Badge ──────────────────────────────────────────────────────

    /**
     * Color-coded prediction badge:
     *   Low    → Green  (#4CAF50)
     *   Medium → Yellow (#FFC107)
     *   High   → Red    (#F44336)
     */
    private fun updatePredictionBadge(level: String) {
        binding.predictionCard.visibility = View.VISIBLE
        binding.tvAddictionLevel.text = level
        val color = when (level) {
            "Low"    -> R.color.addiction_low
            "Medium" -> R.color.addiction_medium
            else     -> R.color.addiction_high
        }
        binding.predictionCard.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), color)
        )
    }

    // ── Clicks ────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnGetPrediction.setOnClickListener {
            viewModel.fetchPrediction()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${hours}h ${minutes}m"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
