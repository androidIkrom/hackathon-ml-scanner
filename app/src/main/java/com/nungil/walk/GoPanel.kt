package com.nungil.walk

import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.android.material.button.MaterialButton
import com.nungil.R
import com.nungil.contract.Lang
import com.nungil.core.walk.GoState
import com.nungil.core.walk.Place
import com.nungil.core.walk.RoutePhrases
import com.nungil.core.walk.WalkPhrases
import com.nungil.databinding.WalkingFragmentBinding

/**
 * Go mode's part of the walk screen (owner I): the "Where to?" search with its results, and while
 * navigating the direction card (arrow, next instruction, distances). Main thread only.
 */
class GoPanel(
    private val b: WalkingFragmentBinding,
    private val onSearch: (String) -> Unit,
    private val onMic: () -> Unit,
    private val onPick: (Place) -> Unit,
) {
    init {
        b.walkingGoQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        b.walkingGoMic.setOnClickListener { onMic() }
    }

    fun showSearch(query: String = "") {
        fill(true)
        b.walkingGoPanel.visibility = View.VISIBLE
        b.walkingGoSearch.visibility = View.VISIBLE
        b.walkingGoResults.visibility = View.VISIBLE
        b.walkingGoDirection.visibility = View.GONE
        b.walkingGoResults.removeAllViews()
        if (query.isNotBlank()) b.walkingGoQuery.setText(query)
    }

    fun showSearching() {
        b.walkingGoResults.removeAllViews()
        b.walkingGoResults.addView(label(b.root.context.getString(R.string.walking_go_searching)))
    }

    /** Candidates with their distance; tapping one starts the route. */
    fun showResults(places: List<Pair<Place, Double>>, lang: Lang) {
        b.walkingGoResults.removeAllViews()
        if (places.isEmpty()) {
            b.walkingGoResults.addView(label(b.root.context.getString(R.string.walking_go_none)))
            return
        }
        val inflater = android.view.LayoutInflater.from(b.root.context)
        for ((place, metres) in places) {
            val button = inflater.inflate(R.layout.walking_go_result, b.walkingGoResults, false) as MaterialButton
            button.text = "${place.name} · ${WalkPhrases.far(metres, lang)}"
            button.setOnClickListener { onPick(place) }
            b.walkingGoResults.addView(button)
        }
    }

    /** [arrowDeg] null when there is no compass: the arrow then stays hidden. */
    fun showDirection(state: GoState, arrowDeg: Float?, lang: Lang) {
        fill(false)
        b.walkingGoPanel.visibility = View.VISIBLE
        b.walkingGoSearch.visibility = View.GONE
        b.walkingGoResults.visibility = View.GONE
        b.walkingGoDirection.visibility = View.VISIBLE
        b.walkingGoInstruction.text = state.instruction
        b.walkingGoDistance.text = RoutePhrases.goDistance(state.toTargetM, state.remainingM, lang)
        b.walkingGoArrow.visibility = if (arrowDeg == null) View.INVISIBLE else View.VISIBLE
        if (arrowDeg != null) b.walkingGoArrow.rotation = arrowDeg
    }

    /** The typed place, for the Go button. */
    fun query(): String = b.walkingGoQuery.text?.toString()?.trim().orEmpty()

    /** Search step: the panel takes the camera's space. Navigation: it only wraps the direction card. */
    private fun fill(search: Boolean) {
        val lp = b.walkingGoPanel.layoutParams as android.widget.LinearLayout.LayoutParams
        if (search) {
            lp.height = 0
            lp.weight = 1f
        } else {
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
        }
        b.walkingGoPanel.layoutParams = lp
    }

    fun hide() {
        b.walkingGoPanel.visibility = View.GONE
    }

    private fun submit() {
        val q = b.walkingGoQuery.text?.toString()?.trim().orEmpty()
        if (q.isNotEmpty()) onSearch(q)
    }

    private fun label(text: String) = android.widget.TextView(b.root.context).apply {
        setTextAppearance(R.style.TextAppearance_Nungil_Body)
        this.text = text
    }
}
