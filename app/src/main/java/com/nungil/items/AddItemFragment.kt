package com.nungil.items

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.ItemKind
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.items.ItemNameAnswer
import com.nungil.core.items.ItemNames
import com.nungil.data.AppDatabase
import com.nungil.databinding.ItemAddFragmentBinding
import com.nungil.shell.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Name a new car or object (voice or keyboard), then go to the item enrolment.
 *
 * A name that was said is read back and learning starts only after a yes: "My green chair" was heard as "My
 * green tear", saved under that name, and could not be found by its own (the logs). "No" offers the recognizer's
 * next guess, then asks for the name again. Other words are not taken for a new name there: in a room where
 * people talk they would be someone else's. A name that is already saved is said to be so: "replace" learns it
 * again in its place, another name (read back like the first) keeps both. A name that was typed is the user's
 * own and is not read back.
 */
class AddItemFragment : Fragment(), VoiceHandler {
    /** NAME: waiting for a name. CONFIRM: a name was read back. TAKEN: the name is saved already, replace it? */
    private enum class Step { NAME, CONFIRM, TAKEN }

    private var _binding: ItemAddFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddItemFragmentArgs>()
    private lateinit var services: AppServices
    private var arrived = false
    private var step = Step.NAME

    /** The name heard and the recognizer's other guesses for it; [offered] is the one read back. */
    private var candidates: List<String> = emptyList()
    private var offered = 0

    /** The name the user was told is already saved. */
    private var takenName: String? = null

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
        binding.itemAddOther.setOnClickListener { askName() }
        // Only on first arrival: coming Back from enrolment restores the typed name instead.
        if (!arrived) {
            arrived = true
            val given = args.name
            if (!given.isNullOrBlank()) offer(listOf(given.trim())) else askName()
        } else {
            step = Step.NAME
            show(null)
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

    /** Asks for the name: the first time, or [again] after a "no" to every guess. */
    private fun askName(again: Boolean = false) {
        step = Step.NAME
        show(null)
        services.speaker.say(getString(if (again) R.string.item_add_again else R.string.item_add_ask))
        services.askForWords(viewLifecycleOwner) { text -> offer(guessesFor(text)) }
    }

    /** What was heard, then the recognizer's other guesses for it. */
    private fun guessesFor(text: String): List<String> =
        ItemNames.candidates(text, (activity as? MainActivity)?.heardGuesses().orEmpty())

    /** Reads the first of [names] back; asks again when there is none. */
    private fun offer(names: List<String>) {
        if (_binding == null) return
        if (names.isEmpty()) {
            askName(again = true)
            return
        }
        candidates = names
        offered = 0
        confirm()
    }

    /** "My green chair. Is that right?" */
    private fun confirm() {
        val name = candidates[offered]
        step = Step.CONFIRM
        binding.itemAddName.setText(name)
        binding.itemAddNameLayout.error = null
        val prompt = getString(R.string.item_add_confirm, name)
        Log.i(TAG, "Item name read back: \"$name\" (${offered + 1} of ${candidates.size})")
        show(prompt)
        services.speaker.say(prompt)
        listen()
    }

    /** Waits for yes, no, replace, or another name. */
    private fun listen() {
        (activity as? MainActivity)?.let {
            it.dictationAccepts = { text -> ItemNames.answer(text) != ItemNameAnswer.OTHER }
            it.dictationEarly = true
        }
        services.askForWords(viewLifecycleOwner) { text -> answer(text) }
    }

    private fun answer(text: String) {
        if (_binding == null) return
        when (ItemNames.answer(text)) {
            // "Yes" to "it is already saved" is not a yes to replacing it: that takes the word itself.
            ItemNameAnswer.YES -> if (step == Step.TAKEN) taken(takenName.orEmpty()) else accepted()
            ItemNameAnswer.REPLACE -> if (step == Step.TAKEN) go() else listen()
            ItemNameAnswer.NO -> when {
                step == Step.CONFIRM && offered + 1 < candidates.size -> {
                    offered++
                    confirm()
                }
                else -> askName(again = true)
            }
            // Another name for a thing whose name is taken. To "Is that right?" only yes or no is an answer.
            ItemNameAnswer.OTHER -> if (step == Step.TAKEN) offer(guessesFor(text)) else listen()
        }
    }

    /** The name is the one the user wants: learn it, unless an item is saved under it already. */
    private fun accepted() {
        val name = name()
        if (name.isEmpty()) {
            needName()
            return
        }
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { AppDatabase.get(context).items().allItems().map { it.name } }
            if (_binding == null) return@launch
            if (ItemNames.taken(name, saved)) taken(name) else go()
        }
    }

    /** "My bag is already saved. Say replace to learn it again, or say another name." */
    private fun taken(name: String) {
        step = Step.TAKEN
        takenName = name
        val prompt = getString(R.string.item_add_taken, name)
        Log.i(TAG, "Item name already saved: \"$name\"")
        show(prompt)
        services.speaker.say(prompt)
        listen()
    }

    /** The button, or "Start": the name in the field is the user's own. */
    private fun start() {
        val name = name()
        if (name.isEmpty()) {
            needName()
            return
        }
        (activity as? MainActivity)?.dropWords()
        // "Replace and start" for the name that was said to be taken; any other name is checked first.
        if (step == Step.TAKEN && ItemNames.same(name, takenName.orEmpty())) go() else accepted()
    }

    private fun needName() {
        binding.itemAddNameLayout.error = getString(R.string.item_add_need_name)
        services.speaker.say(getString(R.string.item_add_need_name))
        askName()
    }

    private fun name(): String = binding.itemAddName.text?.toString()?.trim().orEmpty()

    /** To the enrolment. Learning a name that is saved already replaces it there (ItemEnrollFragment.finish). */
    private fun go() {
        val name = name()
        if (_binding == null || name.isEmpty()) return
        // Start said and tapped, or one "start" heard twice: both checked the name and came here (the logs).
        if (findNavController().currentDestination?.id != R.id.add_item) return
        (activity as? MainActivity)?.dropWords()
        binding.itemAddNameLayout.error = null
        findNavController().navigate(R.id.item_enroll, ItemEnrollFragmentArgs(name, args.kind).toBundle())
    }

    /** The question on the screen and the buttons that answer it. */
    private fun show(prompt: String?) {
        val b = _binding ?: return
        b.itemAddPrompt.text = prompt.orEmpty()
        b.itemAddPrompt.isVisible = prompt != null
        b.itemAddOther.isVisible = step != Step.NAME
        b.itemAddStart.setText(
            when (step) {
                Step.NAME -> R.string.item_add_start
                Step.CONFIRM -> R.string.item_add_yes
                Step.TAKEN -> R.string.item_add_replace
            },
        )
    }

    private companion object {
        const val TAG = "Nungil"
    }
}
