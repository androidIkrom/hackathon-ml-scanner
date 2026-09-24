package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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
import kotlinx.coroutines.flow.combine
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
        binding.homeAppTitle.setHeading()
        // The clock sits right under the header: text at the top of its 64 dp touch area, not centred in it.
        binding.homeClock.gravity = android.view.Gravity.START or android.view.Gravity.TOP
        val nav = services().navigator
        binding.homeFullScan.setOnClickListener { nav.open(Dest.Scan(ScanMode.FULL)) }
        binding.homeSearch.setOnClickListener { nav.open(Dest.Search()) }
        binding.homeSaved.setOnClickListener { nav.open(Dest.Saved()) }
        binding.homeLive.setOnClickListener { nav.open(Dest.Scan(ScanMode.LIVE)) }
        binding.homeWalk.setOnClickListener { nav.open(Dest.Walk) }
        binding.homeGo.setOnClickListener { (requireActivity() as MainActivity).openGoMode() }
        binding.homeHistory.setOnClickListener { nav.open(Dest.History) }
        binding.homeSettings.setOnClickListener { nav.open(Dest.Settings) }

        val main = requireActivity() as MainActivity
        // Tap: stop every sound and listen. Long press: turn always-on voice off (or on again).
        binding.homeMic.setOnClickListener { main.talkNow() }
        binding.homeMic.setOnLongClickListener {
            main.setVoiceOn(!main.voiceOn.value)
            true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(main.voiceOn, main.awake) { on, awake -> on to awake }.collect { (on, awake) ->
                    binding.homeMic.setText(
                        when {
                            !on -> R.string.home_mic_turn_on
                            awake -> R.string.home_mic_speak
                            else -> R.string.home_mic_asleep
                        },
                    )
                    binding.homeMic.setIconResource(R.drawable.ng_ic_mic)
                    ViewCompat.replaceAccessibilityAction(
                        binding.homeMic,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                        getString(if (on) R.string.home_mic_turn_off else R.string.home_mic_turn_on),
                        null,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
