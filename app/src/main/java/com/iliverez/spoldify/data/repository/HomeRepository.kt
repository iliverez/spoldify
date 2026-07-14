package com.iliverez.spoldify.data.repository

import android.util.Log
import com.iliverez.spoldify.data.api.SpotifyApi
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.HomeSection
import com.iliverez.spoldify.data.model.Track
import com.google.gson.JsonObject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class HomeRepository {

    private val TAG = "HomeRepository"
    private val webApi = SpotifyApi.fromApp()

    private data class CacheEntry<T>(val data: T, val timestamp: Long)
    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L
    private val REC_CACHE_TTL_MS = 10 * 60 * 1000L

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String, ttl: Long = CACHE_TTL_MS): T? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttl) {
            cache.remove(key)
            return null
        }
        return entry.data as T
    }

    private fun <T> putCache(key: String, data: T) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    fun clearCache() = cache.clear()

    data class Wave1Result(
        val topTracks: List<Track>,
        val topTrackIds: List<String>,
        val topArtistIds: List<String>,
        val recentlyPlayed: List<Track>
    )

    suspend fun fetchWave1(): Wave1Result {
        val cachedTracks = getCached<List<Track>>("top_tracks_20")
        val cachedArtistIds = getCached<List<String>>("top_artist_ids")
        val cachedRecent = getCached<List<Track>>("recently_played")

        if (cachedTracks != null && cachedArtistIds != null && cachedRecent != null) {
            val ids = cachedTracks.take(5).map { it.id }
            Log.i(TAG, "Wave1: all cache hit")
            return Wave1Result(cachedTracks, ids, cachedArtistIds, cachedRecent)
        }

        return coroutineScope {
            val topTracksDeferred = async {
                fetchTopTracksInternal()
            }
            val topArtistsDeferred = async {
                fetchTopArtistIds()
            }
            val recentDeferred = async {
                fetchRecentlyPlayedTracks()
            }

            val topTracks = topTracksDeferred.await()
            val topArtistIds = topArtistsDeferred.await()
            val recentTracks = recentDeferred.await()
            val topTrackIds = topTracks.take(5).map { it.id }

            Wave1Result(topTracks, topTrackIds, topArtistIds, recentTracks)
        }
    }

    data class Wave2Result(
        val madeForYou: HomeSection.MadeForYou?,
        val recentlyPlayed: HomeSection.RecentlyPlayed?,
        val jumpBackIn: HomeSection.JumpBackIn?,
        val radioMix: HomeSection.RadioMix?,
        val newReleases: HomeSection.NewReleases?
    )

    suspend fun fetchWave2(wave1: Wave1Result): Wave2Result = coroutineScope {
        val madeForYouDeferred = async {
            fetchMadeForYou(wave1.topTrackIds)
        }
        val radioMixDeferred = async {
            fetchRadioMix(wave1.topArtistIds)
        }

        val recentlyPlayed = if (wave1.recentlyPlayed.isNotEmpty()) {
            HomeSection.RecentlyPlayed(wave1.recentlyPlayed)
        } else null

        val jumpBackInAlbums = wave1.recentlyPlayed.map { track ->
            Album(
                id = track.albumId,
                name = track.albumName,
                artistName = track.artistName,
                artistId = track.artistId,
                artUri = track.artUri
            )
        }.distinctBy { it.id }
        val jumpBackIn = if (jumpBackInAlbums.isNotEmpty()) {
            HomeSection.JumpBackIn(jumpBackInAlbums)
        } else null

        val topTrackAlbums = wave1.topTracks.map {
            Album(
                id = it.albumId,
                name = it.albumName,
                artistName = it.artistName,
                artistId = it.artistId,
                artUri = it.artUri,
                year = null
            )
        }.distinctBy { it.id }
        val newReleases = if (topTrackAlbums.isNotEmpty()) {
            HomeSection.NewReleases(topTrackAlbums)
        } else null

        val madeForYou = madeForYouDeferred.await()
        val radioMix = radioMixDeferred.await()

        Wave2Result(madeForYou, recentlyPlayed, jumpBackIn, radioMix, newReleases)
    }

    private suspend fun fetchMadeForYou(seedTrackIds: List<String>): HomeSection.MadeForYou? {
        getCached<List<Album>>("rec_tracks")?.let {
            Log.i(TAG, "MadeForYou: cache hit (${it.size} items)")
            return HomeSection.MadeForYou(it)
        }
        return try {
            val seeds = seedTrackIds.take(5).joinToString(",")
            if (seeds.isBlank()) return fetchCuratedMadeForYou()
            Log.i(TAG, "MadeForYou seeds (tracks): $seeds")
            val json = webApi.getRecommendations(seedTracks = seeds, limit = 20)
            val items = json.getAsJsonArray("tracks") ?: return fetchCuratedMadeForYou()
            val albums = items.mapNotNull { element ->
                try {
                    val trackObj = element.asJsonObject
                    val albumObj = trackObj.getAsJsonObject("album")
                    val artists = trackObj.getAsJsonArray("artists")
                    val artist = artists?.firstOrNull()?.asJsonObject
                    Album(
                        id = albumObj?.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                        name = albumObj.getAsJsonPrimitive("name").asString,
                        artistName = artist?.getAsJsonPrimitive("name")?.asString ?: "",
                        artistId = artist?.getAsJsonPrimitive("id")?.asString ?: "",
                        artUri = albumObj.getAsJsonArray("images")
                            ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse made-for-you track: ${e.message}")
                    null
                }
            }.distinctBy { it.id }
            Log.i(TAG, "MadeForYou: ${albums.size} albums from recommendations")
            if (albums.isNotEmpty()) {
                putCache("rec_tracks", albums)
                HomeSection.MadeForYou(albums)
            } else fetchCuratedMadeForYou()
        } catch (e: Exception) {
            Log.e(TAG, "fetchMadeForYou failed", e)
            fetchCuratedMadeForYou()
        }
    }

    private suspend fun fetchCuratedMadeForYou(): HomeSection.MadeForYou? {
        return try {
            val curatedIds = listOf(
                "37i9dQZF1DXcBWIGoYBM5M",
                "37i9dQZF1DX0XUsuxWHRQd",
                "37i9dQZF1DX1lVhptIYRda",
                "37i9dQZF1DX4SBhb3fqCJd",
                "37i9dQZF1DX4dyzvuaRJ0n",
                "37i9dQZF1DX5Ejj0EkURtP"
            )
            val albums = coroutineScope {
                curatedIds.map { playlistId ->
                    async {
                        try {
                            val json = webApi.getPlaylist(playlistId)
                            val artUri = json.getAsJsonArray("images")
                                ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                            Album(
                                id = json.getAsJsonPrimitive("id")?.asString ?: return@async null,
                                name = json.getAsJsonPrimitive("name")?.asString ?: return@async null,
                                artistName = json.getAsJsonObject("owner")
                                    ?.getAsJsonPrimitive("display_name")?.asString ?: "Spotify",
                                artistId = "",
                                artUri = artUri
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch curated playlist $playlistId: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            Log.i(TAG, "Curated MadeForYou: ${albums.size} playlists")
            if (albums.isNotEmpty()) HomeSection.MadeForYou(albums) else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchCuratedMadeForYou failed", e)
            null
        }
    }

    private suspend fun fetchRadioMix(topArtistIds: List<String>): HomeSection.RadioMix? {
        getCached<List<Album>>("rec_artists")?.let {
            Log.i(TAG, "RadioMix: cache hit (${it.size} items)")
            return HomeSection.RadioMix(it)
        }
        return try {
            if (topArtistIds.isEmpty()) return null
            val seeds = topArtistIds.take(5).joinToString(",")
            Log.i(TAG, "RadioMix seeds: $seeds")
            val json = webApi.getRecommendations(seedArtists = seeds, limit = 20)
            val items = json.getAsJsonArray("tracks") ?: return null
            val albums = items.mapNotNull { element ->
                try {
                    val trackObj = element.asJsonObject
                    val albumObj = trackObj.getAsJsonObject("album")
                    val artists = trackObj.getAsJsonArray("artists")
                    val artist = artists?.firstOrNull()?.asJsonObject
                    Album(
                        id = albumObj?.getAsJsonPrimitive("id")?.asString ?: return@mapNotNull null,
                        name = albumObj.getAsJsonPrimitive("name").asString,
                        artistName = artist?.getAsJsonPrimitive("name")?.asString ?: "",
                        artistId = artist?.getAsJsonPrimitive("id")?.asString ?: "",
                        artUri = albumObj.getAsJsonArray("images")
                            ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse radio track: ${e.message}")
                    null
                }
            }.distinctBy { it.id }
            Log.i(TAG, "RadioMix: ${albums.size} albums from recommendations")
            if (albums.isNotEmpty()) {
                putCache("rec_artists", albums)
                HomeSection.RadioMix(albums)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchRadioMix failed", e)
            null
        }
    }

    private fun fetchTopArtistIds(): List<String> {
        getCached<List<String>>("top_artist_ids")?.let { return it }
        return try {
            val json = webApi.getUserTopArtists(limit = 5)
            val items = json.getAsJsonArray("items") ?: return emptyList()
            val ids = items.mapNotNull { element ->
                try {
                    element.asJsonObject.getAsJsonPrimitive("id")?.asString
                } catch (e: Exception) {
                    null
                }
            }
            putCache("top_artist_ids", ids)
            ids
        } catch (e: Exception) {
            Log.w(TAG, "Top artists failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchRecentlyPlayedTracks(): List<Track> {
        getCached<List<Track>>("recently_played")?.let { return it }
        return try {
            val json = webApi.getRecentlyPlayed(limit = 20)
            val items = json.getAsJsonArray("items") ?: return emptyList()
            val tracks = items.mapNotNull { element ->
                try {
                    val trackObj = element.asJsonObject.getAsJsonObject("track")
                    parseWebApiTrack(trackObj)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse recent track: ${e.message}")
                    null
                }
            }.distinctBy { it.id }
            putCache("recently_played", tracks)
            tracks
        } catch (e: Exception) {
            Log.w(TAG, "Recently played failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchTopTracksInternal(): List<Track> {
        getCached<List<Track>>("top_tracks_20")?.let { return it }
        return try {
            val json = webApi.getUserTopTracks(limit = 20)
            val items = json.getAsJsonArray("items") ?: return emptyList()
            val tracks = items.mapNotNull { element ->
                try {
                    parseWebApiTrack(element.asJsonObject)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse top track: ${e.message}")
                    null
                }
            }
            putCache("top_tracks_20", tracks)
            tracks
        } catch (e: Exception) {
            Log.w(TAG, "Top tracks failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseWebApiTrack(trackObj: JsonObject): Track {
        val artists = trackObj.getAsJsonArray("artists")
        val artist = artists?.firstOrNull()?.asJsonObject
        val artistName = artist?.getAsJsonPrimitive("name")?.asString ?: ""
        val artistId = artist?.getAsJsonPrimitive("id")?.asString ?: ""
        val albumObj = trackObj.getAsJsonObject("album")
        val albumName = albumObj?.getAsJsonPrimitive("name")?.asString ?: ""
        val albumId = albumObj?.getAsJsonPrimitive("id")?.asString ?: ""
        val artUri = albumObj?.getAsJsonArray("images")
            ?.firstOrNull()?.asJsonObject?.getAsJsonPrimitive("url")?.asString
        val name = trackObj.getAsJsonPrimitive("name").asString
        val id = trackObj.getAsJsonPrimitive("id").asString
        val durationMs = trackObj.getAsJsonPrimitive("duration_ms").asLong
        return Track(
            id = id,
            name = name,
            artistName = artistName,
            albumName = albumName,
            albumId = albumId,
            artistId = artistId,
            durationMs = durationMs,
            artUri = artUri
        )
    }
}
