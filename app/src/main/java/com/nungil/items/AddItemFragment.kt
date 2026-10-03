package com.nungil.items

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.ItemKind
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.ItemAddFragmentBinding

/**
 * Name a new car or object (voice or keyboard), then go to the three-step item enrolment. A name that was said
 * goes there at once: "Start" after it was one more thing to say, and to be misheard (the logs).
 */
class AddItemFragment : Fragment(), VoiceHandler {
    private var _binding: ItemAddFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddItemFragmentArgs>()
    private lateinit var services: AppServices
    private var arrived = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemAddFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        binding.itemAddTitle.setText(
            if (args.kind == ItemKind.CAR) R.string.item_add_title_car else R.string.item_add_title_object,
        )
        ViewCompat.setAccessibilityHeading(binding.itemAddTitle, true)
        binding.itemAddNameLayout.setEndIconOnClickListener { askName() }
        binding.itemAddStart.setOnClickListener { start() }
        // Only on first arrival: coming Back from enrolment restores the typed name instead.
        if (!arrived) {
            arrived = true
            val given = args.name
            if (!given.isNullOrBlank()) {
                binding.itemAddName.setText(given)
                view.post { if (_binding != null) start() }
            } else {
                askName()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        else -> false
    }

    private fun askName() {
        services.speaker.say(getString(R.string.item_add_ask))
        services.askForWords(viewLifecycleOwner) { text ->
            if (_binding == null) return@askForWords
            binding.itemAddName.setText(text.trim())
            start()
        }
    }

    private fun start() {
        val name = binding.itemAddName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.itemAddNameLayout.error = getString(R.string.item_add_need_name)
            services.speaker.say(getString(R.string.item_add_need_name))
            askName()
            return
        }
        binding.itemAddNameLayout.error = null
        findNavController().navigate(R.id.item_enroll, ItemEnrollFragmentArgs(name, args.kind).toBundle())
    }
}
