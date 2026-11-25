package com.example.marketmind.data.repository

import android.content.Context
import com.example.marketmind.data.api.ApiClient
import com.example.marketmind.data.cache.LocalCacheManager
import com.example.marketmind.data.cache.SettingsDataStore
import com.example.marketmind.data.dto.*
import com.example.marketmind.domain.mapper.MarketMappers
import com.example.marketmind.domain.model.*
import com.example.marketmind.ui.common.ErrorType
import com.example.marketmind.ui.common.Formatters
import com.example.marketmind.ui.common.NetworkMonitor
import com.example.marketmind.ui.common.UiState
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.net.SocketTimeoutException

class MarketRepositoryImpl(
    private val context: Context,
    private val cacheManager: LocalCacheManager = LocalCacheManager(context),
    private val settingsDataStore: SettingsDataStore = SettingsDataStore(context),
    private val networkMonitor: NetworkMonitor = NetworkMonitor(context),
    private val gson: Gson = Gson()
) : MarketRepository {

    private suspend fun getApiService() = ApiClient.createApiService(settingsDataStore.baseUrl.first())

    override fun getRiskData(): Flow<UiState<RiskData>> = flow {
        emit(UiState.Loading)

        val isDemo = settingsDataStore.demoMode.first()

        // Stale-While-Revalidate: load cache first
        val cachedJson = cacheManager.getCache(LocalCacheManager.KEY_CACHE_RISK)
        var cachedModel: RiskData? = null
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val dto = gson.fromJson(cachedJson, RiskDto::class.java)
                cachedModel = MarketMappers.mapRisk(dto)
                if (cachedModel != null) {
                    emit(UiState.Success(cachedModel, isStale = true, asOfTimestamp = cachedModel.asOfTimestamp))
                }
            } catch (e: Exception) {
                // Ignore corrupt cache
            }
        }

        if (isDemo && cachedModel == null) {
            val mockData = MockDataProvider.getMockRiskData()
            emit(UiState.Success(mockData, isStale = false, asOfTimestamp = mockData.asOfTimestamp))
            return@flow
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            val fallback = cachedModel ?: MockDataProvider.getMockRiskData()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            return@flow
        }

        try {
            val response = getApiService().getPredictRisk()
            if (response.isSuccessful) {
                val dto = response.body()
                val model = MarketMappers.mapRisk(dto)
                if (model != null) {
                    val rawJson = gson.toJson(dto)
                    cacheManager.saveCache(LocalCacheManager.KEY_CACHE_RISK, rawJson)
                    emit(UiState.Success(model, isStale = false, asOfTimestamp = model.asOfTimestamp))
                } else {
                    val mock = MockDataProvider.getMockRiskData()
                    emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp))
                }
            } else {
                val fallback = cachedModel ?: MockDataProvider.getMockRiskData()
                emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            }
        } catch (e: Exception) {
            val fallback = cachedModel ?: MockDataProvider.getMockRiskData()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
        }
    }

    override fun getRegimeData(): Flow<UiState<RegimeData>> = flow {
        emit(UiState.Loading)

        val isDemo = settingsDataStore.demoMode.first()

        val cachedJson = cacheManager.getCache(LocalCacheManager.KEY_CACHE_REGIME)
        var cachedModel: RegimeData? = null
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val dto = gson.fromJson(cachedJson, RegimeDto::class.java)
                cachedModel = MarketMappers.mapRegime(dto)
                if (cachedModel != null) {
                    emit(UiState.Success(cachedModel, isStale = true, asOfTimestamp = cachedModel.asOfTimestamp))
                }
            } catch (e: Exception) { }
        }

        if (isDemo && cachedModel == null) {
            val mock = MockDataProvider.getMockRegimeData()
            emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp))
            return@flow
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            val fallback = cachedModel ?: MockDataProvider.getMockRegimeData()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            return@flow
        }

        try {
            val response = getApiService().getMarketRegime()
            if (response.isSuccessful) {
                val dto = response.body()
                val model = MarketMappers.mapRegime(dto)
                if (model != null) {
                    val rawJson = gson.toJson(dto)
                    cacheManager.saveCache(LocalCacheManager.KEY_CACHE_REGIME, rawJson)
                    emit(UiState.Success(model, isStale = false, asOfTimestamp = model.asOfTimestamp))
                } else {
                    val mock = MockDataProvider.getMockRegimeData()
                    emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp))
                }
            } else {
                val fallback = cachedModel ?: MockDataProvider.getMockRegimeData()
                emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            }
        } catch (e: Exception) {
            val fallback = cachedModel ?: MockDataProvider.getMockRegimeData()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
        }
    }

    override fun getMarketOverview(): Flow<UiState<List<MarketAsset>>> = flow {
        emit(UiState.Loading)

        val isDemo = settingsDataStore.demoMode.first()

        val cachedJson = cacheManager.getCache(LocalCacheManager.KEY_CACHE_OVERVIEW)
        var cachedList: List<MarketAsset> = emptyList()
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val dto = gson.fromJson(cachedJson, MarketOverviewDto::class.java)
                cachedList = MarketMappers.mapMarketOverview(dto)
                if (cachedList.isNotEmpty()) {
                    emit(UiState.Success(cachedList, isStale = true, asOfTimestamp = Formatters.formatTimestamp(null)))
                }
            } catch (e: Exception) { }
        }

        if (isDemo && cachedList.isEmpty()) {
            val mock = MockDataProvider.getMockMarketOverview()
            emit(UiState.Success(mock, isStale = false, asOfTimestamp = Formatters.formatTimestamp(null)))
            return@flow
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            val fallback = if (cachedList.isNotEmpty()) cachedList else MockDataProvider.getMockMarketOverview()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = Formatters.formatTimestamp(null)))
            return@flow
        }

        try {
            val response = getApiService().getMarketOverview()
            if (response.isSuccessful) {
                val dto = response.body()
                val list = MarketMappers.mapMarketOverview(dto)
                if (list.isNotEmpty()) {
                    val rawJson = gson.toJson(dto)
                    cacheManager.saveCache(LocalCacheManager.KEY_CACHE_OVERVIEW, rawJson)
                    emit(UiState.Success(list, isStale = false, asOfTimestamp = Formatters.formatTimestamp(null)))
                } else {
                    val mock = MockDataProvider.getMockMarketOverview()
                    emit(UiState.Success(mock, isStale = false, asOfTimestamp = Formatters.formatTimestamp(null)))
                }
            } else {
                val fallback = if (cachedList.isNotEmpty()) cachedList else MockDataProvider.getMockMarketOverview()
                emit(UiState.Success(fallback, isStale = true, asOfTimestamp = Formatters.formatTimestamp(null)))
            }
        } catch (e: Exception) {
            val fallback = if (cachedList.isNotEmpty()) cachedList else MockDataProvider.getMockMarketOverview()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = Formatters.formatTimestamp(null)))
        }
    }

    override fun getPortfolioRecommendation(): Flow<UiState<PortfolioRecommendation>> = flow {
        emit(UiState.Loading)

        val isDemo = settingsDataStore.demoMode.first()

        val cachedJson = cacheManager.getCache(LocalCacheManager.KEY_CACHE_PORTFOLIO)
        var cachedModel: PortfolioRecommendation? = null
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val dto = gson.fromJson(cachedJson, PortfolioRecommendationDto::class.java)
                cachedModel = MarketMappers.mapPortfolioRecommendation(dto)
                if (cachedModel != null) {
                    emit(UiState.Success(cachedModel, isStale = true, asOfTimestamp = cachedModel.asOfTimestamp))
                }
            } catch (e: Exception) { }
        }

        if (isDemo && cachedModel == null) {
            val mock = MockDataProvider.getMockPortfolioRecommendation()
            emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp))
            return@flow
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            val fallback = cachedModel ?: MockDataProvider.getMockPortfolioRecommendation()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            return@flow
        }

        try {
            val response = getApiService().getPortfolioRecommend()
            if (response.isSuccessful) {
                val dto = response.body()
                val model = MarketMappers.mapPortfolioRecommendation(dto)
                if (model != null) {
                    val rawJson = gson.toJson(dto)
                    cacheManager.saveCache(LocalCacheManager.KEY_CACHE_PORTFOLIO, rawJson)
                    emit(UiState.Success(model, isStale = false, asOfTimestamp = model.asOfTimestamp))
                } else {
                    val mock = MockDataProvider.getMockPortfolioRecommendation()
                    emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp))
                }
            } else {
                val fallback = cachedModel ?: MockDataProvider.getMockPortfolioRecommendation()
                emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
            }
        } catch (e: Exception) {
            val fallback = cachedModel ?: MockDataProvider.getMockPortfolioRecommendation()
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp))
        }
    }

    override fun getAnalytics(): Flow<UiState<AnalyticsData>> = flow {
        emit(UiState.Loading)

        val isDemo = settingsDataStore.demoMode.first()

        val cachedJson = cacheManager.getCache(LocalCacheManager.KEY_CACHE_ANALYTICS)
        var cachedModel: AnalyticsData? = null
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val dto = gson.fromJson(cachedJson, AnalyticsDto::class.java)
                cachedModel = MarketMappers.mapAnalytics(dto)
                if (cachedModel.performancePoints.isNotEmpty() || cachedModel.riskMetrics.isNotEmpty()) {
                    emit(UiState.Success(cachedModel, isStale = true, asOfTimestamp = cachedModel.asOfTimestamp))
                }
            } catch (e: Exception) { }
        }

        if (isDemo && (cachedModel == null || cachedModel.performancePoints.isEmpty())) {
            val mock = MockDataProvider.getMockAnalytics("1M")
            emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp ?: Formatters.formatTimestamp(null)))
            return@flow
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            val fallback = if (cachedModel != null && cachedModel.performancePoints.isNotEmpty()) cachedModel else MockDataProvider.getMockAnalytics("1M")
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp ?: Formatters.formatTimestamp(null)))
            return@flow
        }

        try {
            val response = getApiService().getAnalytics()
            if (response.isSuccessful) {
                val dto = response.body()
                val model = MarketMappers.mapAnalytics(dto)
                if (model.performancePoints.isNotEmpty() || model.riskMetrics.isNotEmpty()) {
                    val rawJson = gson.toJson(dto)
                    cacheManager.saveCache(LocalCacheManager.KEY_CACHE_ANALYTICS, rawJson)
                    emit(UiState.Success(model, isStale = false, asOfTimestamp = model.asOfTimestamp))
                } else {
                    val mock = MockDataProvider.getMockAnalytics("1M")
                    emit(UiState.Success(mock, isStale = false, asOfTimestamp = mock.asOfTimestamp ?: Formatters.formatTimestamp(null)))
                }
            } else {
                val fallback = if (cachedModel != null && cachedModel.performancePoints.isNotEmpty()) cachedModel else MockDataProvider.getMockAnalytics("1M")
                emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp ?: Formatters.formatTimestamp(null)))
            }
        } catch (e: Exception) {
            val fallback = if (cachedModel != null && cachedModel.performancePoints.isNotEmpty()) cachedModel else MockDataProvider.getMockAnalytics("1M")
            emit(UiState.Success(fallback, isStale = true, asOfTimestamp = fallback.asOfTimestamp ?: Formatters.formatTimestamp(null)))
        }
    }

    override suspend fun testConnection(baseUrl: String): Pair<Boolean, String> {
        return try {
            val service = ApiClient.createApiService(baseUrl)
            val response = service.getPredictRisk()
            if (response.isSuccessful) {
                Pair(true, "Connected successfully (HTTP ${response.code()})")
            } else {
                Pair(false, "Server error (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Pair(false, "Connection failed: ${e.localizedMessage}")
        }
    }
}