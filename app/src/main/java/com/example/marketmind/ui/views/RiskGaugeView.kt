package com.example.marketmind.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.marketmind.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class RiskGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var riskProbability: Float = 0f
    private var animatedProbability: Float = 0f
    private var isDataLoaded: Boolean = false

    private val arcBounds = RectF()
    private val badgeRect = RectF()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var lowColor: Int = Color.parseColor("#10B981")
    private var moderateColor: Int = Color.parseColor("#F59E0B")
    private var highColor: Int = Color.parseColor("#EF4444")
    private var criticalColor: Int = Color.parseColor("#8B5CF6")
    private var trackColor: Int = Color.parseColor("#1E293B")
    private var textPrimaryColor: Int = Color.parseColor("#F9FAFB")
    private var textSecondaryColor: Int = Color.parseColor("#9CA3AF")

    private var animator: ValueAnimator? = null

    init {
        lowColor = ContextCompat.getColor(context, R.color.risk_low)
        moderateColor = ContextCompat.getColor(context, R.color.risk_moderate)
        highColor = ContextCompat.getColor(context, R.color.risk_high)
        criticalColor = ContextCompat.getColor(context, R.color.risk_critical)
        trackColor = ContextCompat.getColor(context, R.color.stroke_dark)
        textPrimaryColor = ContextCompat.getColor(context, R.color.text_primary_dark)
        textSecondaryColor = ContextCompat.getColor(context, R.color.text_secondary_dark)
    }

    fun setRiskProbability(probability: Float?, animate: Boolean = true) {
        if (probability == null) {
            isDataLoaded = false
            invalidate()
            return
        }
        val target = probability.coerceIn(0f, 100f)
        isDataLoaded = true
        riskProbability = target

        animator?.cancel()
        if (animate) {
            animator = ValueAnimator.ofFloat(animatedProbability, target).apply {
                duration = 900
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    animatedProbability = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animatedProbability = target
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val strokeWidth = w * 0.085f
        val padding = strokeWidth / 2f + 16f
        arcBounds.set(padding, padding, w - padding, h * 1.55f - padding)

        val startAngle = 180f
        val sweepAngle = 180f

        // Draw Background Track Arc
        strokePaint.color = trackColor
        strokePaint.strokeWidth = strokeWidth
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, strokePaint)

        // Draw subtle tick marks at 0%, 25%, 50%, 75%, 100%
        val cx = w / 2f
        val cy = (arcBounds.top + arcBounds.bottom) / 2f
        val radius = (arcBounds.width() / 2f)
        tickPaint.color = textSecondaryColor

        for (i in 0..4) {
            val pct = i * 25f
            val angleRad = Math.toRadians((180.0 + (pct / 100f) * 180.0))
            val x1 = (cx + (radius - strokeWidth / 2f - 4f) * cos(angleRad)).toFloat()
            val y1 = (cy + (radius - strokeWidth / 2f - 4f) * sin(angleRad)).toFloat()
            val x2 = (cx + (radius + strokeWidth / 2f + 4f) * cos(angleRad)).toFloat()
            val y2 = (cy + (radius + strokeWidth / 2f + 4f) * sin(angleRad)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        if (!isDataLoaded) {
            textPaint.color = textSecondaryColor
            textPaint.textSize = w * 0.075f
            canvas.drawText(context.getString(R.string.risk_no_data), cx, h * 0.55f, textPaint)
            return
        }

        // Active Color according to risk category
        val currentColor = when {
            animatedProbability < 25f -> lowColor
            animatedProbability < 50f -> moderateColor
            animatedProbability < 75f -> highColor
            else -> criticalColor
        }

        // Draw Filled Gauge Arc
        strokePaint.color = currentColor
        val currentSweep = (animatedProbability / 100f) * sweepAngle
        if (currentSweep > 0.5f) {
            canvas.drawArc(arcBounds, startAngle, currentSweep, false, strokePaint)
        }

        // Draw Glowing Pointer Tip Circle at the tip of active arc
        val pointerAngleRad = Math.toRadians((180.0 + (animatedProbability / 100f) * 180.0))
        val px = (cx + radius * cos(pointerAngleRad)).toFloat()
        val py = (cy + radius * sin(pointerAngleRad)).toFloat()

        pointerPaint.color = Color.WHITE
        canvas.drawCircle(px, py, strokeWidth * 0.45f, pointerPaint)
        pointerPaint.color = currentColor
        canvas.drawCircle(px, py, strokeWidth * 0.30f, pointerPaint)

        // Draw Numeric Percentage
        textPaint.color = textPrimaryColor
        textPaint.textSize = w * 0.16f
        val valueText = String.format(Locale.US, "%.1f%%", animatedProbability)
        canvas.drawText(valueText, cx, h * 0.52f, textPaint)

        // Draw Category Badge Pill
        val categoryLabel = when {
            animatedProbability < 25f -> context.getString(R.string.risk_level_low)
            animatedProbability < 50f -> context.getString(R.string.risk_level_moderate)
            animatedProbability < 75f -> context.getString(R.string.risk_level_high)
            else -> context.getString(R.string.risk_level_critical)
        }

        labelPaint.color = currentColor
        labelPaint.textSize = w * 0.065f

        val textWidth = labelPaint.measureText(categoryLabel)
        val badgePaddingH = 24f
        val badgeHeight = w * 0.09f
        val badgeTop = h * 0.62f
        badgeRect.set(
            cx - textWidth / 2f - badgePaddingH,
            badgeTop,
            cx + textWidth / 2f + badgePaddingH,
            badgeTop + badgeHeight
        )

        badgeBgPaint.color = adjustAlpha(currentColor, 0.18f)
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBgPaint)

        canvas.drawText(categoryLabel, cx, badgeTop + badgeHeight * 0.72f, labelPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}