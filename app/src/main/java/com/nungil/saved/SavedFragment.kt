package com.nungil.saved

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.lang.LabelNames
import com.nungil.core.saved.SavedByVoice
import com.nungil.core.saved.SavedVoicePhrases
import com.nungil.core.saved.VoiceDelete
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.byItem
import com.nungil.databinding.SavedFragmentBinding
import com.nungil.items.ItemFragmentArgs
import com.nungil.people.PersonFragmentArgs
import com.nungil.search.SavedNames
import com.nungil.shell.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Saved people, cars and objects in three tabs, with one "Add" button for the open tab. */
class SavedFragment : Fragment(), VoiceHandler {
    private var _binding: SavedFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SavedFragmentArgs>()
    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private val adapter = SavedAdapter { open(it) }
    private var tab = SavedTab.PEOPLE

    /** The view was shown before: coming back from adding an item keeps the tab the user was on. */
    private var shown = false
    private var collecting: Job? = null

    /** Every saved name, for "Delete <name>" and "Open <name>"; read again after a delete. */
    private var names: List<SavedName> = emptyList()
    private val voiceDelete = VoiceDelete()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SavedFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.savedTitle, true)
        binding.savedList.layoutManager = LinearLayoutManager(requireContext())
        binding.savedList.adapter = adapter
        binding.savedAdd.setOnClickListener { add() }

        val tabs = binding.savedTabs
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_people))
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_cars))
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_objects))
        val start = savedInstanceState?.getInt(KEY_TAB)
            ?: tab.ordinal.takeIf { shown }
            ?: args.tab.takeIf { it in SavedTab.entries.indices }
            ?: SavedTab.PEOPLE.ordinal
        tabs.getTabAt(start)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) = show(SavedTab.entries[t.position])
            override fun onTabUnselected(t: TabLayout.Tab) = Unit
            override fun onTabReselected(t: TabLayout.Tab) = Unit
        })
        show(SavedTab.entries[start])
        loadNames()
    }

    private fun loadNames() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch { names = SavedNames.load(context) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab.ordinal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        if (command == VoiceCommand.Delete) {
            deleteByVoice()
            return true
        }
        voiceDelete.cancel()
        return when (command) {
            VoiceCommand.Start -> {
                add()
                true
            }
            // "Open my charger test one", or the name alone: that person's or item's screen.
            is VoiceCommand.Unknown -> {
                val found = SavedByVoice.named(heard(command.text), names, lang).firstOrNull()
                if (found != null) open(SavedRow(found.id, found.name, "", isPerson = found.type == TargetType.PERSON))
                found != null
            }
            else -> false
        }
    }

    /** The phrase as heard, then the recognizer's other guesses for it. */
    private fun heard(text: String? = null): List<String> {
        val guesses = (activity as? MainActivity)?.heardGuesses().orEmpty()
        return (listOfNotNull(text) + guesses).distinct()
    }

    /** "Delete <name>" asks; "delete" again within a few seconds deletes (VoiceDelete). */
    private fun deleteByVoice() {
        val targets = SavedByVoice.named(heard(), names, lang)
        when (val result = voiceDelete.heard(targets, SystemClock.elapsedRealtime())) {
            VoiceDelete.Result.NotFound -> services.speaker.say(SavedVoicePhrases.notFound(lang))
            is VoiceDelete.Result.Ask -> services.speaker.say(SavedVoicePhrases.ask(result.targets, lang))
            is VoiceDelete.Result.Delete -> {
                val db = AppDatabase.get(requireContext())
                AppScope.launch {
                    for (t in result.targets) {
                        if (t.type == TargetType.PERSON) {
                            db.people().getPerson(t.id)?.let { p ->
                                db.people().deletePerson(p.id)
                                PhotoFiles.delete(p.photoPath)
                            }
                        } else {
                            db.items().getItem(t.id)?.let { i ->
                                db.items().deleteItem(i.id)
                                PhotoFiles.delete(i.photoPath)
                            }
                        }
                    }
                }
                val gone = result.targets.map { it.id to it.type }.toSet()
                names = names.filterNot { (it.id to it.type) in gone }
                services.speaker.say(SavedVoicePhrases.deleted(result.targets, lang))
            }
        }
    }

    private fun show(newTab: SavedTab) {
        tab = newTab
        shown = true
        binding.savedAdd.setText(
            when (newTab) {
                SavedTab.PEOPLE -> R.string.saved_add_person
                SavedTab.CARS -> R.string.saved_add_car
                SavedTab.OBJECTS -> R.string.saved_add_object
            },
        )
        binding.savedEmpty.setText(
            when (newTab) {
                SavedTab.PEOPLE -> R.string.saved_empty_people
                SavedTab.CARS -> R.string.saved_empty_cars
                SavedTab.OBJECTS -> R.string.saved_empty_objects
            },
        )
        collecting?.cancel()
        val rows = rowsFor(newTab)
        collecting = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rows.collect { list ->
                    adapter.submitList(list)
                    binding.savedEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun rowsFor(tab: SavedTab): Flow<List<SavedRow>> {
        val db = AppDatabase.get(requireContext())
        val personSubtitle = getString(R.string.saved_person_subtitle)
        val oldSubtitle = getString(R.string.saved_item_old)
        return when (tab) {
            SavedTab.PEOPLE -> db.people().observePeople().map { people ->
                people.map { SavedRow(it.id, it.name, personSubtitle, isPerson = true) }
            }
            SavedTab.CARS, SavedTab.OBJECTS -> {
                val kind = if (tab == SavedTab.CARS) ItemKind.CAR else ItemKind.OBJECT
                // An item learned the old way finds nothing any more (ItemRecognizer): it says so instead of its label.
                db.items().observeItems(kind.name).combine(db.items().observeVectorCounts()) { items, counts ->
                    val old = ItemEnrollmentGuide.oldStyle(counts.byItem())
                    items.map {
                        val subtitle = if (it.id in old) oldSubtitle else LabelNames.name(it.label, lang)
                        SavedRow(it.id, it.name, subtitle, isPerson = false)
                    }
                }
            }
        }
    }

    private fun open(row: SavedRow) {
        if (row.isPerson) {
            findNavController().navigate(R.id.person, PersonFragmentArgs(row.id).toBundle())
        } else {
            findNavController().navigate(R.id.item, ItemFragmentArgs(row.id).toBundle())
        }
    }

    private fun add() {
        services.navigator.open(
            when (tab) {
                SavedTab.PEOPLE -> Dest.AddPerson()
                SavedTab.CARS -> Dest.AddItem(ItemKind.CAR)
                SavedTab.OBJECTS -> Dest.AddItem(ItemKind.OBJECT)
            },
        )
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
