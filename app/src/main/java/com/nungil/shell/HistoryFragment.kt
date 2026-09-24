package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.ui.HistoryText
import com.nungil.data.AppDatabase
import com.nungil.data.ScanEntity
import com.nungil.databinding.HistoryDeleteSheetBinding
import com.nungil.databinding.HistoryFragmentBinding
import com.nungil.databinding.HistoryItemBinding
import com.nungil.design.setHeading
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Owner I. Past scans from Room, newest first. Tap = hear it again; touch and hold (or say "delete"
 * after tapping one) = delete it after a confirmation sheet. "Start" replays the newest scan.
 */
class HistoryFragment : Fragment(), VoiceHandler {
    private var _binding: HistoryFragmentBinding? = null
    private val binding get() = _binding!!
    private val adapter = ScanAdapter(onTap = ::replay, onHold = ::confirmDelete)
    private var selected: ScanEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HistoryFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.historyHeadline.setHeading()
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        val dao = AppDatabase.get(requireContext()).scans()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeScans().collect { scans ->
                    adapter.submitList(scans)
                    binding.historyEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        val lang = services().lang
        when (command) {
            VoiceCommand.Start -> {
                val newest = adapter.currentList.firstOrNull()
                if (newest == null) services().speaker.sayNow(HistoryText.empty(lang)) else replay(newest)
            }
            VoiceCommand.Delete -> {
                val scan = selected
                if (scan == null) services().speaker.sayNow(HistoryText.pickFirst(lang)) else confirmDelete(scan)
            }
            else -> return false
        }
        return true
    }

    private fun replay(scan: ScanEntity) {
        selected = scan
        services().speaker.sayNow(scan.summaryText)
    }

    private fun confirmDelete(scan: ScanEntity) {
        selected = scan
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = HistoryDeleteSheetBinding.inflate(layoutInflater)
        sheetBinding.historyDeleteTitle.setHeading()
        sheetBinding.historyDeleteConfirm.setOnClickListener {
            val dao = AppDatabase.get(requireContext()).scans()
            val speaker = services().speaker
            val text = HistoryText.deleted(services().lang)
            AppScope.launch { dao.deleteScan(scan.id) }
            speaker.say(text)
            selected = null
            sheet.dismiss()
        }
        sheetBinding.historyDeleteCancel.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(sheetBinding.root)
        sheet.show()
    }

    override fun onDestroyView() {
        binding.historyList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private class ScanAdapter(
        private val onTap: (ScanEntity) -> Unit,
        private val onHold: (ScanEntity) -> Unit,
    ) : ListAdapter<ScanEntity, ScanAdapter.Holder>(Diff) {

        class Holder(val binding: HistoryItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(HistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val scan = getItem(position)
            val context = holder.itemView.context
            val lang = (context as? MainActivity)?.lang ?: Lang.EN
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.startedAt))
            val meta = "$date · ${HistoryText.line(scan.mode, scan.coveragePercent, lang)}"
            holder.binding.historyItemMeta.text = meta
            holder.binding.historyItemSummary.text = scan.summaryText
            holder.itemView.contentDescription = "$meta. ${scan.summaryText}"
            holder.itemView.setOnClickListener { onTap(scan) }
            holder.itemView.setOnLongClickListener {
                onHold(scan)
                true
            }
        }

        private object Diff : DiffUtil.ItemCallback<ScanEntity>() {
            override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem == newItem
        }
    }
}
