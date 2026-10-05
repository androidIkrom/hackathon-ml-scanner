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
import com.nungil.contract.Facing
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.people.SpokenName
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
        is VoiceCommand.SwitchCamera -> {
            chooseCamera(command.to)
            true
        }
        is VoiceCommand.Unknown -> takeName(command.text)
        else -> false
    }

    /**
     * Words that are not a command name the person, also after the first answer: said again, the name is
     * replaced. It is said back, so the user knows what was heard.
     */
    private fun takeName(text: String): Boolean {
        val b = _binding ?: return false
        val name = SpokenName.of(text)
        if (name.isEmpty()) return false
        val current = b.personAddName.text?.toString()?.trim().orEmpty()
        // The app's own "Name: Ali." heard back is the name it already has: not said again.
        if (name.equals(current, ignoreCase = true)) return true
        b.personAddName.setText(name)
        b.personAddNameLayout.error = null
        services.speaker.sayNow(getString(R.string.person_add_named, name))
        return true
    }

    /** "Front camera" picks "Me", "back camera" "Someone else", "switch camera" the other one; said back. */
    private fun chooseCamera(to: Facing?) {
        val b = _binding ?: return
        val front = when (to) {
            Facing.FRONT -> true
            Facing.BACK -> false
            null -> b.personAddCamera.checkedRadioButtonId != R.id.person_add_me
        }
        b.personAddCamera.check(if (front) R.id.person_add_me else R.id.person_add_other)
        services.speaker.sayNow(getString(if (front) R.string.person_add_me else R.string.person_add_other))
    }

    private fun askName() {
        services.speaker.say(getString(R.string.person_add_ask))
        services.askForWords(viewLifecycleOwner) { text -> takeName(text) }
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
