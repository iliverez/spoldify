package com.iliverez.spoldify.data.repository

import android.util.Log
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.api.SpotifyApi
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.data.model.UserLibrary
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import xyz.gianlu.librespot.common.Utils
import xyz.gianlu.librespot.metadata.AlbumId
import xyz.gianlu.librespot.metadata.ArtistId
import xyz.gianlu.librespot.metadata.PlaylistId
import xyz.gianlu.librespot.metadata.TrackId

class BrowseRepository {

    private val TAG = "BrowseRepository"
    private val webApi = SpotifyApi.fromApp()

    private fun getSession() = SpoldifyApp.instance.authRepository.session
        ?: throw IllegalStateException("Not logged in")

    suspend fun getAlbum(albumId: String): Result<Album> {
        return try {
            val session = getSession()
            val id = AlbumId.fromBase62(albumId)
            val meta = session.api().getMetadata4Album(id)

            val primaryArtist = if (meta.artistCount > 0) meta.getArtist(0) else null
            val year = if (meta.hasDate()) meta.date.year.toString() else null
            val coverHex = extractCoverHex(meta.coverGroup?.imageList)

            val tracks = try {
                val json = webApi.getAlbumTracks(albumId)
                val items = json.getAsJsonArray("items") ?: emptyList()
                items.mapNotNull { element ->
                    val item = element.asJsonObject
                    val artist = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject
                    Track(
                        id = item.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                        name = item.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null,
                        artistName = artist?.getAsJsonPrimitive("name")?.asString ?: primaryArtist?.name ?: "",
                        albumName = meta.name,
                        albumId = albumId,
                        artistId = artist?.getAsJsonPrimitive("id")?.asString ?: "",
                        durationMs = item.getAsJsonPrimitive("duration_ms")?.asLong ?: 0L,
                        artUri = coverHex?.let { "https://i.scdn.co/image/$it" }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Web API album tracks failed, using Mercury: ${e.message}")
                meta.discList.flatMap { disc ->
                    disc.trackList.map { track ->
                        val trackArtists = if (track.artistCount > 0) track.getArtist(0) else null
                        Track(
                            id = encodeToBase62(track.gid.toByteArray()),
                            name = track.name,
                            artistName = trackArtists?.name ?: primaryArtist?.name ?: "",
                            albumName = meta.name,
                            albumId = albumId,
                            artistId = encodeToBase62(trackArtists?.gid?.toByteArray() ?: byteArrayOf()),
                            durationMs = track.duration.toLong(),
                            artUri = coverHex?.let { "https://i.scdn.co/image/$it" }
                        )
                    }
                }
            }

            Result.success(Album(
                id = albumId,
                name = meta.name,
                artistName = primaryArtist?.name ?: "",
                artistId = encodeToBase62(primaryArtist?.gid?.toByteArray() ?: byteArrayOf()),
                artUri = coverHex?.let { "https://i.scdn.co/image/$it" },
                year = year,
                tracks = tracks
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load album: $albumId", e)
            Result.failure(e)
        }
    }

    suspend fun getArtist(artistId: String): Result<Artist> {
        return try {
            val session = getSession()
            val id = ArtistId.fromBase62(artistId)
            val meta = session.api().getMetadata4Artist(id)

            val portraitHex = extractCoverHex(meta.portraitGroup?.imageList)

            val topTracks = if (meta.topTrackCount > 0) {
                val trackIds = meta.getTopTrack(0).trackList.map { encodeToBase62(it.gid.toByteArray()) }
                try {
                    val json = webApi.getTracks(trackIds)
                    json.getAsJsonArray("tracks")?.mapNotNull { element ->
                        val item = element.asJsonObject
                        if (item.entrySet().isEmpty()) return@mapNotNull null
                        val albumObj = item.getAsJsonObject("album")
                        val artUri = albumObj?.getAsJsonArray("images")
                            ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                        Track(
                            id = item.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                            name = item.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null,
                            artistName = meta.name,
                            albumName = albumObj?.getAsJsonPrimitive("name")?.asString ?: "",
                            albumId = albumObj?.getAsJsonPrimitive("id")?.asString ?: "",
                            artistId = artistId,
                            durationMs = item.getAsJsonPrimitive("duration_ms")?.asLong ?: 0L,
                            artUri = artUri ?: portraitHex?.let { "https://i.scdn.co/image/$it" }
                        )
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "Web API tracks batch failed, using Mercury: ${e.message}")
                    meta.getTopTrack(0).trackList.mapNotNull { track ->
                        try {
                            val trackId = TrackId.fromBase62(encodeToBase62(track.gid.toByteArray()))
                            val trackMeta = session.api().getMetadata4Track(trackId)
                            val artist = if (trackMeta.artistCount > 0) trackMeta.getArtist(0) else null
                            val album = if (trackMeta.hasAlbum()) trackMeta.album else null
                            val coverHex = extractCoverHex(album?.coverGroup?.imageList)
                            Track(
                                id = encodeToBase62(track.gid.toByteArray()),
                                name = trackMeta.name,
                                artistName = artist?.name ?: meta.name,
                                albumName = album?.name ?: "",
                                albumId = encodeToBase62(album?.gid?.toByteArray() ?: byteArrayOf()),
                                artistId = artistId,
                                durationMs = trackMeta.duration.toLong(),
                                artUri = coverHex?.let { "https://i.scdn.co/image/$it" }
                            )
                        } catch (e2: Exception) { null }
                    }
                }
            } else emptyList()

            val albums = try {
                val json = webApi.getArtistAlbums(artistId)
                val items = json.getAsJsonArray("items")
                if (items == null || items.size() == 0) {
                    Log.w(TAG, "Web API returned empty albums for $artistId")
                    loadAlbumsFromMercury(session, meta.albumGroupList, artistId, meta.name)
                } else {
                    items.mapNotNull { element ->
                        val item = element.asJsonObject
                        val artUri = item.getAsJsonArray("images")
                            ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                        Album(
                            id = item.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                            name = item.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null,
                            artistName = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("name")?.asString ?: meta.name,
                            artistId = artistId,
                            artUri = artUri,
                            year = item.getAsJsonPrimitive("release_date")?.asString?.take(4)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Web API artist albums failed, using Mercury: ${e.message}")
                loadAlbumsFromMercury(session, meta.albumGroupList, artistId, meta.name)
            }

            Result.success(Artist(
                id = artistId,
                name = meta.name,
                artUri = portraitHex?.let { "https://i.scdn.co/image/$it" },
                topTracks = topTracks,
                albums = albums
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artist: $artistId", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylist(playlistId: String): Result<Playlist> {
        return try {
            getSession()
            try {
                getPlaylistFromWebApi(playlistId)
            } catch (e: Exception) {
                Log.w(TAG, "Web API playlist failed, using Mercury: ${e.message}")
                getPlaylistFromMercury(playlistId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playlist: $playlistId", e)
            Result.failure(e)
        }
    }

    private suspend fun getPlaylistFromWebApi(playlistId: String): Result<Playlist> {
        var name = ""
        var ownerName = ""
        try {
            val meta = webApi.getPlaylist(playlistId)
            name = meta.getAsJsonPrimitive("name")?.asString ?: ""
            ownerName = meta.getAsJsonObject("owner")
                ?.getAsJsonPrimitive("display_name")?.asString ?: ""
            Log.d(TAG, "Playlist metadata: name='$name', owner='$ownerName', keys=${meta.keySet()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch playlist metadata from main endpoint", e)
        }

        val tracks = mutableListOf<Track>()
        var totalTracks = 0

        val firstPage = webApi.getPlaylistTracks(playlistId, 0, 100)
        totalTracks = firstPage.getAsJsonPrimitive("total")?.asInt ?: 0
        firstPage.getAsJsonArray("items")?.let { parseWebApiPlaylistItems(it, tracks) }
        Log.d(TAG, "Playlist first page: ${tracks.size}/$totalTracks tracks")

        val remaining = totalTracks - tracks.size
        if (remaining > 0) {
            coroutineScope {
                val pageSize = 100
                val pages = (remaining + pageSize - 1) / pageSize
                val deferreds = (1..pages).map { page ->
                    async(Dispatchers.IO) {
                        val offset = page * pageSize
                        try {
                            val pageJson = webApi.getPlaylistTracks(playlistId, offset, pageSize)
                            val items = pageJson.getAsJsonArray("items") ?: emptyList()
                            val pageTracks = mutableListOf<Track>()
                            parseWebApiPlaylistItems(items, pageTracks)
                            Log.d(TAG, "Fetched playlist page $page: ${pageTracks.size} tracks (offset=$offset)")
                            pageTracks
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch playlist page offset=$offset", e)
                            emptyList()
                        }
                    }
                }
                deferreds.awaitAll().forEach { pageTracks ->
                    tracks.addAll(pageTracks)
                }
            }
        }

        Log.i(TAG, "Web API playlist loaded: $name, ${tracks.size}/$totalTracks tracks")
        return Result.success(Playlist(
            id = playlistId,
            name = name,
            ownerName = ownerName,
            trackCount = totalTracks,
            tracks = tracks
        ))
    }

    private fun parseWebApiPlaylistItems(items: Iterable<com.google.gson.JsonElement>, out: MutableList<Track>) {
        for (element in items) {
            try {
                val item = element.asJsonObject
                val trackObj = item.getAsJsonObject("track") ?: continue
                if (trackObj.isJsonNull || trackObj.entrySet().isEmpty()) continue
                val id = trackObj.getAsJsonPrimitive("id")?.asString ?: continue
                val name = trackObj.getAsJsonPrimitive("name")?.asString ?: continue
                val firstArtist = trackObj.getAsJsonArray("artists")
                    ?.firstOrNull()?.asJsonObject
                val albumObj = trackObj.getAsJsonObject("album")
                val artUri = albumObj?.getAsJsonArray("images")
                    ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                out.add(Track(
                    id = id,
                    name = name,
                    artistName = firstArtist?.getAsJsonPrimitive("name")?.asString ?: "",
                    albumName = albumObj?.getAsJsonPrimitive("name")?.asString ?: "",
                    albumId = albumObj?.getAsJsonPrimitive("id")?.asString ?: "",
                    artistId = firstArtist?.getAsJsonPrimitive("id")?.asString ?: "",
                    durationMs = trackObj.getAsJsonPrimitive("duration_ms")?.asLong ?: 0L,
                    artUri = artUri
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse playlist track item", e)
            }
        }
    }

    private suspend fun getPlaylistFromMercury(playlistId: String): Result<Playlist> {
        val session = getSession()
        val id = PlaylistId.fromUri("spotify:playlist:$playlistId")
        val proto = session.api().getPlaylist(id)

        val name = if (proto.hasAttributes()) proto.attributes.name else ""
        val ownerName = if (proto.hasOwnerUsername()) proto.ownerUsername else ""
        val trackCount = if (proto.hasLength()) proto.length else 0

        val trackIds = if (proto.hasContents()) {
            proto.contents.itemsList.mapNotNull { item ->
                val uri = item.uri
                if (uri.startsWith("spotify:track:")) uri.removePrefix("spotify:track:")
                else null
            }
        } else emptyList()

        val tracks = trackIds.mapNotNull { trackBase62 ->
            try {
                val trackId = TrackId.fromBase62(trackBase62)
                val trackMeta = session.api().getMetadata4Track(trackId)
                val artist = if (trackMeta.artistCount > 0) trackMeta.getArtist(0) else null
                val album = if (trackMeta.hasAlbum()) trackMeta.album else null
                val coverHex = extractCoverHex(album?.coverGroup?.imageList)
                Track(
                    id = trackBase62,
                    name = trackMeta.name,
                    artistName = artist?.name ?: "",
                    albumName = album?.name ?: "",
                    albumId = encodeToBase62(album?.gid?.toByteArray() ?: byteArrayOf()),
                    artistId = encodeToBase62(artist?.gid?.toByteArray() ?: byteArrayOf()),
                    durationMs = trackMeta.duration.toLong(),
                    artUri = coverHex?.let { "https://i.scdn.co/image/$it" }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load track from playlist via Mercury", e)
                null
            }
        }

        return Result.success(Playlist(
            id = playlistId,
            name = name,
            ownerName = ownerName,
            trackCount = trackCount,
            tracks = tracks
        ))
    }

    suspend fun getUserLibrary(): Result<UserLibrary> {
        return try {
            val session = getSession()
            val username = session.username()

            val profile = session.api().getUserProfile(username, 50, 50)
            val following = session.api().getUserFollowing(username)

            val playlists = parsePlaylistFromProfile(profile)
            val artists = parseArtistsFromFollowing(following)
            val albums = emptyList<Album>()

            Result.success(UserLibrary(
                playlists = playlists,
                savedAlbums = albums,
                followedArtists = artists
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user library", e)
            Result.failure(e)
        }
    }

    private fun parsePlaylistFromProfile(profile: JsonObject): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        try {
            val playlistsElement = profile.get("playlists")
                ?: profile.get("public_playlists")
            if (playlistsElement == null || playlistsElement.isJsonNull) {
                Log.d(TAG, "No playlists in profile, keys: ${profile.keySet()}")
                return playlists
            }
            val arr = when {
                playlistsElement.isJsonArray -> playlistsElement.asJsonArray
                playlistsElement.isJsonObject -> {
                    val obj = playlistsElement.asJsonObject
                    obj.getAsJsonArray("items") ?: obj.getAsJsonArray("playlists") ?: return playlists
                }
                else -> return playlists
            }
            for (element in arr) {
                val item = element.asJsonObject
                val uri = item.getAsJsonPrimitive("uri")?.asString
                val name = item.getAsJsonPrimitive("name")?.asString
                if (uri == null || name == null) continue
                val artUri = item.getAsJsonPrimitive("image_url")?.asString
                    ?: item.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                val ownerName = item.getAsJsonPrimitive("owner_name")?.asString
                    ?: item.getAsJsonObject("owner")?.getAsJsonPrimitive("name")?.asString ?: ""
                val playlist = Playlist(
                    id = uri.removePrefix("spotify:playlist:"),
                    name = name,
                    ownerName = ownerName,
                    artUri = artUri,
                    trackCount = item.getAsJsonPrimitive("total_tracks")?.asInt ?: 0
                )
                playlists.add(playlist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse playlists from profile", e)
        }
        return playlists
    }

    private fun parseArtistsFromFollowing(following: JsonObject): List<Artist> {
        val artists = mutableListOf<Artist>()
        try {
            val arr = following.getAsJsonArray("artists") ?: return artists
            for (element in arr) {
                val item = element.asJsonObject
                val uri = item.getAsJsonPrimitive("uri")?.asString ?: continue
                val artist = Artist(
                    id = uri.removePrefix("spotify:artist:"),
                    name = item.getAsJsonPrimitive("name")?.asString ?: continue,
                    artUri = item.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                )
                artists.add(artist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse following artists", e)
        }
        return artists
    }

    private fun loadAlbumsFromMercury(
        session: xyz.gianlu.librespot.core.Session,
        groups: List<com.spotify.metadata.Metadata.AlbumGroup>,
        artistId: String,
        artistName: String
    ): List<Album> {
        return groups.flatMap { group ->
            group.albumList.mapNotNull { albumEntry ->
                try {
                    val albumId = AlbumId.fromBase62(encodeToBase62(albumEntry.gid.toByteArray()))
                    val albumMeta = session.api().getMetadata4Album(albumId)
                    val coverHex = extractCoverHex(albumMeta.coverGroup?.imageList)
                    Album(
                        id = encodeToBase62(albumEntry.gid.toByteArray()),
                        name = albumMeta.name,
                        artistName = artistName,
                        artistId = artistId,
                        artUri = coverHex?.let { "https://i.scdn.co/image/$it" },
                        year = if (albumMeta.hasDate()) albumMeta.date.year.toString() else null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load album metadata from Mercury", e)
                    null
                }
            }
        }
    }

    suspend fun getArtistAlbums(artistId: String): Result<List<Album>> {
        return try {
            getSession()
            val json = webApi.getArtistAlbums(artistId)
            val items = json.getAsJsonArray("items")
            if (items == null) {
                Log.w(TAG, "No items in artist albums response, keys: ${json.keySet()}")
                return Result.success(emptyList())
            }
            val albums = items.mapNotNull { element ->
                val item = element.asJsonObject
                val artUri = item.getAsJsonArray("images")
                    ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                Album(
                    id = item.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                    name = item.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null,
                    artistName = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("name")?.asString ?: "",
                    artistId = artistId,
                    artUri = artUri,
                    year = item.getAsJsonPrimitive("release_date")?.asString?.take(4)
                )
            }
            Log.d(TAG, "Web API returned ${albums.size} albums for artist $artistId")
            Result.success(albums)
        } catch (e: Exception) {
            Log.e(TAG, "Web API artist albums failed for $artistId: ${e.message}")
            Result.failure(e)
        }
    }

    private fun extractCoverHex(images: List<com.spotify.metadata.Metadata.Image>?): String? {
        if (images.isNullOrEmpty()) return null
        return images.maxByOrNull { it.size.number }?.fileId
            ?.let { Utils.bytesToHex(it).lowercase() }
    }

    private fun encodeToBase62(gid: ByteArray): String {
        if (gid.isEmpty()) return ""
        return String(xyz.gianlu.librespot.metadata.PlayableId.BASE62.encode(gid, 22))
    }
}
