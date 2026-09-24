package com.nungil.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.PersonAddFragmentBinding

/** Name a new person (voice or keyboard), pick the camera, then go to the five-pose enrolment. */
class AddPersonFragment : Fragment(), VoiceHandler {
    private var _binding: PersonAddFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddPersonFragmentArgs>()
    private lateinit var services: AppServices
    private var arrived = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PersonAddFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.personAddTitle, true)
        binding.personAddNameLayout.setEndIconOnClickListener { askName() }
        binding.personAddStart.setOnClickListener { start() }
        // Only on first arrival: coming Back from enrolment restores the typed name instead.
        if (!arrived) {
            arrived = true
            val given = args.name
            if (!given.isNullOrBlank()) binding.personAddName.setText(given) else askName()
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
        services.speaker.say(getString(R.string.person_add_ask))
        services.askForWords(viewLifecycleOwner) { text ->
            _binding?.personAddName?.setText(text.trim())
        }
    }

    private fun start() {
        val name = binding.personAddName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.personAddNameLayout.error = getString(R.string.person_add_need_name)
            services.speaker.say(getString(R.string.person_add_need_name))
            askName()
            return
        }
        binding.personAddNameLayout.error = null
        val front = binding.personAddCamera.checkedRadioButtonId == R.id.person_add_me
        findNavController().navigate(R.id.enroll, EnrollFragmentArgs(name, front).toBundle())
    }
}
