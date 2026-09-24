package com.nungil.weather

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.nungil.R
import com.nungil.core.weather.Clock

/**
 * The time for Home, above the greeting: "14:05" (or "2:05 PM" / "오후 2:05" on a 12-hour phone), updated every
 * minute. Tapping it speaks the time and the date. Place it in Home's layout: `<com.nungil.weather.ClockLabel …/>`.
 */
class ClockLabel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {
    private val ticker = MinuteTicker { show() }

    init {
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Nungil_Title)
        minHeight = resources.getDimensionPixelSize(R.dimen.ng_touch)
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setOnClickListener { context.appServices()?.speaker?.say(spoken()) }
        show()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.register(context)
        show()
    }

    override fun onDetachedFromWindow() {
        ticker.unregister(context)
        super.onDetachedFromWindow()
    }

    private fun spoken(): String {
        val now = Now.read()
        val lang = context.appLang()
        return Clock.timeSpoken(now.hour, now.minute, lang) + " " +
            Clock.dateSpoken(now.month, now.day, now.dayOfWeek, lang)
    }

    private fun show() {
        val now = Now.read()
        text = Clock.timeLabel(now.hour, now.minute, DateFormat.is24HourFormat(context), context.appLang())
        contentDescription = spoken()
    }
}
