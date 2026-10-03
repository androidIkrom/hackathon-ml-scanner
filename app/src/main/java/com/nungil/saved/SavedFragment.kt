package com.nungil.saved

import android.os.Bundle
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
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.data.AppDatabase
import com.nungil.databinding.SavedFragmentBinding
import com.nungil.items.ItemFragmentArgs
import com.nungil.people.PersonFragmentArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab.ordinal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            add()
            true
        }
        else -> false
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
        return when (tab) {
            SavedTab.PEOPLE -> db.people().observePeople().map { people ->
                people.map { SavedRow(it.id, it.name, personSubtitle, isPerson = true) }
            }
            SavedTab.CARS, SavedTab.OBJECTS -> {
                val kind = if (tab == SavedTab.CARS) ItemKind.CAR else ItemKind.OBJECT
                db.items().observeItems(kind.name).map { items ->
                    items.map { SavedRow(it.id, it.name, LabelNames.name(it.label, lang), isPerson = false) }
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
