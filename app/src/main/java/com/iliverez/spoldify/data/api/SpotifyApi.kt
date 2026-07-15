package com.iliverez.spoldify.data.api

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SpotifyApi(private val tokenManager: UserTokenManager) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var rateLimitedUntil: Long = 0

    private val throttleLock = Any()
    @Volatile
    private var lastRequestTime: Long = 0
    private val MIN_REQUEST_INTERVAL_MS = 1000L

    private fun throttle() {
        synchronized(throttleLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                val wait = MIN_REQUEST_INTERVAL_MS - elapsed
                Log.d(TAG, "Throttling ${wait}ms")
                try {
                    Thread.sleep(wait)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    fun search(query: String, type: String, limit: Int = 10, offset: Int = 0): JsonObject {
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "https://api.spotify.com/v1/search?q=$encodedQuery&type=$type&limit=$limit&offset=$offset"
        return request(url)
    }

    fun getAlbumTracks(albumId: String): JsonObject {
        val url = "https://api.spotify.com/v1/albums/$albumId/tracks?limit=50"
        return request(url)
    }

    fun getAlbum(albumId: String): JsonObject {
        val url = "https://api.spotify.com/v1/albums/$albumId"
        return request(url)
    }

    fun getSeveralAlbums(albumIds: List<String>): JsonObject {
        val ids = albumIds.take(20).joinToString(",")
        val url = "https://api.spotify.com/v1/albums?ids=$ids"
        return request(url)
    }

    fun getArtistTopTracks(artistId: String): JsonObject {
        val url = "https://api.spotify.com/v1/artists/$artistId/top-tracks?market=from_token"
        return request(url)
    }

    fun getArtistAlbums(artistId: String): JsonObject {
        val url = "https://api.spotify.com/v1/artists/$artistId/albums?limit=50&include_groups=album,single"
        return request(url)
    }

    fun getTracks(trackIds: List<String>): JsonObject {
        val ids = trackIds.take(50).joinToString(",")
        val url = "https://api.spotify.com/v1/tracks?ids=$ids"
        return request(url)
    }

    fun getSeveralArtists(artistIds: List<String>): JsonObject {
        val ids = artistIds.take(50).joinToString(",")
        val url = "https://api.spotify.com/v1/artists?ids=$ids"
        return request(url)
    }

    fun getPlaylist(playlistId: String): JsonObject {
        val url = "https://api.spotify.com/v1/playlists/$playlistId?fields=id,name,description,images,owner,tracks.total"
        return request(url)
    }

    fun getPlaylistTracks(playlistId: String, offset: Int, limit: Int = 100): JsonObject {
        val url = "https://api.spotify.com/v1/playlists/$playlistId/items?offset=$offset&limit=$limit"
        return request(url)
    }

    fun getRecommendations(seedArtists: String? = null, seedTracks: String? = null, limit: Int = 20): JsonObject {
        val params = mutableListOf<String>()
        seedArtists?.let { params.add("seed_artists=$it") }
        seedTracks?.let { params.add("seed_tracks=$it") }
        if (params.isEmpty()) throw IllegalArgumentException("At least one seed is required")
        val url = "https://api.spotify.com/v1/recommendations?${params.joinToString("&")}&limit=$limit"
        return request(url)
    }

    fun getUserTopTracks(limit: Int = 20, timeRange: String = "medium_term"): JsonObject {
        val url = "https://api.spotify.com/v1/me/top/tracks?limit=$limit&time_range=$timeRange"
        return request(url)
    }

    fun getUserTopArtists(limit: Int = 5, timeRange: String = "medium_term"): JsonObject {
        val url = "https://api.spotify.com/v1/me/top/artists?limit=$limit&time_range=$timeRange"
        return request(url)
    }

    fun getRecentlyPlayed(limit: Int = 20): JsonObject {
        val url = "https://api.spotify.com/v1/me/player/recently-played?limit=$limit"
        return request(url)
    }

    fun getUserPlaylists(limit: Int = 50): JsonObject {
        val url = "https://api.spotify.com/v1/me/playlists?limit=$limit"
        return request(url)
    }

    fun getArtist(artistId: String): JsonObject {
        val url = "https://api.spotify.com/v1/artists/$artistId"
        return request(url)
    }

    fun request(url: String): JsonObject {
        val now = System.currentTimeMillis()
        if (now < rateLimitedUntil) {
            val waitSec = (rateLimitedUntil - now) / 1000
            Log.w(TAG, "Rate limited, skipping ${url.take(60)} (${waitSec}s remaining)")
            throw RuntimeException("API rate limited, retry after ${waitSec}s")
        }

        throttle()

        val token = tokenManager.getValidToken()
            ?: throw RuntimeException("No valid Spotify token available")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (response.code == 401) {
                Log.w(TAG, "Token expired (401), attempting refresh")
                val newToken = tokenManager.getValidToken()
                if (newToken != null && newToken != token) {
                    return retryWithToken(url, newToken)
                }
                throw RuntimeException("Token expired and refresh failed")
            }
            if (response.code == 429) {
                val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 30L
                rateLimitedUntil = System.currentTimeMillis() + (retryAfter * 1000) + 2000L
                Log.e(TAG, "Rate limited (429) on ${url.take(60)}, Retry-After: ${retryAfter}s")
                throw RuntimeException("API rate limited, retry after ${retryAfter}s")
            }
            if (!response.isSuccessful || body == null) {
                Log.e(TAG, "API failed: ${response.code} for ${url.take(80)} - ${body?.take(200)}")
                throw RuntimeException("API request failed: ${response.code}")
            }
            return JsonParser.parseString(body).asJsonObject
        }
    }

    private fun retryWithToken(url: String, token: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                Log.e(TAG, "Retry failed: ${response.code} for ${url.take(80)}")
                throw RuntimeException("API request failed after token refresh: ${response.code}")
            }
            return JsonParser.parseString(body).asJsonObject
        }
    }

    fun clearRateLimit() {
        rateLimitedUntil = 0
        Log.i(TAG, "Rate limit state cleared")
    }

    companion object {
        private const val TAG = "SpotifyApi"

        @Volatile
        private var instance: SpotifyApi? = null

        fun fromApp(): SpotifyApi {
            return instance ?: synchronized(this) {
                instance ?: SpotifyApi(
                    UserTokenManager.getInstance(com.iliverez.spoldify.SpoldifyApp.instance)
                ).also { instance = it }
            }
        }
    }
}
