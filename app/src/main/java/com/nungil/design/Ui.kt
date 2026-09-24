package com.nungil.design

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

/** Marks this view as the screen's heading, so TalkBack users can jump to it. */
fun View.setHeading() = ViewCompat.setAccessibilityHeading(this, true)

/** Speaks through TalkBack only. For everyone else use services().speaker. */
fun View.announce(text: CharSequence) = announceForAccessibility(text)

/** A theme colour such as R.attr.ngPrimary, for drawing in Kotlin. */
@ColorInt
fun Context.resolveColorAttr(@AttrRes attr: Int): Int {
    val value = TypedValue()
    check(theme.resolveAttribute(attr, value, true)) { "theme has no value for attribute $attr" }
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}

/** True while TalkBack (or another touch-exploration service) is on. */
fun Context.isTalkBackOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/** Opens this app's page in system settings, where a permanently denied permission can be allowed. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
