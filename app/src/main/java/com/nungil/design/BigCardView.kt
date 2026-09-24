package com.nungil.design

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.core.content.withStyledAttributes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.card.MaterialCardView
import com.nungil.R
import com.nungil.databinding.NgBigCardBinding

/**
 * Owner I. A large tappable card: icon, title, subtitle and a chevron. TalkBack reads it as one button
 * ("Look around. Turn once and hear what is around you. Button").
 *
 * XML: app:ngIcon, app:ngTitle, app:ngSubtitle, app:ngEmphasis (tinted background for the main card).
 */
class BigCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding = NgBigCardBinding.inflate(LayoutInflater.from(context), this)

    var title: CharSequence
        get() = binding.ngBigCardTitle.text
        set(value) {
            binding.ngBigCardTitle.text = value
            describe()
        }

    var subtitle: CharSequence
        get() = binding.ngBigCardSubtitle.text
        set(value) {
            binding.ngBigCardSubtitle.text = value
            binding.ngBigCardSubtitle.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            describe()
        }

    init {
        isClickable = true
        isFocusable = true
        minimumHeight = resources.getDimensionPixelSize(R.dimen.ng_touch)
        context.withStyledAttributes(attrs, R.styleable.BigCardView) {
            setIcon(getDrawable(R.styleable.BigCardView_ngIcon))
            title = getString(R.styleable.BigCardView_ngTitle).orEmpty()
            subtitle = getString(R.styleable.BigCardView_ngSubtitle).orEmpty()
            if (getBoolean(R.styleable.BigCardView_ngEmphasis, false)) {
                setCardBackgroundColor(context.resolveColorAttr(R.attr.ngPrimarySoft))
            }
        }
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    fun setIcon(icon: Drawable?) {
        binding.ngBigCardIcon.setImageDrawable(icon)
        binding.ngBigCardIcon.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    private fun describe() {
        contentDescription = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(". ")
    }
}
