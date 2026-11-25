package com.example.marketmind.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.marketmind.R
import java.text.SimpleDateFormat
import java.util.*

data class PerformancePoint(val timestamp: Long, val portfolioReturn: Float, val benchmarkReturn: Float)

class PerformanceChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<PerformancePoint> = emptyList()

    private val portfolioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val benchmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
    }
    private val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#3B82F6")
    }
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val pathPortfolio = Path()
    private val pathFill = Path()
    private val pathBenchmark = Path()
    private val tooltipRect = RectF()

    private var touchX: Float? = null
    private var selectedPoint: PerformancePoint? = null

    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    init {
        portfolioPaint.color = ContextCompat.getColor(context, R.color.accent_blue)
        benchmarkPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        gridPaint.color = ContextCompat.getColor(context, R.color.stroke_dark)
        textPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        legendTextPaint.color = ContextCompat.getColor(context, R.color.text_primary_dark)
        tooltipBgPaint.color = ContextCompat.getColor(context, R.color.surface_elevated_dark)
    }

    fun setData(data: List<PerformancePoint>?) {
        points = data ?: emptyList()
        touchX = null
        selectedPoint = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                calculateSelectedPoint()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchX = null
                selectedPoint = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun calculateSelectedPoint() {
        val tx = touchX ?: return
        if (points.isEmpty()) return
        val paddingLeft = 90f
        val paddingRight = 40f
        val chartWidth = width - paddingLeft - paddingRight

        val step = chartWidth / (points.size - 1).coerceAtLeast(1)
        val index = ((tx - paddingLeft) / step).toInt().coerceIn(0, points.size - 1)
        selectedPoint = points[index]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingLeft = 90f
        val paddingRight = 40f
        val paddingTop = 65f
        val paddingBottom = 65f

        val chartWidth = w - paddingLeft - paddingRight
        val chartHeight = h - paddingTop - paddingBottom

        // Draw Top Chart Legend
        val legendY = 32f
        legendDotPaint.color = ContextCompat.getColor(context, R.color.accent_blue)
        canvas.drawCircle(paddingLeft + 10f, legendY - 6f, 7f, legendDotPaint)
        legendTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Portfolio", paddingLeft + 24f, legendY, legendTextPaint)

        legendDotPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        canvas.drawCircle(paddingLeft + 160f, legendY - 6f, 7f, legendDotPaint)
        canvas.drawText("NIFTY 50 Benchmark", paddingLeft + 174f, legendY, legendTextPaint)

        if (points.isEmpty()) {
            val emptyMsg = context.getString(R.string.analytics_empty)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(emptyMsg, w / 2f, h / 2f, textPaint)
            return
        }

        val maxVal = points.flatMap { listOf(it.portfolioReturn, it.benchmarkReturn) }.maxOrNull()?.coerceAtLeast(5f) ?: 5f
        val minVal = points.flatMap { listOf(it.portfolioReturn, it.benchmarkReturn) }.minOrNull()?.coerceAtMost(-5f) ?: -5f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        // Draw Y-Axis Horizontal Grid Lines and Percentages
        val levelsCount = 4
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..levelsCount) {
            val valAtLevel = minVal + (maxVal - minVal) * (i / levelsCount.toFloat())
            val y = paddingTop + chartHeight * (1f - (valAtLevel - minVal) / range)
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
            val valStr = String.format(Locale.US, "%+.1f%%", valAtLevel)
            canvas.drawText(valStr, paddingLeft - 12f, y + 8f, textPaint)
        }

        pathPortfolio.reset()
        pathFill.reset()
        pathBenchmark.reset()

        val step = chartWidth / (points.size - 1).coerceAtLeast(1)
        val ptCoords = mutableListOf<PointF>()
        val benchCoords = mutableListOf<PointF>()

        for (i in points.indices) {
            val pt = points[i]
            val x = paddingLeft + i * step
            val yPort = paddingTop + chartHeight * (1f - (pt.portfolioReturn - minVal) / range)
            val yBench = paddingTop + chartHeight * (1f - (pt.benchmarkReturn - minVal) / range)
            ptCoords.add(PointF(x, yPort))
            benchCoords.add(PointF(x, yBench))
        }

        pathPortfolio.moveTo(ptCoords[0].x, ptCoords[0].y)
        pathFill.moveTo(ptCoords[0].x, h - paddingBottom)
        pathFill.lineTo(ptCoords[0].x, ptCoords[0].y)
        pathBenchmark.moveTo(benchCoords[0].x, benchCoords[0].y)

        for (i in 1 until points.size) {
            val pPrev = ptCoords[i - 1]
            val pCurr = ptCoords[i]
            val cx = (pPrev.x + pCurr.x) / 2f
            pathPortfolio.cubicTo(cx, pPrev.y, cx, pCurr.y, pCurr.x, pCurr.y)
            pathFill.cubicTo(cx, pPrev.y, cx, pCurr.y, pCurr.x, pCurr.y)

            val bPrev = benchCoords[i - 1]
            val bCurr = benchCoords[i]
            val bcx = (bPrev.x + bCurr.x) / 2f
            pathBenchmark.cubicTo(bcx, bPrev.y, bcx, bCurr.y, bCurr.x, bCurr.y)
        }

        pathFill.lineTo(ptCoords.last().x, h - paddingBottom)
        pathFill.close()

        val fillGradient = LinearGradient(
            0f, paddingTop, 0f, h - paddingBottom,
            Color.parseColor("#333B82F6"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        fillPaint.shader = fillGradient

        canvas.drawPath(pathFill, fillPaint)
        canvas.drawPath(pathBenchmark, benchmarkPaint)
        canvas.drawPath(pathPortfolio, portfolioPaint)

        // Draw X-Axis Date Labels
        val labelStep = (points.size / 4).coerceAtLeast(1)
        textPaint.textAlign = Paint.Align.CENTER
        for (i in points.indices step labelStep) {
            val dateStr = dateFormat.format(Date(points[i].timestamp))
            canvas.drawText(dateStr, ptCoords[i].x, h - 16f, textPaint)
        }

        // Draw Touch Inspector & Floating Tooltip
        selectedPoint?.let { pt ->
            val index = points.indexOf(pt)
            if (index in ptCoords.indices) {
                val p = ptCoords[index]
                canvas.drawLine(p.x, paddingTop, p.x, h - paddingBottom, gridPaint)
                canvas.drawCircle(p.x, p.y, 10f, markerPaint)

                val alpha = pt.portfolioReturn - pt.benchmarkReturn
                val dateStr = dateFormat.format(Date(pt.timestamp))
                val tooltipStr = "$dateStr | Port: ${String.format(Locale.US, "%+.1f%%", pt.portfolioReturn)} (Alpha: ${String.format(Locale.US, "%+.1f%%", alpha)})"

                val tooltipW = 340f
                val tooltipH = 50f
                val tooltipX = p.x.coerceIn(paddingLeft + tooltipW / 2f, w - paddingRight - tooltipW / 2f)
                val tooltipY = (p.y - 45f).coerceAtLeast(paddingTop + tooltipH / 2f)

                tooltipRect.set(
                    tooltipX - tooltipW / 2f,
                    tooltipY - tooltipH / 2f,
                    tooltipX + tooltipW / 2f,
                    tooltipY + tooltipH / 2f
                )

                canvas.drawRoundRect(tooltipRect, 12f, 12f, tooltipBgPaint)
                canvas.drawRoundRect(tooltipRect, 12f, 12f, tooltipBorderPaint)
                canvas.drawText(tooltipStr, tooltipX, tooltipY + 8f, tooltipTextPaint)
            }
        }
    }
}