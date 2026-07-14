package com.iliverez.spoldify.data.model

data class Track(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String,
    val albumId: String,
    val artistId: String,
    val durationMs: Long,
    val artUri: String? = null,
    val isLocal: Boolean = false
)

data class Album(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String,
    val artUri: String? = null,
    val year: String? = null,
    val tracks: List<Track> = emptyList()
)

data class Artist(
    val id: String,
    val name: String,
    val artUri: String? = null,
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList()
)

data class Playlist(
    val id: String,
    val name: String,
    val ownerName: String,
    val artUri: String? = null,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

sealed class SearchResultItem {
    data class TrackItem(val track: Track) : SearchResultItem()
    data class AlbumItem(val album: Album) : SearchResultItem()
    data class ArtistItem(val artist: Artist) : SearchResultItem()
    data class PlaylistItem(val playlist: Playlist) : SearchResultItem()
}

enum class PlaybackState {
    IDLE, LOADING, PLAYING, PAUSED, BUFFERING, ERROR
}

data class PlaybackInfo(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

enum class RepeatMode { OFF, ALL, ONE }

enum class AudioQuality(val bitrate: Int) {
    LOW(96), NORMAL(160), HIGH(320)
}

sealed class HomeSection(val title: String) {
    data class RecentlyPlayed(val items: List<Track>) : HomeSection("Recently Played")
    data class JumpBackIn(val items: List<Album>) : HomeSection("Jump back in")
    data class MadeForYou(val items: List<Album>) : HomeSection("Made for You")
    data class RadioMix(val items: List<Album>) : HomeSection("Radio Mix")
    data class NewReleases(val items: List<Album>) : HomeSection("New Releases")
}

data class UserLibrary(
    val playlists: List<Playlist> = emptyList(),
    val savedAlbums: List<Album> = emptyList(),
    val followedArtists: List<Artist> = emptyList()
)
