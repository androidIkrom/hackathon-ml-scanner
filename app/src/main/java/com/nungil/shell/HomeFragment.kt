package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.ScanMode
import com.nungil.contract.app.services
import com.nungil.databinding.HomeFragmentBinding
import com.nungil.design.setHeading
import kotlinx.coroutines.launch

/** Owner I. Voice-first Home: one card per job and the microphone button within thumb reach. */
class HomeFragment : Fragment() {
    private var _binding: HomeFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.homeHeadline.setHeading()
        val nav = services().navigator
        binding.homeFullScan.setOnClickListener { nav.open(Dest.Scan(ScanMode.FULL)) }
        binding.homeSearch.setOnClickListener { nav.open(Dest.Search()) }
        binding.homeSaved.setOnClickListener { nav.open(Dest.Saved()) }
        binding.homeLive.setOnClickListener { nav.open(Dest.Scan(ScanMode.LIVE)) }
        binding.homeWalk.setOnClickListener { nav.open(Dest.Walk) }
        binding.homeHistory.setOnClickListener { nav.open(Dest.History) }
        binding.homeSettings.setOnClickListener { nav.open(Dest.Settings) }

        val main = requireActivity() as MainActivity
        binding.homeMic.setOnClickListener { main.setVoiceOn(!main.voiceOn.value) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                main.voiceOn.collect { on ->
                    binding.homeMic.setText(if (on) R.string.home_mic_turn_off else R.string.home_mic_turn_on)
                    binding.homeMic.setIconResource(if (on) R.drawable.ng_ic_mic_off else R.drawable.ng_ic_mic)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
