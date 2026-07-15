package com.iliverez.spoldify.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iliverez.spoldify.BuildConfig
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UserTokenManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "spoldify_user_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val TAG = "UserTokenManager"

    var accessToken: String? = null
        private set
    private var refreshToken: String? = null
    private var tokenExpiry: Long = 0
    @Volatile
    private var isSessionToken: Boolean = false

    val hasToken: Boolean
        get() = prefs.contains(KEY_REFRESH_TOKEN) || prefs.contains(KEY_ACCESS_TOKEN)

    val hasOAuthRefreshToken: Boolean
        get() = prefs.contains(KEY_REFRESH_TOKEN)

    fun saveTokens(accessToken: String, refreshToken: String?, expiresIn: Long) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L) - 60_000L
        this.isSessionToken = false

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_EXPIRY, tokenExpiry)
            putBoolean(KEY_IS_SESSION_TOKEN, false)
            apply()
        }
        Log.d(TAG, "Saved OAuth tokens, expires in ${expiresIn}s")
    }

    private var sessionRebootstrap: (() -> String?)? = null

    fun setSessionRebootstrap(block: () -> String?) {
        sessionRebootstrap = block
    }

    fun getValidToken(): String? {
        if (isSessionToken) {
            val rt = refreshToken ?: loadRefreshTokenFromPrefs()
            if (rt != null) {
                val refreshed = refreshWithToken(rt)
                if (refreshed != null) {
                    Log.d(TAG, "Token source: OAuth refresh (upgraded from session token)")
                    return refreshed
                }
            }
        }

        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            Log.d(TAG, "Token source: cached ${if (isSessionToken) "session" else "OAuth"}")
            return accessToken
        }

        loadFromPrefs()
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            Log.d(TAG, "Token source: persisted ${if (isSessionToken) "session" else "OAuth"}")
            return accessToken
        }

        val rt = refreshToken ?: loadRefreshTokenFromPrefs()
        if (rt != null) {
            Log.d(TAG, "Token source: OAuth refresh (app client ID ${BuildConfig.SPOTIFY_CLIENT_ID.take(8)}...)")
            return refreshWithToken(rt)
        }

        val newToken = sessionRebootstrap?.invoke()
        if (newToken != null) {
            Log.w(TAG, "Token source: librespot session bootstrap (NOT app client ID)")
            bootstrapFromSession(newToken)
            return newToken
        }

        Log.w(TAG, "No valid token, no refresh token, no session available")
        return null
    }

    fun bootstrapFromSession(sessionAccessToken: String) {
        val existingRt = refreshToken ?: prefs.getString(KEY_REFRESH_TOKEN, null)
        if (existingRt != null) {
            val refreshed = refreshWithToken(existingRt)
            if (refreshed != null) {
                Log.d(TAG, "Used OAuth refresh instead of session bootstrap")
                return
            }
        }

        this.accessToken = sessionAccessToken
        this.refreshToken = existingRt
        this.tokenExpiry = System.currentTimeMillis() + 3_540_000L
        this.isSessionToken = true

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, sessionAccessToken)
            if (existingRt != null) {
                putString(KEY_REFRESH_TOKEN, existingRt)
            }
            putLong(KEY_EXPIRY, tokenExpiry)
            putBoolean(KEY_IS_SESSION_TOKEN, true)
            apply()
        }
        Log.d(TAG, "Bootstrapped token from session")
    }

    fun refreshWithToken(refreshToken: String): String? {
        return try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "Token refresh failed: ${response.code} - ${responseBody?.take(200)}")
                    if (response.code == 400) {
                        Log.w(TAG, "Refresh token invalidated, clearing from storage")
                        prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
                        this.refreshToken = null
                    }
                    return null
                }
                val json = JsonParser.parseString(responseBody).asJsonObject
                val newAccessToken = json.getAsJsonPrimitive("access_token").asString
                val expiresIn = json.getAsJsonPrimitive("expires_in").asLong
                val newRefreshToken = json.getAsJsonPrimitive("refresh_token")?.asString

                saveTokens(newAccessToken, newRefreshToken ?: refreshToken, expiresIn)
                Log.d(TAG, "Token refreshed, expires in ${expiresIn}s")
                newAccessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error: ${e.message}")
            null
        }
    }

    private fun loadFromPrefs() {
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        tokenExpiry = prefs.getLong(KEY_EXPIRY, 0)
        isSessionToken = prefs.getBoolean(KEY_IS_SESSION_TOKEN, false)
    }

    private fun loadRefreshTokenFromPrefs(): String? {
        val rt = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (rt != null) refreshToken = rt
        return rt
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        tokenExpiry = 0
        isSessionToken = false
        prefs.edit().clear().apply()
        Log.d(TAG, "Tokens cleared")
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRY = "token_expiry"
        private const val KEY_IS_SESSION_TOKEN = "is_session_token"
        @Volatile
        private var instance: UserTokenManager? = null

        fun getInstance(context: Context): UserTokenManager =
            instance ?: synchronized(this) {
                instance ?: UserTokenManager(context.applicationContext).also { instance = it }
            }
    }
}
