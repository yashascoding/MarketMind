package com.example.marketmind.domain.model

import com.example.marketmind.ui.views.PerformancePoint
import com.example.marketmind.ui.views.RegimeSegment
import com.example.marketmind.ui.views.RiskPoint

data class RiskData(
    val probability: Float, // 0 to 100
    val category: String,   // Low, Moderate, High, Critical
    val explanation: String?,
    val asOfTimestamp: String,
    val history: List<RiskPoint> = emptyList()
)

data class RegimeData(
    val regime: String,      // Calm, Volatile, Bearish
    val confidence: Float?,  // 0 to 100 or null
    val description: String?,
    val asOfTimestamp: String
)

data class MarketAsset(
    val id: String,
    val name: String,
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val trend: String,        // "up", "down", "neutral"
    val category: String = "Indices"
)

data class PortfolioAllocation(
    val assetId: String,
    val assetName: String,
    val currentPercentage: Float,
    val suggestedPercentage: Float,
    val differencePercentage: Float,
    val colorHex: String
)

data class PortfolioRecommendation(
    val currentAllocations: List<PortfolioAllocation>,
    val explanation: String?,
    val riskExposure: String?,
    val asOfTimestamp: String
)

data class RiskMetric(
    val name: String,
    val value: String,
    val unit: String,
    val description: String?
)

data class AnalyticsData(
    val performancePoints: List<PerformancePoint> = emptyList(),
    val riskMetrics: List<RiskMetric> = emptyList(),
    val regimeSegments: List<RegimeSegment> = emptyList(),
    val insights: String? = null,
    val asOfTimestamp: String? = null
)