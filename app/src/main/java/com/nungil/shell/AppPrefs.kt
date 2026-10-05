package com.nungil.shell

import android.content.Context

/** App-wide choices in SharedPreferences "app_prefs". The UI language itself is stored by AppCompat. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    /** Always-on voice commands: on unless the user turned them off, and on again whenever the app is opened. */
    var voiceOn: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ON, value).apply()

    /** Speak each screen's help when it opens. */
    var learnerOn: Boolean
        get() = prefs.getBoolean(KEY_LEARNER_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_LEARNER_ON, value).apply()

    /** Tapping a control speaks its name (only while TalkBack is off). */
    var voiceGuideOn: Boolean
        get() = prefs.getBoolean(KEY_VOICE_GUIDE_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_GUIDE_ON, value).apply()

    /** Directions as clock hours ("at 10 o'clock") instead of words, on every screen. */
    var clockDirections: Boolean
        get() = prefs.getBoolean(KEY_CLOCK_DIRECTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK_DIRECTIONS, value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** A sentence to speak after the activity is recreated (for example after a language change). */
    fun setPendingAnnouncement(text: String) = prefs.edit().putString(KEY_PENDING, text).apply()

    fun takePendingAnnouncement(): String? {
        val text = prefs.getString(KEY_PENDING, null) ?: return null
        prefs.edit().remove(KEY_PENDING).apply()
        return text
    }

    private companion object {
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_VOICE_ON = "voice_on"
        const val KEY_LEARNER_ON = "learner_on"
        const val KEY_VOICE_GUIDE_ON = "voice_guide_on"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_CLOCK_DIRECTIONS = "clock_directions"
        const val KEY_PENDING = "pending_announcement"
    }
}
