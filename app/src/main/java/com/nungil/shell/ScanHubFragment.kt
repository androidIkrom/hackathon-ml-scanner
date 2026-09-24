package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.contract.Dest
import com.nungil.contract.ScanMode
import com.nungil.contract.app.services
import com.nungil.databinding.HubFragmentBinding
import com.nungil.design.setHeading

/** Owner I. Scan menu: full scan, live scan, walk mode and history. */
class ScanHubFragment : Fragment() {
    private var _binding: HubFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HubFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.hubHeadline.setHeading()
        val nav = services().navigator
        binding.hubFullScan.setOnClickListener { nav.open(Dest.Scan(ScanMode.FULL)) }
        binding.hubLive.setOnClickListener { nav.open(Dest.Scan(ScanMode.LIVE)) }
        binding.hubWalk.setOnClickListener { nav.open(Dest.Walk) }
        binding.hubHistory.setOnClickListener { nav.open(Dest.History) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
