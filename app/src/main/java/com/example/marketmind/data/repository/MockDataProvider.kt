package com.example.marketmind.data.repository

import android.graphics.Color
import com.example.marketmind.domain.model.*
import com.example.marketmind.ui.views.PerformancePoint
import com.example.marketmind.ui.views.RegimeSegment
import com.example.marketmind.ui.views.RiskPoint
import java.text.SimpleDateFormat
import java.util.*

object MockDataProvider {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun getMockRiskData(): RiskData {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val history = mutableListOf<RiskPoint>()

        // Generate 30 days of realistic historical risk points
        val baseProbabilities = floatArrayOf(
            18f, 22f, 25f, 21f, 19f, 24f, 28f, 35f, 42f, 48f,
            55f, 62f, 58f, 51f, 45f, 39f, 42f, 49f, 53f, 47f,
            41f, 36f, 32f, 30f, 35f, 38f, 34f, 31f, 33f, 34.2f
        )

        for (i in baseProbabilities.indices) {
            val timestamp = now - (baseProbabilities.size - 1 - i) * dayMs
            history.add(RiskPoint(timestamp, baseProbabilities[i]))
        }

        return RiskData(
            probability = 34.2f,
            category = "Moderate",
            explanation = "LSTM risk model detects moderate volatility. Market momentum remains resilient with selective sector rotation.",
            asOfTimestamp = timeFormat.format(Date()),
            history = history
        )
    }

    fun getMockRegimeData(): RegimeData {
        return RegimeData(
            regime = "Volatile",
            confidence = 88.5f,
            description = "HMM multi-asset model classifies market in a Volatile regime due to macro rate expectations and earnings shifts.",
            asOfTimestamp = timeFormat.format(Date())
        )
    }

    fun getMockMarketOverview(): List<MarketAsset> {
        return listOf(
            MarketAsset("nifty50", "NIFTY 50 Index", "NIFTY", 24850.40, 0.65, "up", "Indices"),
            MarketAsset("banknifty", "Bank NIFTY", "BANKNIFTY", 51200.15, -0.32, "down", "Indices"),
            MarketAsset("it_index", "NIFTY IT Index", "NIFTYIT", 38410.80, 1.42, "up", "Indices"),
            MarketAsset("reliance", "Reliance Industries", "RELIANCE", 2980.50, 1.15, "up", "Equities"),
            MarketAsset("tcs", "Tata Consultancy Services", "TCS", 4120.00, 2.05, "up", "Equities"),
            MarketAsset("hdfcbank", "HDFC Bank", "HDFCBANK", 1640.25, -0.85, "down", "Equities"),
            MarketAsset("gold_etf", "Gold ETF Benchmark", "GOLDBEES", 6240.00, 0.45, "up", "Commodities"),
            MarketAsset("silver_etf", "Silver ETF", "SILVERBEES", 88.50, 1.80, "up", "Commodities"),
            MarketAsset("bond_10y", "10Y Sovereign Bond", "IN10Y", 102.50, -0.05, "down", "Bonds"),
            MarketAsset("bond_5y", "5Y Corporate Bond", "CORPBOND", 105.80, 0.12, "up", "Bonds")
        )
    }

    fun getMockPortfolioRecommendation(): PortfolioRecommendation {
        val allocations = listOf(
            PortfolioAllocation("equity_nifty", "Large Cap Equity", 50.0f, 40.0f, -10.0f, "#3B82F6"),
            PortfolioAllocation("it_tech", "Tech & Growth", 15.0f, 20.0f, 5.0f, "#10B981"),
            PortfolioAllocation("gold", "Gold & Commodities", 10.0f, 15.0f, 5.0f, "#F59E0B"),
            PortfolioAllocation("bonds", "Government Bonds", 15.0f, 20.0f, 5.0f, "#8B5CF6"),
            PortfolioAllocation("cash", "Liquid Treasury", 10.0f, 5.0f, -5.0f, "#64748B")
        )

        return PortfolioRecommendation(
            currentAllocations = allocations,
            explanation = "Model recommends trimming Large Cap Equities by 10% and scaling into Gold ETFs (+5%) and Sovereign Bonds (+5%) to hedge against current regime volatility.",
            riskExposure = "Moderate Risk (Beta 0.82)",
            asOfTimestamp = timeFormat.format(Date())
        )
    }

    fun getMockAnalytics(period: String = "1M"): AnalyticsData {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val pointsCount = when (period) {
            "1M" -> 30
            "3M" -> 90
            "6M" -> 180
            "1Y" -> 365
            else -> 30
        }

        val performancePoints = mutableListOf<PerformancePoint>()
        var portVal = 0.0f
        var benchVal = 0.0f

        val random = Random(42) // Fixed seed for stable demo visualization

        for (i in 0 until pointsCount) {
            val timestamp = now - (pointsCount - 1 - i) * dayMs
            val portChange = (random.nextFloat() - 0.46f) * 1.5f
            val benchChange = (random.nextFloat() - 0.49f) * 1.6f
            portVal += portChange
            benchVal += benchChange
            performancePoints.add(PerformancePoint(timestamp, portVal, benchVal))
        }

        val metrics = listOf(
            RiskMetric("Sharpe Ratio", "1.85", "", "Risk-adjusted return efficiency vs risk-free rate"),
            RiskMetric("Max Drawdown", "-7.2%", "%", "Maximum peak-to-trough portfolio decline over period"),
            RiskMetric("Annualized Volatility", "12.4%", "%", "Annualized standard deviation of daily returns"),
            RiskMetric("Portfolio Beta", "0.82", "", "Sensitivity relative to NIFTY 50 index benchmark"),
            RiskMetric("Sortino Ratio", "2.38", "", "Downside risk-adjusted return ratio")
        )

        val regimeSegments = listOf(
            RegimeSegment("Calm", 45f, 0xFF10B981.toInt()),
            RegimeSegment("Volatile", 35f, 0xFFF59E0B.toInt()),
            RegimeSegment("Bearish", 20f, 0xFFEF4444.toInt())
        )

        return AnalyticsData(
            performancePoints = performancePoints,
            riskMetrics = metrics,
            regimeSegments = regimeSegments,
            insights = "Portfolio risk-adjusted performance remains strong with a Sharpe ratio of 1.85, outperforming the benchmark index by +4.8% while maintaining lower maximum drawdown.",
            asOfTimestamp = timeFormat.format(Date())
        )
    }
}