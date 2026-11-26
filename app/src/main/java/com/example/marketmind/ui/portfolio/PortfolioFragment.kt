package com.example.marketmind.ui.portfolio

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

                val recommendation = state.data
                allocations = recommendation.currentAllocations
                applyAllocationFilter(
                    when {
                        binding.chipAllocIncrease.isChecked -> "increase"
                        binding.chipAllocDecrease.isChecked -> "decrease"
                        else -> "all"
                    }
                )

                val largestShift = recommendation.currentAllocations.minByOrNull {
                    it.differencePercentage
                }
                binding.tvMajorShift.text = String.format(
                    java.util.Locale.US,
                    "%+.1f%%",
                    largestShift?.differencePercentage ?: 0f
                )
                binding.tvPortfolioState.text =
                    recommendation.riskExposure?.substringBefore("(")?.trim() ?: "Balanced"

                // Update Donut Charts
                val currentSlices = recommendation.currentAllocations.map {
                    AllocationSlice(it.assetName, it.currentPercentage, parseColorSafe(it.colorHex))
                }
                val suggestedSlices = recommendation.currentAllocations.map {
                    AllocationSlice(it.assetName, it.suggestedPercentage, parseColorSafe(it.colorHex))
                }

                binding.chartCurrentAllocation.setData(currentSlices, "Current")
                binding.chartSuggestedAllocation.setData(suggestedSlices, "Suggested")

                binding.tvRationaleBody.text = recommendation.explanation ?: getString(R.string.settings_disclaimer)
            }
            is UiState.Empty -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.VISIBLE
            }
            is UiState.Error -> {
                binding.skeletonView.containerSkeletonPortfolio.visibility = View.GONE
                val cached = state.cachedData as? PortfolioRecommendation
                if (cached != null) {
                    allocations = cached.currentAllocations
                    applyAllocationFilter("all")
                } else {
                    binding.errorView.containerError.visibility = View.VISIBLE
                    binding.errorView.tvErrorTitle.text = getString(R.string.error_backend_unavailable_title)
                    binding.errorView.tvErrorMessage.text = state.message
                }
            }
        }
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