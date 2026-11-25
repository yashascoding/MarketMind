package com.example.marketmind.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketmind.data.repository.MarketRepository
import com.example.marketmind.data.repository.MarketRepositoryImpl
import com.example.marketmind.data.repository.MockDataProvider
import com.example.marketmind.domain.model.AnalyticsData
import com.example.marketmind.ui.common.UiState
import com.example.marketmind.ui.views.PerformancePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MarketRepository = MarketRepositoryImpl(application)

    private val _analyticsState = MutableStateFlow<UiState<AnalyticsData>>(UiState.Loading)
    val analyticsState: StateFlow<UiState<AnalyticsData>> = _analyticsState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("1M")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    private var refreshJob: Job? = null
    private var rawData: AnalyticsData? = null

    init {
        loadData()
    }

    fun refreshData() {
        if (refreshJob?.isActive == true) return
        loadData()
    }

    fun selectPeriod(period: String) {
        _selectedPeriod.value = period
        filterByPeriod(period)
    }

    private fun filterByPeriod(period: String) {
        val current = rawData ?: MockDataProvider.getMockAnalytics(period)
        val filteredPoints = filterPointsByPeriod(current.performancePoints, period)
        val updatedData = current.copy(performancePoints = filteredPoints)
        _analyticsState.value = UiState.Success(updatedData)
    }

    private fun filterPointsByPeriod(points: List<PerformancePoint>, period: String): List<PerformancePoint> {
        if (points.isEmpty()) return MockDataProvider.getMockAnalytics(period).performancePoints
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val maxDays = when (period) {
            "1M" -> 30
            "3M" -> 90
            "6M" -> 180
            "1Y" -> 365
            else -> 3650
        }
        val cutoff = now - maxDays * dayMs
        val filtered = points.filter { it.timestamp >= cutoff }
        return if (filtered.isEmpty()) MockDataProvider.getMockAnalytics(period).performancePoints else filtered
    }

    private fun loadData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repository.getAnalytics().collect { state ->
                if (state is UiState.Success) {
                    rawData = state.data
                    filterByPeriod(_selectedPeriod.value)
                } else {
                    _analyticsState.value = state
                }
            }
        }
    }
}