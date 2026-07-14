package com.iliverez.spoldify.data.repository

import android.util.Log
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.api.SpotifyApi
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.model.SearchResults
import com.iliverez.spoldify.data.model.Track
import com.google.gson.JsonObject

class SearchRepository {

    private val TAG = "SearchRepository"
    private val api = SpotifyApi.fromApp()

    suspend fun search(query: String, type: String = "track,album,artist,playlist", limit: Int = 10, offset: Int = 0): Result<SearchResults> {
        return try {
            SpoldifyApp.instance.authRepository.session
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val json = api.search(query, type, limit, offset)
            val tracks = parseTracks(json)
            val albums = parseAlbums(json)
            val artists = parseArtists(json)
            val playlists = parsePlaylists(json)
            Log.d(TAG, "Search '$query' type='$type' offset=$offset results: ${tracks.size} tracks, ${albums.size} albums, ${artists.size} artists, ${playlists.size} playlists")

            Result.success(SearchResults(tracks, albums, artists, playlists))
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    suspend fun searchPaginated(query: String, type: String, pages: Int = 3): Result<SearchResults> {
        return try {
            SpoldifyApp.instance.authRepository.session
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val allTracks = mutableListOf<Track>()
            val allAlbums = mutableListOf<Album>()
            val allArtists = mutableListOf<Artist>()
            val allPlaylists = mutableListOf<Playlist>()
            val seenIds = mutableSetOf<String>()

            for (page in 0 until pages) {
                val offset = page * 10
                try {
                    val json = api.search(query, type, 10, offset)
                    val tracks = parseTracks(json).filter { seenIds.add(it.id) }
                    val albums = parseAlbums(json).filter { seenIds.add(it.id) }
                    val artists = parseArtists(json).filter { seenIds.add(it.id) }
                    val playlists = parsePlaylists(json).filter { seenIds.add(it.id) }
                    allTracks.addAll(tracks)
                    allAlbums.addAll(albums)
                    allArtists.addAll(artists)
                    allPlaylists.addAll(playlists)
                    if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) break
                } catch (e: Exception) {
                    Log.w(TAG, "Page $page failed: ${e.message}")
                    break
                }
            }
            Log.d(TAG, "Paginated search '$query' type='$type': ${allTracks.size} tracks, ${allAlbums.size} albums, ${allArtists.size} artists, ${allPlaylists.size} playlists")
            Result.success(SearchResults(allTracks, allAlbums, allArtists, allPlaylists))
        } catch (e: Exception) {
            Log.e(TAG, "Paginated search failed", e)
            Result.failure(e)
        }
    }

    private fun parseTracks(json: JsonObject): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val tracksObj = json.getAsJsonObject("tracks")
            if (tracksObj == null) {
                Log.w(TAG, "No 'tracks' object in search response, keys: ${json.keySet()}")
                return tracks
            }
            val items = tracksObj.getAsJsonArray("items")
            if (items == null) {
                Log.w(TAG, "No 'items' in tracks object, keys: ${tracksObj.keySet()}")
                return tracks
            }
            for (element in items) {
                val item = element.asJsonObject
                val id = item.getAsJsonPrimitive("id")?.asString ?: continue
                val name = item.getAsJsonPrimitive("name")?.asString ?: continue
                val artistName = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("name")?.asString ?: ""
                val albumName = item.getAsJsonObject("album")?.getAsJsonPrimitive("name")?.asString ?: ""
                val albumId = item.getAsJsonObject("album")?.getAsJsonPrimitive("id")?.asString ?: ""
                val artistId = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("id")?.asString ?: ""
                val durationMs = item.getAsJsonPrimitive("duration_ms")?.asLong ?: 0L
                val artUri = item.getAsJsonObject("album")?.let { getArtUrl(it) }
                Log.d(TAG, "Track: id=$id name='$name' artist='$artistName' album='$albumName'")
                tracks.add(Track(id, name, artistName, albumName, albumId, artistId, durationMs, artUri))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tracks", e)
        }
        return tracks
    }

    private fun parseAlbums(json: JsonObject): List<Album> {
        val albums = mutableListOf<Album>()
        try {
            val items = json.getAsJsonObject("albums")
                ?.getAsJsonArray("items") ?: return albums
            for (element in items) {
                val item = element.asJsonObject
                val album = Album(
                    id = item.getAsJsonPrimitive("id")?.asString ?: continue,
                    name = item.getAsJsonPrimitive("name")?.asString ?: continue,
                    artistName = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("name")?.asString ?: "",
                    artistId = item.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("id")?.asString ?: "",
                    artUri = getArtUrl(item),
                    year = item.getAsJsonPrimitive("release_date")?.asString?.take(4)
                )
                albums.add(album)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse albums", e)
        }
        return albums
    }

    private fun parseArtists(json: JsonObject): List<Artist> {
        val artists = mutableListOf<Artist>()
        try {
            val items = json.getAsJsonObject("artists")
                ?.getAsJsonArray("items") ?: return artists
            for (element in items) {
                val item = element.asJsonObject
                val artist = Artist(
                    id = item.getAsJsonPrimitive("id")?.asString ?: continue,
                    name = item.getAsJsonPrimitive("name")?.asString ?: continue,
                    artUri = getArtUrl(item)
                )
                artists.add(artist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse artists", e)
        }
        return artists
    }

    private fun getArtUrl(obj: JsonObject): String? {
        val images = obj.getAsJsonArray("images") ?: return null
        if (images.size() == 0) return null
        return images[0].asJsonObject.getAsJsonPrimitive("url")?.asString
    }

    private fun parsePlaylists(json: JsonObject): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        val playlistsObj = json.getAsJsonObject("playlists")
        if (playlistsObj == null) return playlists
        val items = playlistsObj.getAsJsonArray("items") ?: return playlists
        Log.d(TAG, "Playlists response: total=${playlistsObj.getAsJsonPrimitive("total")?.asInt}, items=${items.size()}")
        if (items.size() > 0) {
            Log.d(TAG, "First playlist element type: ${items[0].javaClass.simpleName}, isNull=${items[0].isJsonNull}, value=${items[0].toString().take(200)}")
        }
        for (element in items) {
            try {
                if (element.isJsonNull) continue
                val item = element.asJsonObject
                val id = item.getAsJsonPrimitive("id")?.asString ?: continue
                val name = item.getAsJsonPrimitive("name")?.asString ?: continue
                val ownerObj = item.getAsJsonObject("owner")
                playlists.add(Playlist(
                    id = id,
                    name = name,
                    ownerName = ownerObj?.getAsJsonPrimitive("display_name")?.asString ?: "",
                    artUri = getArtUrl(item),
                    trackCount = item.getAsJsonObject("tracks")?.getAsJsonPrimitive("total")?.asInt ?: 0
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse playlist item: element=${element.toString().take(100)} error=${e.message}")
            }
        }
        return playlists
    }
}
