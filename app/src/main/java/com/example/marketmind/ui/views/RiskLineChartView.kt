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

data class RiskPoint(val timestamp: Long, val probability: Float)

class RiskLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<RiskPoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#EF4444")
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val thresholdTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        textSize = 24f
        isFakeBoldText = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
    }
    private val xAxisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val markerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
        textSize = 28f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val linePath = Path()
    private val fillPath = Path()
    private val tooltipRect = RectF()
    private val points = mutableListOf<PointF>()

    private var touchX: Float? = null
    private var selectedPoint: RiskPoint? = null

    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    init {
        linePaint.color = ContextCompat.getColor(context, R.color.accent_blue)
        gridPaint.color = ContextCompat.getColor(context, R.color.stroke_dark)
        textPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        xAxisTextPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        tooltipBgPaint.color = ContextCompat.getColor(context, R.color.surface_elevated_dark)
    }

    fun setData(pts: List<RiskPoint>?) {
        dataPoints = pts ?: emptyList()
        touchX = null
        selectedPoint = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dataPoints.isEmpty()) return false
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
        if (dataPoints.isEmpty()) return
        val paddingLeft = 85f
        val paddingRight = 40f
        val chartWidth = width - paddingLeft - paddingRight

        val step = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)
        val index = ((tx - paddingLeft) / step).toInt().coerceIn(0, dataPoints.size - 1)
        selectedPoint = dataPoints[index]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingLeft = 85f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 70f

        val chartWidth = w - paddingLeft - paddingRight
        val chartHeight = h - paddingTop - paddingBottom

        // Grid lines at 0%, 25%, 50%, 75%, 100%
        val levels = listOf(0f, 25f, 50f, 75f, 100f)
        for (lvl in levels) {
            val y = paddingTop + chartHeight * (1f - lvl / 100f)
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${lvl.toInt()}%", paddingLeft - 12f, y + 8f, textPaint)
        }

        // 50% High Risk Threshold Line
        val highThresholdY = paddingTop + chartHeight * 0.5f
        canvas.drawLine(paddingLeft, highThresholdY, w - paddingRight, highThresholdY, thresholdPaint)
        canvas.drawText("50% Threshold", w - paddingRight - 10f, highThresholdY - 8f, thresholdTextPaint)

        if (dataPoints.isEmpty()) {
            val emptyText = context.getString(R.string.chart_empty_risk)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(emptyText, w / 2f, h / 2f, textPaint)
            return
        }

        points.clear()
        val step = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)

        for (i in dataPoints.indices) {
            val pt = dataPoints[i]
            val x = paddingLeft + i * step
            val y = paddingTop + chartHeight * (1f - (pt.probability.coerceIn(0f, 100f) / 100f))
            points.add(PointF(x, y))
        }

        linePath.reset()
        fillPath.reset()

        linePath.moveTo(points[0].x, points[0].y)
        fillPath.moveTo(points[0].x, h - paddingBottom)
        fillPath.lineTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val cx = (pPrev.x + pCurr.x) / 2f
            linePath.cubicTo(cx, pPrev.y, cx, pCurr.y, pCurr.x, pCurr.y)
            fillPath.cubicTo(cx, pPrev.y, cx, pCurr.y, pCurr.x, pCurr.y)
        }

        fillPath.lineTo(points.last().x, h - paddingBottom)
        fillPath.close()

        val gradient = LinearGradient(
            0f, paddingTop, 0f, h - paddingBottom,
            Color.parseColor("#333B82F6"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        // Draw X-axis timestamps (4-5 labels)
        val labelStep = (dataPoints.size / 4).coerceAtLeast(1)
        for (i in dataPoints.indices step labelStep) {
            if (i in points.indices) {
                val dateStr = dateFormat.format(Date(dataPoints[i].timestamp))
                canvas.drawText(dateStr, points[i].x, h - 16f, xAxisTextPaint)
            }
        }

        // Draw Touch Inspector & Tooltip Card
        selectedPoint?.let { pt ->
            val index = dataPoints.indexOf(pt)
            if (index in points.indices) {
                val p = points[index]
                canvas.drawLine(p.x, paddingTop, p.x, h - paddingBottom, thresholdPaint)

                markerOuterPaint.color = ContextCompat.getColor(context, R.color.accent_blue)
                canvas.drawCircle(p.x, p.y, 14f, markerPaint)
                canvas.drawCircle(p.x, p.y, 14f, markerOuterPaint)

                val dateStr = dateFormat.format(Date(pt.timestamp))
                val tooltipStr = "$dateStr: ${String.format(Locale.US, "%.1f%%", pt.probability)}"

                val tooltipW = 240f
                val tooltipH = 54f
                val tooltipX = p.x.coerceIn(paddingLeft + tooltipW / 2f, w - paddingRight - tooltipW / 2f)
                val tooltipY = (p.y - 45f).coerceAtLeast(paddingTop + tooltipH / 2f)

                tooltipRect.set(
                    tooltipX - tooltipW / 2f,
                    tooltipY - tooltipH / 2f,
                    tooltipX + tooltipW / 2f,
                    tooltipY + tooltipH / 2f
                )

                canvas.drawRoundRect(tooltipRect, 14f, 14f, tooltipBgPaint)
                canvas.drawRoundRect(tooltipRect, 14f, 14f, tooltipBorderPaint)
                canvas.drawText(tooltipStr, tooltipX, tooltipY + 9f, tooltipTextPaint)
            }
        }
    }
}