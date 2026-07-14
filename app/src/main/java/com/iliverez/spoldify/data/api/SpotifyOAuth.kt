package com.iliverez.spoldify.data.api

import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class SpotifyOAuth(
    private val clientId: String,
    private val redirectUri: String,
    private val scopes: List<String> = VALID_SCOPES,
    private val state: String? = null
) {
    val codeVerifier: String = generateCodeVerifier()
    val codeChallenge: String = generateCodeChallenge(codeVerifier)

    val authorizationUrl: String = buildAuthorizationUrl()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun buildAuthorizationUrl(): String {
        val scopeEncoded = URLEncoder.encode(scopes.joinToString(" "), "UTF-8")
        val redirectEncoded = URLEncoder.encode(redirectUri, "UTF-8")
        val sb = StringBuilder("https://accounts.spotify.com/authorize?")
            .append("response_type=code")
            .append("&client_id=").append(clientId)
            .append("&redirect_uri=").append(redirectEncoded)
            .append("&code_challenge=").append(codeChallenge)
            .append("&code_challenge_method=S256")
            .append("&scope=").append(scopeEncoded)
        if (state != null) {
            sb.append("&state=").append(URLEncoder.encode(state, "UTF-8"))
        }
        return sb.toString()
    }

    fun exchangeCode(code: String): TokenResult {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("redirect_uri", redirectUri)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw RuntimeException("Empty response from token endpoint")
            if (!response.isSuccessful) {
                throw RuntimeException("Token exchange failed: ${response.code} - $responseBody")
            }
            val json = JsonParser.parseString(responseBody).asJsonObject
            val accessToken = json.getAsJsonPrimitive("access_token").asString
            val refreshToken = json.getAsJsonPrimitive("refresh_token")?.asString
            val expiresIn = json.getAsJsonPrimitive("expires_in")?.asLong ?: 3600L
            Log.d(TAG, "Token exchange successful, expires in ${expiresIn}s, has refresh: ${refreshToken != null}")
            return TokenResult(accessToken, refreshToken, expiresIn)
        }
    }

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long
    )

    companion object {
        private const val TAG = "SpotifyOAuth"

        val VALID_SCOPES = listOf(
            "streaming",
            "playlist-read-private",
            "playlist-read-collaborative",
            "playlist-modify-private",
            "playlist-modify-public",
            "ugc-image-upload",
            "user-follow-read",
            "user-follow-modify",
            "user-library-read",
            "user-library-modify",
            "user-read-email",
            "user-read-private",
            "user-top-read",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-read-recently-played",
            "user-read-playback-position"
        )

        fun refreshToken(clientId: String, refreshToken: String): TokenResult {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    throw RuntimeException("Token refresh failed: ${response.code}")
                }
                val json = JsonParser.parseString(responseBody).asJsonObject
                val accessToken = json.getAsJsonPrimitive("access_token").asString
                val expiresIn = json.getAsJsonPrimitive("expires_in").asLong
                val newRefreshToken = json.getAsJsonPrimitive("refresh_token")?.asString
                return TokenResult(accessToken, newRefreshToken ?: refreshToken, expiresIn)
            }
        }
    }
}
