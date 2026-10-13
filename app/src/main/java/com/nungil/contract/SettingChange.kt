package com.nungil.contract

/** A Settings switch changed by voice ("model accurate", "colours off"). */
sealed interface SettingChange {
    data class Model(val choice: ModelChoice) : SettingChange
    data class Compute(val compute: com.nungil.contract.Compute) : SettingChange
    data class Speech(val on: Boolean) : SettingChange
    data class Colors(val on: Boolean) : SettingChange
    data class HighContrast(val on: Boolean) : SettingChange
    data class VoiceGuide(val on: Boolean) : SettingChange

    /**
     * How sure the detector must be before a thing is said: set [to] a percent ("sureness 50"), or moved [by] some
     * ("more sure" +10, "less sure" -10). The slider "How sure before speaking", 30-70 %.
     */
    data class Sureness(val to: Int? = null, val by: Int = 0) : SettingChange
}
