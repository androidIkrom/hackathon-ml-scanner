package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.R
import com.nungil.contract.Compute
import com.nungil.contract.Facing
import com.nungil.contract.ModelChoice
import com.nungil.contract.ScanSettings
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.SettingsMath
import com.nungil.data.SettingsStore
import com.nungil.databinding.SettingsFragmentBinding
import com.nungil.design.setHeading

/**
 * Owner I. Scan settings are saved through SettingsStore on every change (A reads them when a camera
 * starts); app settings go to AppPrefs and the per-app language. Listeners are attached after the
 * current values are shown, so showing them never counts as a change.
 */
class SettingsFragment : Fragment() {
    private var _binding: SettingsFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: SettingsStore
    private lateinit var prefs: AppPrefs
    private var settings = ScanSettings()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsHeadline.setHeading()
        store = SettingsStore(requireContext())
        prefs = AppPrefs(requireContext())
        settings = store.load()
        show()
        listen()
    }

    private fun show() = with(binding) {
        settingsCamera.check(if (settings.facing == Facing.FRONT) R.id.settings_camera_front else R.id.settings_camera_back)
        settingsCompute.check(if (settings.compute == Compute.CPU) R.id.settings_compute_cpu else R.id.settings_compute_gpu)
        settingsModel.check(
            when (settings.model) {
                ModelChoice.LIGHT -> R.id.settings_model_light
                ModelChoice.FAST -> R.id.settings_model_fast
                ModelChoice.ACCURATE -> R.id.settings_model_accurate
            },
        )
        val slider = SettingsMath.scoreToSlider(settings.minScore)
        settingsScore.value = slider
        settingsScoreValue.text = "${slider.toInt()}%"
        settingsSpeech.isChecked = settings.speechOn
        settingsColors.isChecked = settings.colorsOn

        val main = requireActivity() as MainActivity
        settingsLanguage.check(
            when (main.languageChoice) {
                LanguageChoice.SYSTEM -> R.id.settings_language_system
                LanguageChoice.ENGLISH -> R.id.settings_language_english
                LanguageChoice.KOREAN -> R.id.settings_language_korean
            },
        )
        settingsHighContrast.isChecked = main.highContrast
        settingsVoiceGuide.isChecked = prefs.voiceGuideOn
        settingsClockDirections.isChecked = prefs.clockDirections
        settingsLearner.isChecked = main.learnerOn
    }

    private fun listen() = with(binding) {
        settingsCamera.addOnButtonCheckedListener { _, id, checked ->
            if (checked) update { copy(facing = if (id == R.id.settings_camera_front) Facing.FRONT else Facing.BACK) }
        }
        settingsCompute.addOnButtonCheckedListener { _, id, checked ->
            if (checked) update { copy(compute = if (id == R.id.settings_compute_cpu) Compute.CPU else Compute.GPU) }
        }
        settingsModel.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val model = when (id) {
                R.id.settings_model_light -> ModelChoice.LIGHT
                R.id.settings_model_fast -> ModelChoice.FAST
                else -> ModelChoice.ACCURATE
            }
            update { copy(model = model) }
        }
        settingsScore.addOnChangeListener { _, value, fromUser ->
            settingsScoreValue.text = "${value.toInt()}%"
            if (fromUser) update { copy(minScore = SettingsMath.sliderToScore(value)) }
        }
        settingsSpeech.setOnCheckedChangeListener { _, on -> update { copy(speechOn = on) } }
        settingsColors.setOnCheckedChangeListener { _, on -> update { copy(colorsOn = on) } }

        val main = requireActivity() as MainActivity
        settingsLanguage.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            main.setLanguage(
                when (id) {
                    R.id.settings_language_english -> LanguageChoice.ENGLISH
                    R.id.settings_language_korean -> LanguageChoice.KOREAN
                    else -> LanguageChoice.SYSTEM
                },
            )
        }
        settingsHighContrast.setOnCheckedChangeListener { _, on -> main.setHighContrast(on) }
        settingsVoiceGuide.setOnCheckedChangeListener { _, on -> prefs.voiceGuideOn = on }
        settingsClockDirections.setOnCheckedChangeListener { _, on -> prefs.clockDirections = on }
        settingsLearner.setOnCheckedChangeListener { _, on -> main.setLearner(on) }
    }

    private fun update(change: ScanSettings.() -> ScanSettings) {
        settings = settings.change().normalized()
        store.save(settings)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
