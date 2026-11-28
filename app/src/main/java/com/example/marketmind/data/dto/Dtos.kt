package com.example.marketmind.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Backend response DTO for GET /predict-risk
 * All fields nullable to handle contract changes gracefully.
 */
data class RiskDto(
    @SerializedName("risk_probability") val riskProbability: Float? = null,
    @SerializedName("risk_threshold") val riskThreshold: Float? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("explanation") val explanation: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("history") val history: List<RiskHistoryItemDto>? = null
)

data class RiskHistoryItemDto(
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("probability") val probability: Float? = null
)

/**
 * Backend response DTO for GET /market-regime
 */
data class RegimeDto(
    @SerializedName("regime") val regime: String? = null,
    @SerializedName("confidence") val confidence: Float? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

/**
 * Backend response DTO for GET /portfolio/recommend
 */
data class PortfolioRecommendationDto(
    @SerializedName("allocations") val allocations: List<AllocationItemDto>? = null,
    @SerializedName("explanation") val explanation: String? = null,
    @SerializedName("risk_exposure") val riskExposure: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("current_allocation_source") val currentAllocationSource: String? = null
)

data class AllocationItemDto(
    @SerializedName("asset_id") val assetId: String? = null,
    @SerializedName("asset_name") val assetName: String? = null,
    @SerializedName("current_pct") val currentPct: Float? = null,
    @SerializedName("suggested_pct") val suggestedPct: Float? = null,
    @SerializedName("difference_pct") val differencePct: Float? = null,
    @SerializedName("color") val color: String? = null
)

/**
 * Backend response DTO for GET /market-overview
 */
data class MarketOverviewDto(
    @SerializedName("assets") val assets: List<AssetItemDto>? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class AssetItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("change_pct") val changePct: Double? = null,
    @SerializedName("trend") val trend: String? = null,
    @SerializedName("category") val category: String? = null
)

/**
 * Placeholder DTO for future analytics endpoint
 */
data class AnalyticsDto(
    @SerializedName("performance") val performance: List<PerformanceItemDto>? = null,
    @SerializedName("sharpe_ratio") val sharpeRatio: Double? = null,
    @SerializedName("max_drawdown") val maxDrawdown: Double? = null,
    @SerializedName("volatility") val volatility: Double? = null,
    @SerializedName("insights") val insights: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("regime_segments") val regimeSegments: List<RegimeSegmentDto>? = null,
    @SerializedName("model_performance") val modelPerformance: Map<String, ModelEvaluationDto>? = null
)

data class PerformanceItemDto(
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("portfolio_return") val portfolioReturn: Float? = null,
    @SerializedName("benchmark_return") val benchmarkReturn: Float? = null
)

data class RegimeSegmentDto(
    @SerializedName("regime") val regime: String? = null,
    @SerializedName("percentage") val percentage: Float? = null
)

data class ModelEvaluationDto(
    @SerializedName("test") val test: ModelMetricsDto? = null
)

data class ModelMetricsDto(
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("precision") val precision: Double? = null,
    @SerializedName("recall") val recall: Double? = null,
    @SerializedName("f1") val f1: Double? = null,
    @SerializedName("roc_auc") val rocAuc: Double? = null,
    @SerializedName("pr_auc") val prAuc: Double? = null,
    @SerializedName("brier_score") val brierScore: Double? = null
)