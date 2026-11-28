package com.example.marketmind.domain.mapper

import android.graphics.Color
import com.example.marketmind.data.dto.*
import com.example.marketmind.domain.model.*
import com.example.marketmind.ui.views.PerformancePoint
import com.example.marketmind.ui.views.RegimeSegment
import com.example.marketmind.ui.views.RiskPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MarketMappers {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun mapRisk(dto: RiskDto?): RiskData? {
        if (dto == null || dto.riskProbability == null) return null
        val prob = dto.riskProbability.coerceIn(0f, 100f)
        val level = dto.riskLevel ?: when {
            prob < 25f -> "Low"
            prob < 50f -> "Moderate"
            prob < 75f -> "High"
            else -> "Critical"
        }
        val history = dto.history?.mapNotNull { item ->
            if (item.timestamp != null && item.probability != null) {
                RiskPoint(item.timestamp, item.probability)
            } else null
        } ?: emptyList()

        return RiskData(
            probability = prob,
            category = level,
            explanation = dto.explanation,
            asOfTimestamp = dto.timestamp ?: timeFormat.format(Date()),
            threshold = dto.riskThreshold?.coerceIn(0f, 100f) ?: 50f,
            history = history
        )
    }

    fun mapRegime(dto: RegimeDto?): RegimeData? {
        if (dto == null || dto.regime == null) return null
        return RegimeData(
            regime = dto.regime,
            confidence = dto.confidence,
            description = dto.description,
            asOfTimestamp = dto.timestamp ?: timeFormat.format(Date())
        )
    }

    fun mapMarketOverview(dto: MarketOverviewDto?): List<MarketAsset> {
        if (dto?.assets == null) return emptyList()
        return dto.assets.mapNotNull { item ->
            if (item.id != null && item.name != null && item.price != null) {
                MarketAsset(
                    id = item.id,
                    name = item.name,
                    symbol = item.symbol ?: item.id.uppercase(),
                    price = item.price,
                    changePercent = item.changePct ?: 0.0,
                    trend = item.trend ?: if ((item.changePct ?: 0.0) >= 0) "up" else "down",
                    category = item.category ?: "Indices"
                )
            } else null
        }
    }

    fun mapPortfolioRecommendation(dto: PortfolioRecommendationDto?): PortfolioRecommendation? {
        if (dto?.allocations == null) return null
        val list = dto.allocations.mapNotNull { item ->
            if (item.assetName != null && item.currentPct != null && item.suggestedPct != null) {
                val diff = item.differencePct ?: (item.suggestedPct - item.currentPct)
                PortfolioAllocation(
                    assetId = item.assetId ?: item.assetName,
                    assetName = item.assetName,
                    currentPercentage = item.currentPct,
                    suggestedPercentage = item.suggestedPct,
                    differencePercentage = diff,
                    colorHex = item.color ?: "#3B82F6"
                )
            } else null
        }
        return PortfolioRecommendation(
            currentAllocations = list,
            explanation = listOfNotNull(
                dto.explanation,
                dto.currentAllocationSource
                    ?.takeIf { it.contains("benchmark assumption", ignoreCase = true) }
                    ?.let { "Current weights use a 60/20/20 benchmark assumption, not your actual holdings." }
            ).joinToString(" ").takeIf { it.isNotBlank() },
            riskExposure = dto.riskExposure,
            asOfTimestamp = dto.timestamp ?: timeFormat.format(Date())
        )
    }

    fun mapAnalytics(dto: AnalyticsDto?): AnalyticsData {
        if (dto == null) return AnalyticsData()

        val perfPoints = dto.performance?.mapNotNull { item ->
            if (item.timestamp != null && item.portfolioReturn != null && item.benchmarkReturn != null) {
                PerformancePoint(item.timestamp, item.portfolioReturn, item.benchmarkReturn)
            } else null
        } ?: emptyList()

        val metrics = mutableListOf<RiskMetric>()
        dto.sharpeRatio?.let {
            metrics.add(RiskMetric("Sharpe Ratio", String.format(Locale.US, "%.2f", it), "", "Risk-adjusted return measure"))
        }
        dto.maxDrawdown?.let {
            metrics.add(RiskMetric("Max Drawdown", String.format(Locale.US, "%.1f%%", it), "%", "Maximum peak-to-trough decline"))
        }
        dto.volatility?.let {
            metrics.add(RiskMetric("Annualized Volatility", String.format(Locale.US, "%.1f%%", it), "%", "Annualized standard deviation of returns"))
        }
        dto.modelPerformance?.forEach { (modelName, evaluation) ->
            evaluation.test?.let { test ->
                listOf(
                    "Accuracy" to test.accuracy,
                    "Precision" to test.precision,
                    "Recall" to test.recall,
                    "F1-score" to test.f1,
                    "ROC-AUC" to test.rocAuc,
                    "PR-AUC" to test.prAuc,
                    "Brier score" to test.brierScore
                ).forEach { (metricName, value) ->
                    value?.let {
                        metrics.add(
                            RiskMetric(
                                "$modelName test $metricName",
                                String.format(Locale.US, "%.3f", it),
                                "",
                                "Chronological unseen test-period model comparison"
                            )
                        )
                    }
                }
            }
        }

        val regimeColors = mapOf(
            "calm" to 0xFF10B981.toInt(),
            "volatile" to 0xFFF59E0B.toInt(),
            "bearish" to 0xFFEF4444.toInt()
        )
        val regimeSegments = dto.regimeSegments.orEmpty().mapNotNull { item ->
            val name = item.regime ?: return@mapNotNull null
            val percentage = item.percentage ?: return@mapNotNull null
            val color = regimeColors[name.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            RegimeSegment(name, percentage, color)
        }

        return AnalyticsData(
            performancePoints = perfPoints,
            riskMetrics = metrics,
            regimeSegments = regimeSegments,
            insights = dto.insights,
            asOfTimestamp = dto.timestamp ?: timeFormat.format(Date())
        )
    }
}