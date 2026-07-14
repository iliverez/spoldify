package com.iliverez.spoldify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.iliverez.spoldify.data.repository.AuthRepository
import com.iliverez.spoldify.databinding.ActivityMainBinding
import com.iliverez.spoldify.service.PlayerService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isReady = false
    private var pendingOAuthCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottomNav = when (destination.id) {
                R.id.loginFragment -> false
                R.id.nowPlayingFragment -> false
                else -> true
            }
            binding.bottomNav.visibility = if (showBottomNav) View.VISIBLE else View.GONE
            binding.miniPlayer.visibility = if (showBottomNav) View.VISIBLE else View.GONE
        }

        binding.miniPlayer.setOnClickListener {
            navController.navigate(R.id.nowPlayingFragment)
        }

        binding.miniPlayer.observePlayback(this)

        val authRepository = SpoldifyApp.instance.authRepository

        authRepository.authState.observe(this) { state ->
            when (state) {
                is AuthRepository.AuthState.LoggedIn -> {
                    SpoldifyApp.instance.playerWrapper.initialize(state.session)
                    startService(Intent(this, PlayerService::class.java))
                    isReady = true
                    if (navController.currentDestination?.id == R.id.loginFragment) {
                        navController.navigate(R.id.action_login_to_home)
                    }
                }
                is AuthRepository.AuthState.Idle -> {
                    isReady = true
                }
                is AuthRepository.AuthState.Loading -> {
                }
                is AuthRepository.AuthState.Error -> {
                    isReady = true
                }
            }
        }

        authRepository.tryAutoLogin()

        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "spoldify" && data.host == "auth" && data.path == "/callback") {
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            val error = data.getQueryParameter("error")
            if (code != null) {
                Log.i(TAG, "Received OAuth callback with code, state=$state")
                val authRepository = SpoldifyApp.instance.authRepository

                if (state != null && state.contains(":") && !state.startsWith("/")) {
                    relayCodeToCar(code, state)
                } else if (authRepository.authState.value is AuthRepository.AuthState.LoggedIn) {
                    authRepository.swapAuthCodeForTokens(code)
                } else {
                    authRepository.loginWithOAuthCode(code)
                }
            } else if (error != null) {
                Log.w(TAG, "OAuth callback error: $error")
            }
        }
    }

    private fun relayCodeToCar(code: String, state: String) {
        Thread {
            try {
                val encodedCode = URLEncoder.encode(code, "UTF-8")
                val url = "http://$state/callback?code=$encodedCode"
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Code relayed to car device at $state")
                        runOnUiThread {
                            Toast.makeText(this, R.string.oauth_relay_sent, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.w(TAG, "Relay failed: ${response.code}")
                        runOnUiThread {
                            Toast.makeText(this, R.string.oauth_relay_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Relay error", e)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.oauth_relay_failed) + ": " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        binding.miniPlayer.stopObserving(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
