package com.nungil.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.nungil.R
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.core.search.SavedName
import com.nungil.core.search.SearchPhrases
import com.nungil.core.search.SearchResolver
import com.nungil.databinding.SearchFragmentBinding
import kotlinx.coroutines.launch

/**
 * "What should I find?" The query comes from the nav argument, the voice (askForWords), the keyboard or a
 * suggestion chip, and is resolved to a saved person or item first, then to a COCO label.
 */
class SearchFragment : Fragment(), VoiceHandler {
    private var _binding: SearchFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SearchFragmentArgs>()
    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private var saved: List<SavedName> = emptyList()

    /** The nav-argument query is used once; coming Back from the camera must not jump forward again. */
    private var queryConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queryConsumed = savedInstanceState?.getBoolean(KEY_CONSUMED) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.searchTitle, true)
        binding.searchFind.setOnClickListener { resolveAndGo(binding.searchInput.text?.toString().orEmpty()) }
        binding.searchInputLayout.setEndIconOnClickListener { listen() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                resolveAndGo(binding.searchInput.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            saved = SavedNames.load(requireContext().applicationContext)
            if (_binding == null) return@launch
            showChips()
            val query = args.query
            if (!queryConsumed && !query.isNullOrBlank()) {
                queryConsumed = true
                binding.searchInput.setText(query)
                resolveAndGo(query)
            } else {
                listen()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CONSUMED, queryConsumed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            resolveAndGo(binding.searchInput.text?.toString().orEmpty())
            true
        }
        else -> false
    }

    /** Ask, then treat the next words as the query. */
    private fun listen() {
        services.speaker.say(SearchPhrases.askWhat(lang))
        services.askForWords(viewLifecycleOwner) { text ->
            if (_binding == null) return@askForWords
            binding.searchInput.setText(text)
            resolveAndGo(text)
        }
    }

    private fun showChips() {
        val group = binding.searchChips
        group.removeAllViews()
        val names = saved.map { it.name } + COMMON_LABELS.map { LabelNames.name(it, lang) }
        for (name in names.distinct()) {
            group.addView(Chip(requireContext()).apply {
                text = name
                setTextAppearance(R.style.TextAppearance_Nungil_Label)
                chipMinHeight = resources.getDimension(R.dimen.ng_touch) - CHIP_TOUCH_INSET_PX
                setEnsureMinTouchTargetSize(true)
                setOnClickListener {
                    binding.searchInput.setText(name)
                    resolveAndGo(name)
                }
            })
        }
    }

    private fun resolveAndGo(text: String) {
        val target = SearchResolver.resolve(text, saved, lang)
        if (target == null) {
            binding.searchInputLayout.error = getString(R.string.search_not_understood)
            services.speaker.say(SearchPhrases.unknown(lang))
            return
        }
        binding.searchInputLayout.error = null
        val args = SearchCameraFragmentArgs(
            targetType = target.type.name,
            targetId = target.id,
            targetLabel = target.label,
            spokenName = target.spokenName,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private companion object {
        const val KEY_CONSUMED = "query_consumed"
        const val CHIP_TOUCH_INSET_PX = 8f

        /** Things people ask for most, shown as chips after the saved names. */
        val COMMON_LABELS = listOf("backpack", "cell phone", "cup", "bottle", "chair", "laptop")
    }
}
