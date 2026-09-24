package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.contract.Dest
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.ui.OnboardingText
import com.nungil.databinding.OnboardingFragmentBinding
import com.nungil.design.setHeading

/** Owner I. Twenty-second spoken introduction on first launch; "Start" (button or voice) goes Home. */
class OnboardingFragment : Fragment(), VoiceHandler {
    private var _binding: OnboardingFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = OnboardingFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.onboardingHeadline.setHeading()
        if (savedInstanceState == null) services().speaker.sayNow(OnboardingText.intro(services().lang))
        binding.onboardingStart.setOnClickListener { finish() }
        binding.onboardingVoice.setOnClickListener { (requireActivity() as MainActivity).setVoiceOn(true) }
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        if (command != VoiceCommand.Start) return false
        finish()
        return true
    }

    private fun finish() {
        services().speaker.stop()
        services().navigator.open(Dest.Home)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
