package com.example.marketmind.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.marketmind.R
import com.example.marketmind.databinding.FragmentAnalyticsBinding
import com.example.marketmind.domain.model.AnalyticsData
import com.example.marketmind.ui.adapters.RiskMetricAdapter
import com.example.marketmind.ui.common.HapticFeedbackUtil
import com.example.marketmind.ui.common.UiState
import kotlinx.coroutines.launch
import java.util.Locale

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnalyticsViewModel by viewModels()
    private lateinit var riskMetricAdapter: RiskMetricAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        riskMetricAdapter = RiskMetricAdapter()
        binding.rvRiskMetrics.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = riskMetricAdapter
        }
    }

    private fun setupListeners() {
        binding.btnRefresh.setOnClickListener {
            HapticFeedbackUtil.performRefreshFeedback(it)
            viewModel.refreshData()
        }

        binding.swipeRefresh.setOnRefreshListener {
            HapticFeedbackUtil.performRefreshFeedback(binding.swipeRefresh)
            viewModel.refreshData()
        }

        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val period = when (checkedIds.first()) {
                    R.id.chip_1m -> "1M"
                    R.id.chip_3m -> "3M"
                    R.id.chip_6m -> "6M"
                    R.id.chip_1y -> "1Y"
                    R.id.chip_all -> "ALL"
                    else -> "1M"
                }
                viewModel.selectPeriod(period)
            }
        }

        binding.chipGroupBenchmark.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                HapticFeedbackUtil.performClickFeedback(binding.chipGroupBenchmark)
                viewModel.refreshData()
            }
        }

        binding.errorView.btnErrorRetry.setOnClickListener {
            viewModel.refreshData()
        }

        binding.emptyView.btnEmptyAction.setOnClickListener {
            viewModel.refreshData()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.analyticsState.collect { state ->
                        handleAnalyticsState(state)
                    }
                }
            }
        }
    }

    private fun handleAnalyticsState(state: UiState<AnalyticsData>) {
        binding.swipeRefresh.isRefreshing = false
        when (state) {
            is UiState.Loading -> {
                binding.errorView.containerError.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.errorView.containerError.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE

                renderAnalytics(state.data)
            }
            is UiState.Empty -> {
                binding.scrollContent.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.VISIBLE
                binding.emptyView.tvEmptyTitle.text = getString(R.string.error_empty_title)
                binding.emptyView.tvEmptyMessage.text = getString(R.string.analytics_empty)
            }
            is UiState.Error -> {
                val cached = state.cachedData as? AnalyticsData
                if (cached != null && (cached.performancePoints.isNotEmpty() || cached.riskMetrics.isNotEmpty())) {
                    binding.errorView.containerError.visibility = View.GONE
                    binding.emptyView.containerEmpty.visibility = View.GONE
                    binding.scrollContent.visibility = View.VISIBLE
                    renderAnalytics(cached)
                } else {
                    binding.scrollContent.visibility = View.GONE
                    binding.emptyView.containerEmpty.visibility = View.VISIBLE
                    binding.emptyView.tvEmptyTitle.text = getString(R.string.error_empty_title)
                    binding.emptyView.tvEmptyMessage.text = getString(R.string.analytics_empty)
                }
            }
        }
    }

    private fun renderAnalytics(data: AnalyticsData) {
        binding.chartPerformance.setData(data.performancePoints)
        riskMetricAdapter.submitList(data.riskMetrics)
        binding.viewRegimeTimeline.setSegments(data.regimeSegments)
        binding.tvAnalyticsInsight.text =
            data.insights?.takeIf { it.isNotBlank() }
                ?: getString(R.string.analytics_insight_unavailable)
        updatePerformanceSummary(data)
    }

    private fun updatePerformanceSummary(data: AnalyticsData) {
        val latest = data.performancePoints.maxByOrNull { it.timestamp }
        if (latest == null) {
            val unavailable = getString(R.string.value_unavailable)
            binding.tvPeriodPortfolioReturn.text = unavailable
            binding.tvPeriodBenchmarkReturn.text = unavailable
            binding.tvPeriodRelativeReturn.text = unavailable
            val unavailableColor =
                ContextCompat.getColor(requireContext(), R.color.text_secondary_dark)
            binding.tvPeriodPortfolioReturn.setTextColor(unavailableColor)
            binding.tvPeriodBenchmarkReturn.setTextColor(unavailableColor)
            binding.tvPeriodRelativeReturn.setTextColor(unavailableColor)
            return
        }

        val relativeReturn = latest.portfolioReturn - latest.benchmarkReturn
        binding.tvPeriodPortfolioReturn.text = formatReturn(latest.portfolioReturn)
        binding.tvPeriodBenchmarkReturn.text = formatReturn(latest.benchmarkReturn)
        binding.tvPeriodRelativeReturn.text = formatReturn(relativeReturn)
        binding.tvPeriodPortfolioReturn.setTextColor(returnColor(latest.portfolioReturn))
        binding.tvPeriodBenchmarkReturn.setTextColor(returnColor(latest.benchmarkReturn))
        binding.tvPeriodRelativeReturn.setTextColor(returnColor(relativeReturn))
    }

    private fun formatReturn(value: Float): String =
        String.format(Locale.US, "%+.1f%%", value)

    private fun returnColor(value: Float): Int {
        val color = when {
            value > 0f -> R.color.market_positive
            value < 0f -> R.color.market_negative
            else -> R.color.market_neutral
        }
        return ContextCompat.getColor(requireContext(), color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}