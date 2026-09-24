package com.nungil.saved

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nungil.R
import com.nungil.databinding.SavedRowBinding

/** One saved person or item in the list. */
data class SavedRow(val id: Long, val title: String, val subtitle: String, val isPerson: Boolean)

class SavedAdapter(private val onClick: (SavedRow) -> Unit) : ListAdapter<SavedRow, SavedAdapter.Holder>(Diff) {

    class Holder(val binding: SavedRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(SavedRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.binding.savedRowTitle.text = row.title
        holder.binding.savedRowSubtitle.text = row.subtitle
        holder.binding.root.contentDescription =
            holder.itemView.context.getString(R.string.saved_row_description, row.title, row.subtitle)
        holder.binding.root.setOnClickListener { onClick(row) }
    }

    private object Diff : DiffUtil.ItemCallback<SavedRow>() {
        override fun areItemsTheSame(a: SavedRow, b: SavedRow) = a.id == b.id && a.isPerson == b.isPerson
        override fun areContentsTheSame(a: SavedRow, b: SavedRow) = a == b
    }
}
