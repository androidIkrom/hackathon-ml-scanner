package com.nungil.people

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nungil.R
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.people.ConfirmWindow
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.PersonEntity
import com.nungil.databinding.PersonFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.search.SearchCameraFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved person: photo, name, find, rename (voice or keyboard) and delete (confirmed). */
class PersonFragment : Fragment(), VoiceHandler {
    private var _binding: PersonFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<PersonFragmentArgs>()
    private lateinit var services: AppServices
    private var person: PersonEntity? = null
    private val confirm = ConfirmWindow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PersonFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.personName, true)
        binding.personFind.setOnClickListener { find() }
        binding.personRename.setOnClickListener { rename() }
        binding.personDelete.setOnClickListener { askDelete() }
        val dao = AppDatabase.get(requireContext()).people()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getPerson(args.personId) }
            if (loaded == null) {
                services.speaker.say(getString(R.string.person_not_found))
                services.navigator.back()
                return@launch
            }
            person = loaded
            binding.personName.text = loaded.name
            binding.personPhoto.contentDescription = getString(R.string.person_photo, loaded.name)
            val photo = withContext(Dispatchers.IO) { PhotoFiles.load(loaded.photoPath) }
            _binding?.personPhoto?.setImageBitmap(photo)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Delete -> {
            if (confirm.press(SystemClock.elapsedRealtime())) delete() else services.speaker.say(getString(R.string.person_delete_again))
            true
        }
        VoiceCommand.Start -> {
            find()
            true
        }
        else -> false
    }

    private fun find() {
        val p = person ?: return
        val args = SearchCameraFragmentArgs(
            targetType = TargetType.PERSON.name,
            targetId = p.id,
            targetLabel = "person",
            spokenName = p.name,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private fun rename() {
        val p = person ?: return
        val field = TextInputEditText(requireContext()).apply { setText(p.name) }
        val layout = TextInputLayout(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.ng_gutter)
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.person_rename_title)
            .setView(layout)
            .setPositiveButton(R.string.person_ok) { _, _ -> applyName(field.text?.toString().orEmpty()) }
            .setNeutralButton(R.string.person_rename_speak) { _, _ ->
                services.speaker.say(getString(R.string.person_add_ask))
                services.askForWords(viewLifecycleOwner) { applyName(it) }
            }
            .setNegativeButton(R.string.person_cancel, null)
            .create()
        dialog.show()
    }

    private fun applyName(raw: String) {
        val p = person ?: return
        val name = raw.trim()
        if (name.isEmpty() || name == p.name) return
        val dao = AppDatabase.get(requireContext()).people()
        AppScope.launch { dao.rename(p.id, name) }
        person = p.copy(name = name)
        _binding?.personName?.text = name
        services.speaker.say(getString(R.string.person_renamed, name))
    }

    private fun askDelete() {
        val p = person ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.person_delete_title, p.name))
            .setMessage(R.string.person_delete_body)
            .setPositiveButton(R.string.person_delete) { _, _ -> delete() }
            .setNegativeButton(R.string.person_cancel, null)
            .show()
    }

    private fun delete() {
        val p = person ?: return
        val dao = AppDatabase.get(requireContext()).people()
        AppScope.launch {
            dao.deletePerson(p.id)
            PhotoFiles.delete(p.photoPath)
        }
        services.speaker.say(getString(R.string.person_deleted))
        services.navigator.back()
    }
}
