package com.iliverez.spoldify.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.iliverez.spoldify.BuildConfig
import com.iliverez.spoldify.data.api.SpotifyOAuth
import com.iliverez.spoldify.data.auth.TokenExchangeServer
import xyz.gianlu.librespot.core.OAuth
import xyz.gianlu.librespot.core.Session
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AuthRepository private constructor(private val context: Context) {

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    private val _tokenUpgradedEvent = MutableLiveData<Long>()
    val tokenUpgradedEvent: LiveData<Long> = _tokenUpgradedEvent

    private val _tokenExchangeStatus = MutableLiveData<TokenExchangeState>()
    val tokenExchangeStatus: LiveData<TokenExchangeState> = _tokenExchangeStatus

    var session: Session? = null
        private set

    val tokenManager = com.iliverez.spoldify.data.api.UserTokenManager.getInstance(context)

    private val credentialsFile: File
        get() = File(context.filesDir, "librespot_credentials.json")

    private val cacheDir: File
        get() = File(context.cacheDir, "librespot_cache").also { it.mkdirs() }

    private var currentSpotifyOAuth: SpotifyOAuth? = null
    private var tokenExchangeServer: TokenExchangeServer? = null

    fun getConfiguration(): Session.Configuration = buildConfiguration()

    internal fun buildConfiguration(): Session.Configuration {
        return Session.Configuration.Builder()
            .setCacheEnabled(true)
            .setCacheDir(cacheDir)
            .setStoreCredentials(true)
            .setStoredCredentialsFile(credentialsFile)
            .setDoCacheCleanUp(true)
            .setRetryOnChunkError(true)
            .build()
    }

    fun login(username: String, password: String) {
        if (_authState.value is AuthState.Loading) return
        _authState.postValue(AuthState.Loading)

        Thread {
            try {
                val conf = buildConfiguration()
                session = Session.Builder(conf)
                    .setDeviceName("Spoldify")
                    .setPreferredLocale("en")
                    .userPass(username, password)
                    .create()
                bootstrapSessionToken()
                _authState.postValue(AuthState.LoggedIn(session!!))
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                _authState.postValue(AuthState.Error(e.message ?: "Login failed"))
            }
        }.start()
    }

    fun loginWithOAuthCode(code: String) {
        if (_authState.value is AuthState.Loading) return
        _authState.postValue(AuthState.Loading)

        Thread {
            try {
                val oauth = currentOAuth ?: throw IllegalStateException("No OAuth flow in progress")

                oauth.setCode(code)
                oauth.requestToken()
                val creds = oauth.credentials

                val conf = buildConfiguration()
                session = Session.Builder(conf)
                    .setDeviceName("Spoldify")
                    .setPreferredLocale("en")
                    .credentials(creds)
                    .create()

                currentOAuth = null

                val token = session!!.tokens().getToken(
                    *SpotifyOAuth.VALID_SCOPES.toTypedArray()
                ).accessToken
                tokenManager.saveTokens(token, null, 3600L)

                _authState.postValue(AuthState.LoggedIn(session!!))
            } catch (e: Exception) {
                Log.e(TAG, "OAuth login failed", e)
                _authState.postValue(AuthState.Error(e.message ?: "OAuth login failed"))
            }
        }.start()
    }

    data class TokenExchangeResult(val setupUrl: String, val oauthUrl: String)

    fun startTokenExchange(): TokenExchangeResult? {
        stopTokenExchange()

        val tempServer = TokenExchangeServer(context, "") { }
        val serverUrl = tempServer.startServer()
        tempServer.stop()

        if (serverUrl == null) {
            Log.e(TAG, "No WiFi IP address available")
            _tokenExchangeStatus.postValue(TokenExchangeState.Error("No WiFi connection"))
            return null
        }

        val ipPort = serverUrl.removePrefix("http://")
        val spotifyOAuth = SpotifyOAuth(
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = REDIRECT_URI,
            scopes = SpotifyOAuth.VALID_SCOPES,
            state = ipPort
        )

        currentSpotifyOAuth = spotifyOAuth

        val server = TokenExchangeServer(context, spotifyOAuth.authorizationUrl) { code ->
            swapAuthCodeForTokens(code)
        }

        val finalUrl = server.startServer()
        if (finalUrl == null) {
            _tokenExchangeStatus.postValue(TokenExchangeState.Error("Server failed to start"))
            return null
        }
        tokenExchangeServer = server

        _tokenExchangeStatus.postValue(TokenExchangeState.Waiting)
        Log.i(TAG, "Token exchange started. Setup URL: $finalUrl")

        return TokenExchangeResult(setupUrl = finalUrl, oauthUrl = spotifyOAuth.authorizationUrl)
    }

    fun stopTokenExchange() {
        tokenExchangeServer?.stop()
        tokenExchangeServer = null
        currentSpotifyOAuth = null
    }

    fun swapAuthCodeForTokens(code: String) {
        Thread {
            try {
                val oauth = currentSpotifyOAuth
                    ?: throw IllegalStateException("No OAuth flow in progress")

                val result = oauth.exchangeCode(code)
                tokenManager.saveTokens(result.accessToken, result.refreshToken, result.expiresIn)
                tokenManager.setSessionRebootstrap {
                    try {
                        result.refreshToken?.let { rt ->
                            tokenManager.refreshWithToken(rt)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Token refresh failed: ${e.message}")
                        null
                    }
                }

                stopTokenExchange()
                Log.i(TAG, "Token exchange succeeded, has refresh: ${result.refreshToken != null}")
                _tokenUpgradedEvent.postValue(System.currentTimeMillis())
                _tokenExchangeStatus.postValue(TokenExchangeState.Connected)
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange failed", e)
                _tokenExchangeStatus.postValue(TokenExchangeState.Error(e.message ?: "Token exchange failed"))
            }
        }.start()
    }

    fun hasPendingOAuthFlow(): Boolean = currentSpotifyOAuth != null

    fun createOAuthFlow(): Pair<OAuth, String> {
        val oauth = OAuth(BuildConfig.SPOTIFY_CLIENT_ID, REDIRECT_URI)
        currentOAuth = oauth
        return Pair(oauth, oauth.authUrl)
    }

    fun tryAutoLogin() {
        if (_authState.value is AuthState.Loading) return
        if (!credentialsFile.exists() && !hasStoredCredentials()) {
            _authState.postValue(AuthState.Idle)
            return
        }

        _authState.postValue(AuthState.Loading)
        Thread {
            try {
                val conf = buildConfiguration()
                val sessionBuilder = Session.Builder(conf)
                    .setDeviceName("Spoldify")
                    .setPreferredLocale("en")

                if (credentialsFile.exists()) {
                    session = sessionBuilder.stored().create()
                } else {
                    val storage = getCredentialStorage()
                    val user = storage.username ?: throw IllegalStateException("No stored username")
                    val pass = storage.password ?: throw IllegalStateException("No stored password")
                    session = sessionBuilder.userPass(user, pass).create()
                }
                if (!tokenManager.hasToken) {
                    bootstrapSessionToken()
                }
                _authState.postValue(AuthState.LoggedIn(session!!))
            } catch (e: Exception) {
                Log.w(TAG, "Auto-login failed, showing login screen", e)
                _authState.postValue(AuthState.Idle)
            }
        }.start()
    }

    fun onZeroconfSession(newSession: Session) {
        session = newSession
        if (!tokenManager.hasToken) {
            bootstrapSessionToken()
        }
        _authState.postValue(AuthState.LoggedIn(newSession))
    }

    private fun bootstrapSessionToken() {
        try {
            val s = session ?: return
            val token = s.tokens().getToken(
                "user-top-read", "user-read-recently-played",
                "playlist-read-private", "playlist-read",
                "user-library-read", "user-follow-read"
            ).accessToken
            tokenManager.bootstrapFromSession(token)
            tokenManager.setSessionRebootstrap {
                try {
                    session?.tokens()?.getToken(
                        "user-top-read", "user-read-recently-played",
                        "playlist-read-private", "playlist-read",
                        "user-library-read", "user-follow-read"
                    )?.accessToken
                } catch (e: Exception) {
                    Log.w(TAG, "Re-bootstrap failed: ${e.message}")
                    null
                }
            }
            Log.d(TAG, "Bootstrapped token from session for user: ${s.username()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bootstrap session token: ${e.message}")
        }
    }

    fun logout() {
        stopTokenExchange()
        Thread {
            try {
                session?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session", e)
            }
            session = null
            tokenManager.clear()
            if (credentialsFile.exists()) {
                credentialsFile.delete()
            }
            getCredentialStorage().clear()
            _authState.postValue(AuthState.Idle)
        }.start()
    }

    private fun hasStoredCredentials(): Boolean = getCredentialStorage().hasCredentials

    private fun getCredentialStorage() = com.iliverez.spoldify.data.local.CredentialStorage(context)

    sealed class AuthState {
        data object Idle : AuthState()
        data object Loading : AuthState()
        data class LoggedIn(val session: Session) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    sealed class TokenExchangeState {
        data object Waiting : TokenExchangeState()
        data object Connected : TokenExchangeState()
        data class Error(val message: String) : TokenExchangeState()
    }

    companion object {
        private const val TAG = "AuthRepository"
        const val REDIRECT_URI = "spoldify://auth/callback"

        private var currentOAuth: OAuth? = null

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
    }
}
