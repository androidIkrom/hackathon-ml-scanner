package com.nungil.core.ui

import com.nungil.contract.Compute
import com.nungil.contract.Lang
import com.nungil.contract.ModelChoice
import com.nungil.contract.SettingChange

/** What the app says back after a setting changed by voice. */
object SettingPhrases {
    /** [nextTime]: a camera screen is open; the model and the processor are taken when a scan starts. */
    fun done(change: SettingChange, lang: Lang, nextTime: Boolean = false): String {
        val ko = lang == Lang.KO
        val text = when (change) {
            is SettingChange.Model -> if (ko) "모델: ${modelKo(change.choice)}." else "Model: ${change.choice.name.lowercase()}."
            is SettingChange.Compute -> if (ko) "프로세서: ${computeName(change.compute)}." else "Processor: ${computeName(change.compute)}."
            is SettingChange.Speech -> onOff(change.on, ko, "Speaking results", "결과 말하기")
            is SettingChange.Colors -> onOff(change.on, ko, "Colours", "색 말하기")
            is SettingChange.HighContrast -> onOff(change.on, ko, "High contrast", "고대비")
            is SettingChange.VoiceGuide -> onOff(change.on, ko, "Reading what you touch", "터치하면 읽어주기")
        }
        if (!nextTime) return text
        return text + if (ko) " 다음 스캔부터 적용돼요." else " From the next scan."
    }

    private fun onOff(on: Boolean, ko: Boolean, en: String, kor: String) =
        if (ko) "$kor ${if (on) "켰어요" else "껐어요"}." else "$en ${if (on) "on" else "off"}."

    private fun modelKo(m: ModelChoice) = when (m) {
        ModelChoice.LIGHT -> "가벼움"
        ModelChoice.FAST -> "빠름"
        ModelChoice.ACCURATE -> "정확"
    }

    private fun computeName(c: Compute) = c.name
}
