package com.iliverez.spoldify.ui.common.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iliverez.spoldify.R
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.databinding.ItemAlbumBinding
import com.iliverez.spoldify.util.ImageLoader

class AlbumGridAdapter(
    private val onAlbumClick: (Album) -> Unit
) : ListAdapter<Album, AlbumGridAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) {
            binding.tvAlbumName.text = album.name
            binding.tvAlbumArtist.text = album.artistName
            ImageLoader.loadArtThumbnail(itemView.context, album.artUri, binding.ivAlbumArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onAlbumClick(album) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
    }
}
