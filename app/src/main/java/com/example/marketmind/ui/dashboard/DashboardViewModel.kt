package com.example.marketmind.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketmind.data.repository.MarketRepository
import com.example.marketmind.data.repository.MarketRepositoryImpl
import com.example.marketmind.data.repository.MockDataProvider
import com.example.marketmind.domain.model.MarketAsset
import com.example.marketmind.domain.model.RegimeData
import com.example.marketmind.domain.model.RiskData
import com.example.marketmind.ui.common.NetworkMonitor
import com.example.marketmind.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MarketRepository = MarketRepositoryImpl(application)
    private val networkMonitor = NetworkMonitor(application)

    private val _riskState = MutableStateFlow<UiState<RiskData>>(UiState.Loading)
    val riskState: StateFlow<UiState<RiskData>> = _riskState.asStateFlow()

    private val _regimeState = MutableStateFlow<UiState<RegimeData>>(UiState.Loading)
    val regimeState: StateFlow<UiState<RegimeData>> = _regimeState.asStateFlow()

    private val _overviewState = MutableStateFlow<UiState<List<MarketAsset>>>(UiState.Loading)
    val overviewState: StateFlow<UiState<List<MarketAsset>>> = _overviewState.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), networkMonitor.isCurrentlyOnline())

    private var refreshJob: Job? = null
    private var rawOverview: List<MarketAsset> = emptyList()
    private var lastRefreshTime = 0L

    init {
        loadData()
    }

    fun refreshData() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < 1000 || refreshJob?.isActive == true) return
        lastRefreshTime = now
        loadData()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        filterOverview(category)
    }

    private fun filterOverview(category: String) {
        val list = if (rawOverview.isNotEmpty()) rawOverview else MockDataProvider.getMockMarketOverview()
        val filtered = if (category.equals("ALL", ignoreCase = true)) {
            list
        } else {
            list.filter { it.category.equals(category, ignoreCase = true) }
        }
        _overviewState.value = UiState.Success(if (filtered.isEmpty()) list else filtered)
    }

    private fun loadData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            launch { repository.getRiskData().collect { _riskState.value = it } }
            launch { repository.getRegimeData().collect { _regimeState.value = it } }
            launch {
                repository.getMarketOverview().collect { state ->
                    if (state is UiState.Success) {
                        rawOverview = state.data
                        filterOverview(_selectedCategory.value)
                    } else {
                        _overviewState.value = state
                    }
                }
            }
        }
    }
}