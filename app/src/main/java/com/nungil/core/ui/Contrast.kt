package com.nungil.core.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast maths for opaque sRGB colours given as 0xRRGGBB. */
object Contrast {
    /** Normal text (and icons that carry meaning) needs 4.5:1. */
    const val TEXT_MIN = 4.5

    /** Large shapes such as a button against the page need 3:1. */
    const val NON_TEXT_MIN = 3.0

    /** "#RGB", "#RRGGBB" or an opaque "#FFRRGGBB" to 0xRRGGBB. Translucent colours are rejected. */
    fun parseHex(hex: String): Int {
        val h = hex.trim().removePrefix("#")
        return when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("").toInt(16)
            6 -> h.toInt(16)
            8 -> {
                require(h.substring(0, 2).equals("FF", ignoreCase = true)) { "translucent colour $hex" }
                h.substring(2).toInt(16)
            }
            else -> throw IllegalArgumentException("not a colour: $hex")
        }
    }

    fun luminance(rgb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(rgb shr 16 and 0xFF) +
            0.7152 * channel(rgb shr 8 and 0xFF) +
            0.0722 * channel(rgb and 0xFF)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
