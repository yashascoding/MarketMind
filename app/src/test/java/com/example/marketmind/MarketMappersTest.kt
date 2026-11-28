package com.example.marketmind

import com.example.marketmind.data.dto.*
import com.example.marketmind.data.repository.MockDataProvider
import com.example.marketmind.domain.mapper.MarketMappers
import org.junit.Assert.*
import org.junit.Test

class MarketMappersTest {

    @Test
    fun testRiskMapping_validDto_returnsMappedRiskData() {
        val dto = RiskDto(
            riskProbability = 45.5f,
            riskThreshold = 42f,
            riskLevel = "Moderate",
            explanation = "Sample risk prediction",
            timestamp = "14:30:00",
            history = listOf(RiskHistoryItemDto(1700000000L, 40f))
        )

        val result = MarketMappers.mapRisk(dto)

        assertNotNull(result)
        assertEquals(45.5f, result!!.probability, 0.01f)
        assertEquals("Moderate", result.category)
        assertEquals("Sample risk prediction", result.explanation)
        assertEquals(42f, result.threshold, 0.01f)
        assertEquals(1, result.history.size)
    }

    @Test
    fun testRiskMapping_nullDto_returnsNull() {
        val result = MarketMappers.mapRisk(null)
        assertNull(result)
    }

    @Test
    fun analyticsMapping_withoutInsight_doesNotInventOne() {
        val result = MarketMappers.mapAnalytics(AnalyticsDto())

        assertNull(result.insights)
        assertTrue(result.regimeSegments.isEmpty())
        assertTrue(result.riskMetrics.isEmpty())
    }

    @Test
    fun analyticsMapping_usesBackendRegimesAndTestMetrics() {
        val result = MarketMappers.mapAnalytics(
            AnalyticsDto(
                regimeSegments = listOf(RegimeSegmentDto("Bearish", 20f)),
                modelPerformance = mapOf(
                    "LSTM" to ModelEvaluationDto(
                        test = ModelMetricsDto(accuracy = 0.71, brierScore = 0.14)
                    )
                )
            )
        )

        assertEquals(1, result.regimeSegments.size)
        assertEquals("Bearish", result.regimeSegments.single().regimeName)
        assertEquals(2, result.riskMetrics.size)
        assertEquals("LSTM test Accuracy", result.riskMetrics.first().name)
        assertEquals("0.140", result.riskMetrics.last().value)
    }

    @Test
    fun testMockDataProvider_returnsValidData() {
        val risk = MockDataProvider.getMockRiskData()
        assertNotNull(risk)
        assertTrue(risk.probability > 0f)
        assertTrue(risk.history.isNotEmpty())

        val regime = MockDataProvider.getMockRegimeData()
        assertNotNull(regime)
        assertEquals("Volatile", regime.regime)

        val assets = MockDataProvider.getMockMarketOverview()
        assertTrue(assets.isNotEmpty())

        val portfolio = MockDataProvider.getMockPortfolioRecommendation()
        assertNotNull(portfolio)
        assertTrue(portfolio.currentAllocations.isNotEmpty())

        val analytics = MockDataProvider.getMockAnalytics("1M")
        assertNotNull(analytics)
        assertTrue(analytics.performancePoints.isNotEmpty())
        assertTrue(analytics.regimeSegments.isNotEmpty())
    }
}