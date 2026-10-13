package com.nungil.data

import android.content.Context
import com.nungil.contract.ModelChoice
import com.nungil.contract.ScanSettings

/** Which saved model moves to YOLO26 the one time it becomes the default: Accurate (the old default) or none saved. */
object SettingsDefaults {
    fun movesToYolo(savedModel: String?): Boolean = savedModel == null || savedModel == ModelChoice.ACCURATE.name
}

/** Remembers the last scan settings in SharedPreferences "scan_settings". */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)

    fun load(): ScanSettings {
        movedToYolo()
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

    /**
     * Calls [onChange] with the new score each time the saved score changes, until the returned function is called.
     * The listener is held here: SharedPreferences keeps only a weak reference.
     */
    fun onMinScore(onChange: (Float) -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MIN_SCORE) onChange(load().minScore)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Once: a model saved as Accurate, the default until YOLO26 (2026-10-13), becomes YOLO26, as the user asked. A
     * model chosen after that stays as chosen.
     */
    private fun movedToYolo() {
        if (prefs.getBoolean(KEY_MOVED_TO_YOLO, false)) return
        val edit = prefs.edit().putBoolean(KEY_MOVED_TO_YOLO, true)
        if (SettingsDefaults.movesToYolo(prefs.getString(KEY_MODEL, null))) edit.putString(KEY_MODEL, ModelChoice.YOLO.name)
        edit.apply()
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
        const val KEY_MOVED_TO_YOLO = "moved_to_yolo"
    }
}
