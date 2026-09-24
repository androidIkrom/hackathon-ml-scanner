package com.nungil.saved

import androidx.navigation.NavController
import com.nungil.R
import com.nungil.contract.SavedTab

/**
 * After an enrolment finishes: leave the "add" and "enrol" screens (Back must not return to them) and show the
 * Saved list on [tab].
 */
fun NavController.returnToSaved(addDestination: Int, tab: SavedTab) {
    if (!popBackStack(addDestination, true)) popBackStack()
    if (currentDestination?.id != R.id.saved) {
        navigate(R.id.saved, SavedFragmentArgs(tab.ordinal).toBundle())
    }
}
