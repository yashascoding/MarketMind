package com.example.marketmind.ui.dashboard

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.marketmind.R
import com.example.marketmind.databinding.FragmentDashboardBinding
import com.example.marketmind.domain.model.MarketAsset
import com.example.marketmind.domain.model.RegimeData
import com.example.marketmind.domain.model.RiskData
import com.example.marketmind.ui.adapters.AssetAdapter
import com.example.marketmind.ui.common.ErrorType
import com.example.marketmind.ui.common.HapticFeedbackUtil
import com.example.marketmind.ui.common.UiState
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var assetAdapter: AssetAdapter
    private var marketAssets: List<MarketAsset> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter()
        binding.rvAssets.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = assetAdapter
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

        binding.btnSettings.setOnClickListener {
            HapticFeedbackUtil.performClickFeedback(it)
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }

        binding.tvConnectionChip.setOnClickListener {
            HapticFeedbackUtil.performClickFeedback(it)
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }

        binding.errorView.btnErrorRetry.setOnClickListener {
            viewModel.refreshData()
        }

        binding.errorView.btnErrorSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }

        binding.emptyView.btnEmptyAction.setOnClickListener {
            viewModel.refreshData()
        }

        binding.chipGroupAssets.setOnCheckedStateChangeListener { _, checkedIds ->
            val filterCategory = when {
                checkedIds.contains(R.id.chip_assets_indices) -> "Indices"
                checkedIds.contains(R.id.chip_assets_equities) -> "Equities"
                checkedIds.contains(R.id.chip_assets_commodities) -> "Commodities"
                checkedIds.contains(R.id.chip_assets_bonds) -> "Bonds"
                else -> "All"
            }
            applyAssetCategoryFilter(filterCategory)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOnline.collect { isOnline ->
                        updateConnectionChip(isOnline)
                    }
                }

                launch {
                    viewModel.riskState.collect { state ->
                        handleRiskState(state)
                    }
                }

                launch {
                    viewModel.regimeState.collect { state ->
                        handleRegimeState(state)
                    }
                }

                launch {
                    viewModel.overviewState.collect { state ->
                        handleOverviewState(state)
                    }
                }
            }
        }
    }

    private fun updateConnectionChip(isOnline: Boolean) {
        if (isOnline) {
            binding.tvConnectionChip.text = getString(R.string.status_connected)
            binding.tvConnectionChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_online))
            binding.tvConnectionChip.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        } else {
            binding.tvConnectionChip.text = getString(R.string.status_offline)
            binding.tvConnectionChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
            binding.tvConnectionChip.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }

    private fun handleRiskState(state: UiState<RiskData>) {
        binding.swipeRefresh.isRefreshing = false
        when (state) {
            is UiState.Loading -> {
                binding.skeletonView.containerSkeletonDashboard.visibility = View.VISIBLE
                binding.errorView.containerError.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.skeletonView.containerSkeletonDashboard.visibility = View.GONE
                binding.errorView.containerError.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.GONE

                val risk = state.data
                binding.gaugeRisk.setRiskProbability(risk.probability)
                binding.tvRiskExplanation.text = risk.explanation ?: getString(R.string.risk_no_data)
                binding.chartHistoricalRisk.setData(risk.history)

                val asOfText = if (state.isStale) {
                    getString(R.string.status_stale, risk.asOfTimestamp)
                } else {
                    getString(R.string.last_updated, risk.asOfTimestamp)
                }
                binding.tvLastUpdated.text = asOfText
            }
            is UiState.Empty -> {
                binding.skeletonView.containerSkeletonDashboard.visibility = View.GONE
                binding.emptyView.containerEmpty.visibility = View.VISIBLE
            }
            is UiState.Error -> {
                binding.skeletonView.containerSkeletonDashboard.visibility = View.GONE
                val cached = state.cachedData as? RiskData
                if (cached != null) {
                    binding.gaugeRisk.setRiskProbability(cached.probability)
                    binding.tvRiskExplanation.text = cached.explanation ?: getString(R.string.risk_no_data)
                    binding.chartHistoricalRisk.setData(cached.history)
                    binding.tvLastUpdated.text = getString(R.string.status_stale, cached.asOfTimestamp)
                } else {
                    showErrorUI(state.type, state.message)
                }
            }
        }
    }

    private fun handleRegimeState(state: UiState<RegimeData>) {
        val regime = when (state) {
            is UiState.Success -> state.data
            is UiState.Error -> state.cachedData as? RegimeData
            else -> null
        }
        regime?.let {
            binding.tvRegimeName.text = it.regime
            binding.tvRegimeDescription.text = it.description ?: getRegimeDescription(it.regime)
            it.confidence?.let { conf ->
                binding.tvRegimeConfidence.text = getString(R.string.regime_confidence_format, conf)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleOverviewState(state: UiState<List<MarketAsset>>) {
        val list = when (state) {
            is UiState.Success -> state.data
            is UiState.Error -> state.cachedData as? List<MarketAsset>
            else -> null
        }
        list?.let {
            marketAssets = it
            applyAssetCategoryFilter(getCurrentFilter())
        }
    }

    private fun getCurrentFilter(): String {
        return when {
            binding.chipAssetsIndices.isChecked -> "Indices"
            binding.chipAssetsEquities.isChecked -> "Equities"
            binding.chipAssetsCommodities.isChecked -> "Commodities"
            binding.chipAssetsBonds.isChecked -> "Bonds"
            else -> "All"
        }
    }

    private fun applyAssetCategoryFilter(category: String) {
        val filtered = if (category.equals("All", ignoreCase = true)) {
            marketAssets
        } else {
            marketAssets.filter { it.category.equals(category, ignoreCase = true) }
        }
        assetAdapter.submitList(if (filtered.isEmpty()) marketAssets else filtered)
    }

    private fun getRegimeDescription(regime: String): String {
        return when (regime.lowercase()) {
            "calm" -> getString(R.string.regime_desc_calm)
            "volatile" -> getString(R.string.regime_desc_volatile)
            "bearish" -> getString(R.string.regime_desc_bearish)
            else -> getString(R.string.regime_desc_volatile)
        }
    }

    private fun showErrorUI(type: ErrorType, message: String) {
        binding.errorView.containerError.visibility = View.VISIBLE
        when (type) {
            ErrorType.NO_INTERNET -> {
                binding.errorView.tvErrorTitle.text = getString(R.string.error_no_internet_title)
                binding.errorView.tvErrorMessage.text = getString(R.string.error_no_internet_msg)
            }
            ErrorType.TIMEOUT -> {
                binding.errorView.tvErrorTitle.text = getString(R.string.error_timeout_title)
                binding.errorView.tvErrorMessage.text = getString(R.string.error_timeout_msg)
            }
            ErrorType.BACKEND_UNAVAILABLE -> {
                binding.errorView.tvErrorTitle.text = getString(R.string.error_backend_unavailable_title)
                binding.errorView.tvErrorMessage.text = getString(R.string.error_backend_unavailable_msg)
            }
            else -> {
                binding.errorView.tvErrorTitle.text = getString(R.string.error_api_error_title)
                binding.errorView.tvErrorMessage.text = message
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}