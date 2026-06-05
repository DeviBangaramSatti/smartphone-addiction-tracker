package com.smarttracker.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.smarttracker.R
import com.smarttracker.databinding.FragmentAnalyticsBinding
import com.smarttracker.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        observeState()

        binding.btnRefresh.setOnClickListener { viewModel.loadWeeklyData() }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                if (!state.isLoading) {
                    renderWeeklyChart(state.weeklyBars)
                    renderPieChart(state.topApps)
                    renderStatCards(state)
                    renderTopSites(state.topWebsites)
                }
            }
        }
    }

    // ── Weekly Grouped Bar Chart (total vs social) ────────────────────────────

    private fun renderWeeklyChart(bars: List<DailyBarEntry>) {
        val totalEntries  = bars.mapIndexed { i, b -> BarEntry(i.toFloat(), b.totalHours) }
        val socialEntries = bars.mapIndexed { i, b -> BarEntry(i.toFloat(), b.socialHours) }

        val totalDataSet = BarDataSet(totalEntries, "Total").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
            valueTextColor = Color.WHITE
        }
        val socialDataSet = BarDataSet(socialEntries, "Social Media").apply {
            color = ContextCompat.getColor(requireContext(), R.color.addiction_medium)
            valueTextColor = Color.WHITE
        }

        val groupCount  = bars.size
        val groupSpace  = 0.2f
        val barSpace    = 0.05f
        val barWidth    = 0.35f

        val data = BarData(totalDataSet, socialDataSet).apply {
            this.barWidth = barWidth
        }

        binding.weeklyChart.apply {
            this.data = data
            groupBars(0f, groupSpace, barSpace)
            xAxis.apply {
                granularity = 1f
                position    = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(bars.map { it.dateLabel })
                setCenterAxisLabels(true)
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            animateY(600)
            invalidate()
        }
    }

    // ── Pie Chart — top apps share ────────────────────────────────────────────

    private fun renderPieChart(apps: List<TopApp>) {
        val entries = apps.take(6).map { app ->
            PieEntry(app.percentage, app.appName.take(12))
        }

        val colors = listOf(
            0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFF4CAF50,
            0xFFFFC107, 0xFFF44336
        ).map { it.toInt() }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize  = 11f
            valueTextColor = Color.WHITE
            sliceSpace = 3f
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            holeRadius       = 40f
            transparentCircleRadius = 45f
            description.isEnabled = false
            legend.isEnabled = true
            setEntryLabelColor(Color.WHITE)
            animateY(700)
            invalidate()
        }
    }

    // ── Stat Cards ────────────────────────────────────────────────────────────

    private fun renderStatCards(state: AnalyticsUiState) {
        binding.tvAvgDaily.text   = "%.1fh".format(state.avgDailyHours)
        binding.tvSocialPct.text  = "%.0f%%".format(state.socialMediaPct)
        binding.tvSwitchFreq.text = "~%.0f/day".format(state.appSwitchingFreq)
    }

    // ── Top Websites list ─────────────────────────────────────────────────────

    private fun renderTopSites(sites: List<Pair<String, Long>>) {
        if (sites.isEmpty()) {
            binding.tvNoSites.visibility = View.VISIBLE
            return
        }
        binding.tvNoSites.visibility = View.GONE

        val text = sites.take(5).joinToString("\n") { (url, ms) ->
            val domain = url.removePrefix("https://").removePrefix("http://")
                .split("/").first().take(30)
            "• $domain  —  ${TimeUtils.formatDuration(ms)}"
        }
        binding.tvTopSites.text = text
    }

    // ── Chart initial config ──────────────────────────────────────────────────

    private fun setupCharts() {
        // Weekly chart baseline styling
        binding.weeklyChart.apply {
            setNoDataText("Loading weekly data…")
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
            }
        }
        // Pie chart baseline
        binding.pieChart.apply {
            setNoDataText("Loading app breakdown…")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
