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
import com.google.gson.JsonParseException
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
            if (cachedModel == null) {
                emit(UiState.Error(ErrorType.NO_INTERNET, "No network connection and no cached risk estimate."))
            }
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
                    emit(UiState.Error(ErrorType.PARSE_ERROR, "Backend returned an invalid risk response.", cachedModel))
                }
            } else {
                emit(UiState.Error(
                    if (response.code() >= 500) ErrorType.BACKEND_UNAVAILABLE else ErrorType.API_ERROR,
                    "Risk request failed with HTTP ${response.code()}.",
                    cachedModel
                ))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Error(ErrorType.TIMEOUT, e.localizedMessage ?: "Risk request timed out.", cachedModel))
        } catch (e: IOException) {
            emit(UiState.Error(ErrorType.BACKEND_UNAVAILABLE, e.localizedMessage ?: "Risk service is unreachable.", cachedModel))
        } catch (e: JsonParseException) {
            emit(UiState.Error(ErrorType.PARSE_ERROR, e.localizedMessage ?: "Risk response could not be parsed.", cachedModel))
        } catch (e: IllegalArgumentException) {
            emit(UiState.Error(ErrorType.API_ERROR, e.localizedMessage ?: "Risk request configuration is invalid.", cachedModel))
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
            if (cachedModel == null) {
                emit(UiState.Error(ErrorType.NO_INTERNET, "No network connection and no cached regime estimate."))
            }
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
                    emit(UiState.Error(ErrorType.PARSE_ERROR, "Backend returned an invalid regime response.", cachedModel))
                }
            } else {
                emit(UiState.Error(
                    if (response.code() >= 500) ErrorType.BACKEND_UNAVAILABLE else ErrorType.API_ERROR,
                    "Regime request failed with HTTP ${response.code()}.",
                    cachedModel
                ))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Error(ErrorType.TIMEOUT, e.localizedMessage ?: "Regime request timed out.", cachedModel))
        } catch (e: IOException) {
            emit(UiState.Error(ErrorType.BACKEND_UNAVAILABLE, e.localizedMessage ?: "Regime service is unreachable.", cachedModel))
        } catch (e: JsonParseException) {
            emit(UiState.Error(ErrorType.PARSE_ERROR, e.localizedMessage ?: "Regime response could not be parsed.", cachedModel))
        } catch (e: IllegalArgumentException) {
            emit(UiState.Error(ErrorType.API_ERROR, e.localizedMessage ?: "Regime request configuration is invalid.", cachedModel))
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
            if (cachedList.isEmpty()) {
                emit(UiState.Error(ErrorType.NO_INTERNET, "No network connection and no cached market data."))
            }
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
                    emit(UiState.Empty)
                }
            } else {
                emit(UiState.Error(
                    if (response.code() >= 500) ErrorType.BACKEND_UNAVAILABLE else ErrorType.API_ERROR,
                    "Market overview request failed with HTTP ${response.code()}.",
                    cachedList.takeIf { it.isNotEmpty() }
                ))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Error(ErrorType.TIMEOUT, e.localizedMessage ?: "Market data request timed out.", cachedList.takeIf { it.isNotEmpty() }))
        } catch (e: IOException) {
            emit(UiState.Error(ErrorType.BACKEND_UNAVAILABLE, e.localizedMessage ?: "Market data service is unreachable.", cachedList.takeIf { it.isNotEmpty() }))
        } catch (e: JsonParseException) {
            emit(UiState.Error(ErrorType.PARSE_ERROR, e.localizedMessage ?: "Market data response could not be parsed.", cachedList.takeIf { it.isNotEmpty() }))
        } catch (e: IllegalArgumentException) {
            emit(UiState.Error(ErrorType.API_ERROR, e.localizedMessage ?: "Market data request configuration is invalid.", cachedList.takeIf { it.isNotEmpty() }))
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
            if (cachedModel == null) {
                emit(UiState.Error(ErrorType.NO_INTERNET, "No network connection and no cached portfolio estimate."))
            }
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
                    emit(UiState.Error(ErrorType.PARSE_ERROR, "Backend returned an invalid portfolio response.", cachedModel))
                }
            } else {
                emit(UiState.Error(
                    if (response.code() >= 500) ErrorType.BACKEND_UNAVAILABLE else ErrorType.API_ERROR,
                    "Portfolio request failed with HTTP ${response.code()}.",
                    cachedModel
                ))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Error(ErrorType.TIMEOUT, e.localizedMessage ?: "Portfolio request timed out.", cachedModel))
        } catch (e: IOException) {
            emit(UiState.Error(ErrorType.BACKEND_UNAVAILABLE, e.localizedMessage ?: "Portfolio service is unreachable.", cachedModel))
        } catch (e: JsonParseException) {
            emit(UiState.Error(ErrorType.PARSE_ERROR, e.localizedMessage ?: "Portfolio response could not be parsed.", cachedModel))
        } catch (e: IllegalArgumentException) {
            emit(UiState.Error(ErrorType.API_ERROR, e.localizedMessage ?: "Portfolio request configuration is invalid.", cachedModel))
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
            if (cachedModel == null || cachedModel.performancePoints.isEmpty()) {
                emit(UiState.Error(ErrorType.NO_INTERNET, "No network connection and no cached analytics."))
            }
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
                    emit(UiState.Empty)
                }
            } else {
                emit(UiState.Error(
                    if (response.code() >= 500) ErrorType.BACKEND_UNAVAILABLE else ErrorType.API_ERROR,
                    "Analytics request failed with HTTP ${response.code()}.",
                    cachedModel?.takeIf { it.performancePoints.isNotEmpty() }
                ))
            }
        } catch (e: SocketTimeoutException) {
            emit(UiState.Error(ErrorType.TIMEOUT, e.localizedMessage ?: "Analytics request timed out.", cachedModel))
        } catch (e: IOException) {
            emit(UiState.Error(ErrorType.BACKEND_UNAVAILABLE, e.localizedMessage ?: "Analytics service is unreachable.", cachedModel))
        } catch (e: JsonParseException) {
            emit(UiState.Error(ErrorType.PARSE_ERROR, e.localizedMessage ?: "Analytics response could not be parsed.", cachedModel))
        } catch (e: IllegalArgumentException) {
            emit(UiState.Error(ErrorType.API_ERROR, e.localizedMessage ?: "Analytics request configuration is invalid.", cachedModel))
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