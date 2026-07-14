package com.iliverez.spoldify.ui.common.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iliverez.spoldify.R
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.databinding.ItemTrackBinding
import com.iliverez.spoldify.util.ImageLoader

class TrackListAdapter(
    private val onTrackClick: (Track) -> Unit
) : ListAdapter<Track, TrackListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTrackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(track: Track) {
            Log.d("TrackListAdapter", "bind: name='${track.name}' artist='${track.artistName}' id=${track.id}")
            binding.tvTrackName.text = if (track.name.isBlank()) "Unknown" else track.name
            binding.tvTrackArtist.text = track.artistName
            ImageLoader.loadArtThumbnail(itemView.context, track.artUri, binding.ivArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onTrackClick(track) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(oldItem: Track, newItem: Track) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Track, newItem: Track) = oldItem == newItem
    }
}
