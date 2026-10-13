package com.nungil.contract

/** A Settings switch changed by voice ("model accurate", "colours off"). */
sealed interface SettingChange {
    data class Model(val choice: ModelChoice) : SettingChange
    data class Compute(val compute: com.nungil.contract.Compute) : SettingChange
    data class Speech(val on: Boolean) : SettingChange
    data class Colors(val on: Boolean) : SettingChange
    data class HighContrast(val on: Boolean) : SettingChange
    data class VoiceGuide(val on: Boolean) : SettingChange
}
