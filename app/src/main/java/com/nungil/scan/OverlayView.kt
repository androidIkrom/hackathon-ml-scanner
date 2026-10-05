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

    /**
     * [LABEL]: the name only, at the middle of [Mark.box]. For a saved item found by its look the box is the square
     * searched, not the thing: drawn as a box it sat beside the thing or around much more (2026-10-05).
     */
    enum class Style { NORMAL, TARGET, DIM, LABEL }

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
                Style.NORMAL, Style.LABEL -> primary
                Style.TARGET -> focus
                Style.DIM -> line
            }
            if (mark.style == Style.LABEL) {
                val text = mark.text ?: continue
                val width = textPaint.measureText(text) + pillPadding() * 2
                drawPill(canvas, text, rect.centerX() - width / 2f, rect.centerY() - pillHeight() / 2f, color, onPrimary)
                continue
            }
            if (mark.style == Style.TARGET) {
                fillPaint.color = Color.argb(TARGET_FILL_ALPHA, Color.red(focus), Color.green(focus), Color.blue(focus))
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
            }
            strokePaint.color = color
            strokePaint.strokeWidth = (if (mark.style == Style.TARGET) 6f else 3f) * density
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
            val text = mark.text ?: continue
            val top = (rect.top - pillHeight()).coerceAtLeast(0f)
            drawPill(canvas, text, rect.left, top, color, if (mark.style == Style.DIM) primary else onPrimary)
        }
    }

    private fun pillPadding() = 8f * density

    private fun pillHeight() = textPaint.textSize + pillPadding() * 2

    /** A label in a rounded pill whose top left corner is at ([left], [top]). */
    private fun drawPill(canvas: Canvas, text: String, left: Float, top: Float, color: Int, textColor: Int) {
        val padding = pillPadding()
        val height = pillHeight()
        pill.set(left, top, left + textPaint.measureText(text) + padding * 2, top + height)
        pillPaint.color = color
        canvas.drawRoundRect(pill, height / 2, height / 2, pillPaint)
        textPaint.color = textColor
        canvas.drawText(text, pill.left + padding, pill.bottom - padding - textPaint.descent() / 2, textPaint)
    }

    private companion object {
        const val TARGET_FILL_ALPHA = 0x40
    }
}
