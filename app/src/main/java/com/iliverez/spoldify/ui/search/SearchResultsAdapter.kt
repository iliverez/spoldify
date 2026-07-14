package com.iliverez.spoldify.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iliverez.spoldify.R
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.model.SearchResultItem
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.databinding.ItemAlbumBinding
import com.iliverez.spoldify.databinding.ItemArtistBinding
import com.iliverez.spoldify.databinding.ItemPlaylistBinding
import com.iliverez.spoldify.databinding.ItemTrackBinding
import com.iliverez.spoldify.util.ImageLoader

class SearchResultsAdapter(
    private val onTrackClick: (Track) -> Unit,
    private val onAlbumClick: (Album) -> Unit,
    private val onArtistClick: (Artist) -> Unit,
    private val onPlaylistClick: (Playlist) -> Unit
) : ListAdapter<SearchResultItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_TRACK = 0
        private const val TYPE_ALBUM = 1
        private const val TYPE_ARTIST = 2
        private const val TYPE_PLAYLIST = 3

        private val DiffCallback = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean {
                return when {
                    oldItem is SearchResultItem.TrackItem && newItem is SearchResultItem.TrackItem -> oldItem.track.id == newItem.track.id
                    oldItem is SearchResultItem.AlbumItem && newItem is SearchResultItem.AlbumItem -> oldItem.album.id == newItem.album.id
                    oldItem is SearchResultItem.ArtistItem && newItem is SearchResultItem.ArtistItem -> oldItem.artist.id == newItem.artist.id
                    oldItem is SearchResultItem.PlaylistItem && newItem is SearchResultItem.PlaylistItem -> oldItem.playlist.id == newItem.playlist.id
                    else -> false
                }
            }
            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchResultItem.TrackItem -> TYPE_TRACK
            is SearchResultItem.AlbumItem -> TYPE_ALBUM
            is SearchResultItem.ArtistItem -> TYPE_ARTIST
            is SearchResultItem.PlaylistItem -> TYPE_PLAYLIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TRACK -> TrackViewHolder(ItemTrackBinding.inflate(inflater, parent, false))
            TYPE_ALBUM -> AlbumViewHolder(ItemAlbumBinding.inflate(inflater, parent, false))
            TYPE_ARTIST -> ArtistViewHolder(ItemArtistBinding.inflate(inflater, parent, false))
            TYPE_PLAYLIST -> PlaylistViewHolder(ItemPlaylistBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchResultItem.TrackItem -> (holder as TrackViewHolder).bind(item.track)
            is SearchResultItem.AlbumItem -> (holder as AlbumViewHolder).bind(item.album)
            is SearchResultItem.ArtistItem -> (holder as ArtistViewHolder).bind(item.artist)
            is SearchResultItem.PlaylistItem -> (holder as PlaylistViewHolder).bind(item.playlist)
        }
    }

    inner class TrackViewHolder(private val binding: ItemTrackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(track: Track) {
            binding.tvTrackName.text = track.name
            binding.tvTrackArtist.text = track.artistName
            ImageLoader.loadArtThumbnail(itemView.context, track.artUri, binding.ivArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onTrackClick(track) }
        }
    }

    inner class AlbumViewHolder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) {
            binding.tvAlbumName.text = album.name
            binding.tvAlbumArtist.text = album.artistName
            ImageLoader.loadArtThumbnail(itemView.context, album.artUri, binding.ivAlbumArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onAlbumClick(album) }
        }
    }

    inner class ArtistViewHolder(private val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(artist: Artist) {
            binding.tvArtistName.text = artist.name
            ImageLoader.loadArtThumbnail(itemView.context, artist.artUri, binding.ivArtistArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onArtistClick(artist) }
        }
    }

    inner class PlaylistViewHolder(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            binding.tvPlaylistName.text = playlist.name
            binding.tvPlaylistOwner.text = playlist.ownerName
            ImageLoader.loadArtThumbnail(itemView.context, playlist.artUri, binding.ivPlaylistArt, R.drawable.ic_music_placeholder)
            binding.root.setOnClickListener { onPlaylistClick(playlist) }
        }
    }
}
