package com.nungil.items

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
import com.nungil.core.lang.LabelNames
import com.nungil.core.people.ConfirmWindow
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.ItemEntity
import com.nungil.databinding.ItemFragmentBinding
import com.nungil.design.announce
import com.nungil.design.isTalkBackOn
import com.nungil.saved.PhotoFiles
import com.nungil.search.SearchCameraFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved car or object: photo, name, find, rename (voice or keyboard) and delete (confirmed). */
class ItemFragment : Fragment(), VoiceHandler {
    private var _binding: ItemFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ItemFragmentArgs>()
    private lateinit var services: AppServices
    private var item: ItemEntity? = null
    private val confirm = ConfirmWindow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.itemName, true)
        binding.itemFind.setOnClickListener { find() }
        binding.itemRename.setOnClickListener { rename() }
        binding.itemDelete.setOnClickListener { askDelete() }
        val dao = AppDatabase.get(requireContext()).items()
        val lang = services.lang
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getItem(args.itemId) }
            if (loaded == null) {
                services.speaker.say(getString(R.string.item_not_found))
                services.navigator.back()
                return@launch
            }
            item = loaded
            binding.itemName.text = loaded.name
            binding.itemLabel.text = LabelNames.name(loaded.label, lang)
            binding.itemPhoto.contentDescription = getString(R.string.item_photo, loaded.name)
            val photo = withContext(Dispatchers.IO) { PhotoFiles.load(loaded.photoPath) }
            _binding?.itemPhoto?.setImageBitmap(photo)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Delete -> {
            if (confirm.press(SystemClock.elapsedRealtime())) delete() else services.speaker.say(getString(R.string.item_delete_again))
            true
        }
        VoiceCommand.Start -> {
            find()
            true
        }
        else -> false
    }

    private fun find() {
        val i = item ?: return
        val args = SearchCameraFragmentArgs(
            targetType = TargetType.ITEM.name,
            targetId = i.id,
            targetLabel = i.label,
            spokenName = i.name,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private fun rename() {
        val i = item ?: return
        val field = TextInputEditText(requireContext()).apply { setText(i.name) }
        val layout = TextInputLayout(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.ng_gutter)
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.item_rename_title)
            .setView(layout)
            .setPositiveButton(R.string.item_ok) { _, _ -> applyName(field.text?.toString().orEmpty()) }
            .setNeutralButton(R.string.item_rename_speak) { _, _ ->
                services.speaker.say(getString(R.string.item_add_ask))
                services.askForWords(viewLifecycleOwner) { applyName(it) }
            }
            .setNegativeButton(R.string.item_cancel, null)
            .show()
    }

    private fun applyName(raw: String) {
        val i = item ?: return
        val name = raw.trim()
        if (name.isEmpty() || name == i.name) return
        val dao = AppDatabase.get(requireContext()).items()
        AppScope.launch { dao.rename(i.id, name) }
        item = i.copy(name = name)
        _binding?.itemName?.text = name
        services.speaker.say(getString(R.string.item_renamed, name))
    }

    /** The Delete button, like the voice command, deletes only when pressed twice within ConfirmWindow's 5 s. */
    private fun askDelete() {
        if (item == null) return
        if (confirm.press(SystemClock.elapsedRealtime())) {
            delete()
            return
        }
        val button = binding.itemDelete
        button.setText(R.string.item_delete_confirm)
        button.postDelayed({ _binding?.itemDelete?.setText(R.string.item_delete) }, ConfirmWindow.WINDOW_MS)
        // With TalkBack on, TalkBack already speaks; our own voice on top would say it twice.
        val again = getString(R.string.item_delete_tap_again)
        if (requireContext().isTalkBackOn()) button.announce(again) else services.speaker.say(again)
    }

    private fun delete() {
        val i = item ?: return
        val dao = AppDatabase.get(requireContext()).items()
        AppScope.launch {
            dao.deleteItem(i.id)
            PhotoFiles.delete(i.photoPath)
        }
        services.speaker.say(getString(R.string.item_deleted))
        services.navigator.back()
    }
}
