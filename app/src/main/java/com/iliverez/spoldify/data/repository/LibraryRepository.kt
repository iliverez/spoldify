package com.iliverez.spoldify.data.repository

import android.util.Log
import com.iliverez.spoldify.data.api.SpotifyApi
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.model.UserLibrary
import com.google.gson.JsonObject

class LibraryRepository {

    private val TAG = "LibraryRepository"
    private val webApi = SpotifyApi.fromApp()

    suspend fun getUserLibrary(): Result<UserLibrary> {
        return try {
            val playlists = fetchPlaylists()
            val albums = fetchSavedAlbums()
            val artists = fetchFollowedArtists()
            Log.i(TAG, "Library loaded: ${playlists.size} playlists, ${albums.size} albums, ${artists.size} artists")
            Result.success(UserLibrary(playlists, albums, artists))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load library", e)
            Result.failure(e)
        }
    }

    private fun fetchPlaylists(): List<Playlist> {
        return try {
            val json = webApi.getUserPlaylists()
            parsePlaylists(json)
        } catch (e: Exception) {
            Log.w(TAG, "Playlists failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchSavedAlbums(): List<Album> {
        return try {
            val json = webApi.request("https://api.spotify.com/v1/me/albums?limit=50")
            val items = json.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { element ->
                try {
                    val albumObj = element.asJsonObject.getAsJsonObject("album")
                    parseAlbum(albumObj)
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Saved albums failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchFollowedArtists(): List<Artist> {
        return try {
            val json = webApi.request("https://api.spotify.com/v1/me/following?type=artist&limit=50")
            val artistsObj = json.getAsJsonObject("artists") ?: return emptyList()
            val items = artistsObj.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { element ->
                try {
                    parseArtist(element.asJsonObject)
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Followed artists failed: ${e.message}")
            emptyList()
        }
    }

    private fun parsePlaylists(json: JsonObject): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        val items = json.getAsJsonArray("items") ?: return playlists
        for (element in items) {
            val item = element.asJsonObject
            val id = item.getAsJsonPrimitive("id")?.asString ?: continue
            playlists.add(Playlist(
                id = id,
                name = item.getAsJsonPrimitive("name")?.asString ?: continue,
                ownerName = item.getAsJsonObject("owner")?.getAsJsonPrimitive("display_name")?.asString ?: "",
                artUri = item.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString,
                trackCount = item.getAsJsonObject("tracks")?.getAsJsonPrimitive("total")?.asInt ?: 0
            ))
        }
        return playlists
    }

    private fun parseAlbum(albumObj: JsonObject): Album {
        val artists = albumObj.getAsJsonArray("artists")
        val artist = artists?.firstOrNull()?.asJsonObject
        return Album(
            id = albumObj.getAsJsonPrimitive("id").asString,
            name = albumObj.getAsJsonPrimitive("name").asString,
            artistName = artist?.getAsJsonPrimitive("name")?.asString ?: "",
            artistId = artist?.getAsJsonPrimitive("id")?.asString ?: "",
            artUri = albumObj.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString,
            year = albumObj.getAsJsonPrimitive("release_date")?.asString?.take(4)
        )
    }

    private fun parseArtist(artistObj: JsonObject): Artist {
        return Artist(
            id = artistObj.getAsJsonPrimitive("id").asString,
            name = artistObj.getAsJsonPrimitive("name").asString,
            artUri = artistObj.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
        )
    }
}
