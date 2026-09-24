package com.nungil.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.core.scan.OverlayMath

/**
 * Draws boxes over a PreviewView that uses the default FILL_CENTER scale type.
 * Call [show] and [clear] on the main thread.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Style { NORMAL, TARGET, DIM }

    /** [text] null draws the box without a label. */
    data class Mark(val box: Box, val text: String?, val style: Style = Style.NORMAL)

    private var marks: List<Mark> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var mirrored = false

    private val density = resources.displayMetrics.density
    private val corner = 12f * density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val pill = RectF()

    init {
        // Boxes are decoration for sighted helpers; everything they show is also spoken.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** [imageWidth] x [imageHeight] is the upright analysed image; [mirrored] for the front camera. */
    fun show(marks: List<Mark>, imageWidth: Int, imageHeight: Int, mirrored: Boolean) {
        this.marks = marks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.mirrored = mirrored
        invalidate()
    }

    fun clear() {
        marks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth <= 0 || imageHeight <= 0 || width == 0 || height == 0) return
        val primary = MaterialColors.getColor(this, R.attr.ngPrimary)
        val onPrimary = MaterialColors.getColor(this, R.attr.ngOnPrimary)
        val focus = MaterialColors.getColor(this, R.attr.ngFocus)
        val line = MaterialColors.getColor(this, R.attr.ngLine)
        for (mark in marks) {
            val r = OverlayMath.toView(mark.box, imageWidth, imageHeight, width, height, mirrored)
            rect.set(r.left, r.top, r.right, r.bottom)
            val color = when (mark.style) {
                Style.NORMAL -> primary
                Style.TARGET -> focus
                Style.DIM -> line
            }
            if (mark.style == Style.TARGET) {
                fillPaint.color = Color.argb(TARGET_FILL_ALPHA, Color.red(focus), Color.green(focus), Color.blue(focus))
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
            }
            strokePaint.color = color
            strokePaint.strokeWidth = (if (mark.style == Style.TARGET) 6f else 3f) * density
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
            val text = mark.text ?: continue
            val padding = 8f * density
            val textWidth = textPaint.measureText(text)
            val pillHeight = textPaint.textSize + padding * 2
            val top = (rect.top - pillHeight).coerceAtLeast(0f)
            pill.set(rect.left, top, rect.left + textWidth + padding * 2, top + pillHeight)
            pillPaint.color = color
            canvas.drawRoundRect(pill, pillHeight / 2, pillHeight / 2, pillPaint)
            textPaint.color = if (mark.style == Style.DIM) primary else onPrimary
            canvas.drawText(text, pill.left + padding, pill.bottom - padding - textPaint.descent() / 2, textPaint)
        }
    }

    private companion object {
        const val TARGET_FILL_ALPHA = 0x40
    }
}
