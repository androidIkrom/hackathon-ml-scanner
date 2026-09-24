package com.nungil.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.nungil.R
import com.nungil.core.ui.RingGeometry
import kotlin.math.min

/**
 * Owner I. A 360° ring of segments with the percent in the middle; seen slices use ?attr/ngPrimary,
 * unseen ones ?attr/ngLine, so it follows the light, dark and high-contrast themes. Main thread only.
 */
class CoverageRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bins = BooleanArray(DEFAULT_BINS)
    private var percent = 0
    private val oval = RectF()

    private val seenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.resolveColorAttr(R.attr.ngPrimary)
    }
    private val unseenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.resolveColorAttr(R.attr.ngLine)
    }
    /** A card-coloured disc behind the ring, so the ring and percent stay readable over the camera image. */
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.resolveColorAttr(R.attr.ngCard)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.resolveColorAttr(R.attr.ngText)
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_bold) }.getOrNull()
    }
    private val maxTextPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, resources.displayMetrics)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setCoverage(0, bins)
    }

    /** [percent] 0..100; [bins] true = that slice has been seen (index 0 = start heading). */
    fun setCoverage(percent: Int, bins: BooleanArray) {
        this.percent = RingGeometry.clampPercent(percent)
        this.bins = if (bins.isEmpty()) BooleanArray(DEFAULT_BINS) else bins.copyOf()
        contentDescription = resources.getString(R.string.ng_ring_description, this.percent)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = (DEFAULT_SIZE_DP * resources.displayMetrics.density).toInt()
        val size = min(resolveSize(preferred, widthMeasureSpec), resolveSize(preferred, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) return
        val stroke = size * RingGeometry.STROKE_SHARE
        seenPaint.strokeWidth = stroke
        unseenPaint.strokeWidth = stroke
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val radius = size / 2f - stroke / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawCircle(cx, cy, size / 2f, discPaint)
        for (i in bins.indices) {
            val arc = RingGeometry.segment(i, bins.size)
            canvas.drawArc(oval, arc.startDeg, arc.sweepDeg, false, if (bins[i]) seenPaint else unseenPaint)
        }
        textPaint.textSize = min(maxTextPx, size * 0.22f)
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("$percent%", cx, baseline, textPaint)
    }

    private companion object {
        const val DEFAULT_BINS = 36
        const val DEFAULT_SIZE_DP = 200
    }
}
