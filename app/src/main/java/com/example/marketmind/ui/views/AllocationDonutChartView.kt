package com.example.marketmind.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.marketmind.R
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

data class AllocationSlice(val name: String, val percentage: Float, val color: Int)

class AllocationDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var slices: List<AllocationSlice> = emptyList()
    private var chartTitle: String = ""
    private var selectedIndex: Int = -1

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val textSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()
    private val selectedRectF = RectF()

    init {
        val holeColor = ContextCompat.getColor(context, R.color.surface_card_dark)
        holePaint.color = holeColor
        gapPaint.color = holeColor
        textPrimaryPaint.color = ContextCompat.getColor(context, R.color.text_primary_dark)
        textSecondaryPaint.color = ContextCompat.getColor(context, R.color.text_secondary_dark)
    }

    fun setData(slicesList: List<AllocationSlice>?, title: String = "") {
        slices = slicesList ?: emptyList()
        chartTitle = title
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (slices.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val cx = width / 2f
            val cy = height / 2f
            val dx = event.x - cx
            val dy = event.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            val radius = (width.coerceAtMost(height) / 2f) * 0.88f

            if (dist in (radius * 0.58f)..radius) {
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f

                var curr = 270f
                val total = slices.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(1f)
                for (i in slices.indices) {
                    val sweep = (slices[i].percentage / total) * 360f
                    val start = curr % 360f
                    val end = (curr + sweep) % 360f

                    if (start <= end) {
                        if (angle in start..end) {
                            selectedIndex = i
                            invalidate()
                            performClick()
                            return true
                        }
                    } else {
                        if (angle >= start || angle <= end) {
                            selectedIndex = i
                            invalidate()
                            performClick()
                            return true
                        }
                    }
                    curr += sweep
                }
            } else {
                selectedIndex = -1
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = (w.coerceAtMost(h) / 2f) * 0.88f
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        if (slices.isEmpty()) {
            slicePaint.color = Color.parseColor("#253148")
            canvas.drawCircle(cx, cy, radius, slicePaint)
            canvas.drawCircle(cx, cy, radius * 0.62f, holePaint)
            textPrimaryPaint.textSize = radius * 0.18f
            canvas.drawText("No Data", cx, cy + 6f, textPrimaryPaint)
            return
        }

        var startAngle = -90f
        val total = slices.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(1f)

        for (i in slices.indices) {
            val slice = slices[i]
            val sweepAngle = (slice.percentage / total) * 360f
            slicePaint.color = slice.color

            if (i == selectedIndex) {
                val offset = 8f
                selectedRectF.set(rectF.left - offset, rectF.top - offset, rectF.right + offset, rectF.bottom + offset)
                canvas.drawArc(selectedRectF, startAngle, sweepAngle, true, slicePaint)
                canvas.drawArc(selectedRectF, startAngle, sweepAngle, true, gapPaint)
            } else {
                canvas.drawArc(rectF, startAngle, sweepAngle, true, slicePaint)
                canvas.drawArc(rectF, startAngle, sweepAngle, true, gapPaint)
            }

            startAngle += sweepAngle
        }

        // Inner Hole for Donut effect
        canvas.drawCircle(cx, cy, radius * 0.60f, holePaint)

        // Center Content
        if (selectedIndex in slices.indices) {
            val selected = slices[selectedIndex]
            textPrimaryPaint.textSize = radius * 0.20f
            textSecondaryPaint.textSize = radius * 0.16f

            canvas.drawText(selected.name, cx, cy - 6f, textPrimaryPaint)
            val pctStr = String.format(Locale.US, "%.1f%%", selected.percentage)
            canvas.drawText(pctStr, cx, cy + radius * 0.22f, textSecondaryPaint)
        } else if (chartTitle.isNotEmpty()) {
            textPrimaryPaint.textSize = radius * 0.22f
            textSecondaryPaint.textSize = radius * 0.15f

            canvas.drawText(chartTitle, cx, cy - 4f, textPrimaryPaint)
            canvas.drawText("Allocation", cx, cy + radius * 0.20f, textSecondaryPaint)
        }
    }
}