package com.nungil.walk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.nungil.contract.app.services
import com.nungil.core.scan.ScanPhrases
import com.nungil.databinding.WalkFragmentBinding

/** Walk mode placeholder: says it is not ready. Walk mode gets its own plan only after Gate 4 (team plan). */
class WalkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        WalkFragmentBinding.inflate(inflater, container, false).also {
            ViewCompat.setAccessibilityHeading(it.walkTitle, true)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val services = services()
        services.speaker.sayNow(ScanPhrases.walkNotReady(services.lang))
    }
}
