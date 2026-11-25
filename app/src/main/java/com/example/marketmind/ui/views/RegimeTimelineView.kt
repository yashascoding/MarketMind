package com.example.marketmind.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.marketmind.R
import java.util.Locale

data class RegimeSegment(val regimeName: String, val durationPercent: Float, val color: Int)

class RegimeTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var segments: List<RegimeSegment> = emptyList()

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }
    private val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        isFakeBoldText = true
    }

    private val rectF = RectF()

    init {
        textPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        legendTextPaint.color = ContextCompat.getColor(context, R.color.text_primary_dark)
    }

    fun setSegments(data: List<RegimeSegment>?) {
        segments = data ?: emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barHeight = 28f
        val top = 12f
        val bottom = top + barHeight
        val gap = 6f

        if (segments.isEmpty()) {
            segmentPaint.color = Color.parseColor("#253148")
            rectF.set(0f, top, w, bottom)
            canvas.drawRoundRect(rectF, 12f, 12f, segmentPaint)
            return
        }

        val totalGaps = (segments.size - 1) * gap
        val availableWidth = (w - totalGaps).coerceAtLeast(1f)
        val totalDuration = segments.sumOf { it.durationPercent.toDouble() }.toFloat().coerceAtLeast(1f)

        var currentX = 0f

        for (i in segments.indices) {
            val seg = segments[i]
            val segWidth = (seg.durationPercent / totalDuration) * availableWidth
            rectF.set(currentX, top, currentX + segWidth, bottom)
            segmentPaint.color = seg.color
            canvas.drawRoundRect(rectF, 10f, 10f, segmentPaint)

            currentX += segWidth + gap
        }

        // Draw Legend Labels below timeline
        val legendY = bottom + 32f
        var legendX = 0f

        for (seg in segments) {
            legendDotPaint.color = seg.color
            canvas.drawCircle(legendX + 8f, legendY - 6f, 6f, legendDotPaint)

            val labelStr = "${seg.regimeName} (${String.format(Locale.US, "%.0f%%", seg.durationPercent)})"
            canvas.drawText(labelStr, legendX + 22f, legendY, legendTextPaint)

            val itemWidth = legendTextPaint.measureText(labelStr) + 48f
            legendX += itemWidth
        }
    }
}