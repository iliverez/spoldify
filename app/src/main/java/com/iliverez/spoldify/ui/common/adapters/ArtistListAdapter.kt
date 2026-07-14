package com.iliverez.spoldify.ui.common.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.databinding.ItemArtistBinding

class ArtistListAdapter(
    private val onArtistClick: (Artist) -> Unit
) : ListAdapter<Artist, ArtistListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(artist: Artist) {
            binding.tvArtistName.text = artist.name
            binding.root.setOnClickListener { onArtistClick(artist) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Artist, newItem: Artist) = oldItem == newItem
    }
}
