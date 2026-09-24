package com.nungil.data

import android.content.Context
import com.nungil.contract.ScanSettings

/** Remembers the last scan settings in SharedPreferences "scan_settings". */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)

    fun load(): ScanSettings {
        val d = ScanSettings()
        return ScanSettings(
            facing = enumOr(prefs.getString(KEY_FACING, null), d.facing),
            compute = enumOr(prefs.getString(KEY_COMPUTE, null), d.compute),
            model = enumOr(prefs.getString(KEY_MODEL, null), d.model),
            minScore = prefs.getFloat(KEY_MIN_SCORE, d.minScore),
            speechOn = prefs.getBoolean(KEY_SPEECH, d.speechOn),
            colorsOn = prefs.getBoolean(KEY_COLORS, d.colorsOn),
        ).normalized()
    }

    fun save(settings: ScanSettings) {
        val s = settings.normalized()
        prefs.edit()
            .putString(KEY_FACING, s.facing.name)
            .putString(KEY_COMPUTE, s.compute.name)
            .putString(KEY_MODEL, s.model.name)
            .putFloat(KEY_MIN_SCORE, s.minScore)
            .putBoolean(KEY_SPEECH, s.speechOn)
            .putBoolean(KEY_COLORS, s.colorsOn)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val KEY_FACING = "facing"
        const val KEY_COMPUTE = "compute"
        const val KEY_MODEL = "model"
        const val KEY_MIN_SCORE = "min_score"
        const val KEY_SPEECH = "speech_on"
        const val KEY_COLORS = "colors_on"
    }
}
