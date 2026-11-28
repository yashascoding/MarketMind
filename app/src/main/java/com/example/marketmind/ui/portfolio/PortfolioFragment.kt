package com.example.marketmind.ui.portfolio

import android.graphics.Color
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
import com.example.marketmind.databinding.FragmentPortfolioBinding
import com.example.marketmind.domain.model.PortfolioRecommendation
import com.example.marketmind.ui.adapters.AllocationComparisonAdapter
import com.example.marketmind.ui.common.HapticFeedbackUtil
import com.example.marketmind.ui.common.UiState
import com.example.marketmind.ui.views.AllocationSlice
import kotlinx.coroutines.launch
import kotlin.math.abs

class PortfolioFragment : Fragment() {

    private var _binding: FragmentPortfolioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PortfolioViewModel by viewModels()
    private lateinit var allocationAdapter: AllocationComparisonAdapter
    private var allocations: List<com.example.marketmind.domain.model.PortfolioAllocation> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortfolioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        allocationAdapter = AllocationComparisonAdapter()
        binding.rvAllocations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = allocationAdapter
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

        binding.layoutRationaleHeader.setOnClickListener {
            HapticFeedbackUtil.performClickFeedback(it)
            viewModel.toggleRationaleExpanded()
        }

        binding.errorView.btnErrorRetry.setOnClickListener {
            viewModel.refreshData()
        }

        binding.emptyView.btnEmptyAction.setOnClickListener {
            viewModel.refreshData()
        }

        binding.chipGroupAllocations.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chip_alloc_increase) -> "increase"
                checkedIds.contains(R.id.chip_alloc_decrease) -> "decrease"
                else -> "all"
            }
            applyAllocationFilter(filter)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.portfolioState.collect { state ->
                        handlePortfolioState(state)
                    }
                }

                launch {
                    viewModel.isRationaleExpanded.collect { isExpanded ->
                        binding.tvRationaleBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        binding.ivExpand.setImageResource(
                            if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                        )
                    }
                }
            }
        }
    }

    private fun handlePortfolioState(state: UiState<PortfolioRecommendation>) {
        binding.swipeRefresh.isRefreshing = false
        when (state) {
            is UiState.Loading -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.VISIBLE
                binding.errorView.containerError.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.GONE
                binding.errorView.containerError.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.GONE

                renderRecommendation(state.data)
            }
            is UiState.Empty -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.VISIBLE
            }
            is UiState.Error -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.GONE
                val cached = state.cachedData as? PortfolioRecommendation
                if (cached != null) {
                    binding.errorView.containerError.visibility = View.GONE
                    binding.emptyView.containerEmpty.visibility = View.GONE
                    renderRecommendation(cached)
                } else {
                    binding.emptyView.containerEmpty.visibility = View.GONE
                    binding.errorView.containerError.visibility = View.VISIBLE
                    binding.errorView.tvErrorTitle.text = getString(R.string.error_backend_unavailable_title)
                    binding.errorView.tvErrorMessage.text = state.message
                }
            }
        }
    }

    private fun renderRecommendation(recommendation: PortfolioRecommendation) {
        allocations = recommendation.currentAllocations
        applyAllocationFilter(
            when {
                binding.chipAllocIncrease.isChecked -> "increase"
                binding.chipAllocDecrease.isChecked -> "decrease"
                else -> "all"
            }
        )

        val largestShift = allocations.maxByOrNull { abs(it.differencePercentage) }
        val difference = largestShift?.differencePercentage
        binding.tvMajorShift.text = difference?.let {
            String.format(java.util.Locale.US, "%+.1f%%", it)
        } ?: getString(R.string.value_unavailable)
        val shiftColor = when {
            difference == null -> R.color.text_secondary_dark
            difference > 0f -> R.color.market_positive
            difference < 0f -> R.color.market_negative
            else -> R.color.market_neutral
        }
        binding.tvMajorShift.setTextColor(ContextCompat.getColor(requireContext(), shiftColor))
        val riskExposure = recommendation.riskExposure
            ?.substringBefore("(")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        binding.tvPortfolioState.text = riskExposure ?: getString(R.string.portfolio_state_unknown)
        val exposureColor = when {
            riskExposure?.contains("critical", ignoreCase = true) == true -> R.color.risk_critical
            riskExposure?.contains("high", ignoreCase = true) == true -> R.color.risk_high
            riskExposure?.contains("moderate", ignoreCase = true) == true -> R.color.risk_moderate
            riskExposure?.contains("low", ignoreCase = true) == true -> R.color.risk_low
            else -> R.color.text_secondary_dark
        }
        binding.tvPortfolioState.setTextColor(
            ContextCompat.getColor(requireContext(), exposureColor)
        )

        val adjustmentCount = allocations.count { abs(it.differencePercentage) >= 0.05f }
        binding.tvAdjustmentSummary.text = when {
            allocations.isEmpty() -> getString(R.string.portfolio_no_allocation_data)
            adjustmentCount == 0 -> {
                getString(R.string.portfolio_no_adjustments)
            }
            else -> getString(R.string.portfolio_adjustment_count, adjustmentCount)
        }

        val currentSlices = allocations.map {
            AllocationSlice(it.assetName, it.currentPercentage, parseColorSafe(it.colorHex))
        }
        val suggestedSlices = allocations.map {
            AllocationSlice(it.assetName, it.suggestedPercentage, parseColorSafe(it.colorHex))
        }
        binding.chartCurrentAllocation.setData(currentSlices, "Current")
        binding.chartSuggestedAllocation.setData(suggestedSlices, "Suggested")
        binding.tvRationaleBody.text =
            recommendation.explanation ?: getString(R.string.portfolio_no_rationale)
    }

    private fun applyAllocationFilter(filter: String) {
        val filtered = when (filter) {
            "increase" -> allocations.filter { it.differencePercentage > 0 }
            "decrease" -> allocations.filter { it.differencePercentage < 0 }
            else -> allocations
        }
        allocationAdapter.submitList(filtered)
    }

    private fun parseColorSafe(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            Color.parseColor("#3B82F6")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}