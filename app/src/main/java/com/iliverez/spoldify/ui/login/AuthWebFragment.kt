package com.iliverez.spoldify.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.repository.AuthRepository
import com.iliverez.spoldify.databinding.FragmentAuthWebBinding

class AuthWebFragment : Fragment() {

    private var _binding: FragmentAuthWebBinding? = null
    private val binding get() = _binding!!
    private val TAG = "AuthWebFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuthWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        val authRepository = SpoldifyApp.instance.authRepository
        val alreadyLoggedIn = authRepository.authState.value is AuthRepository.AuthState.LoggedIn
        val (_, authUrl) = authRepository.createOAuthFlow()

        Log.i(TAG, "Opening OAuth URL (alreadyLoggedIn=$alreadyLoggedIn)")

        CookieManager.getInstance().removeAllCookies(null)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        binding.webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(AuthRepository.REDIRECT_URI)) {
                    val code = request.url.getQueryParameter("code")
                    val error = request.url.getQueryParameter("error")
                    if (code != null) {
                        binding.webView.visibility = View.GONE
                        binding.progressBar.visibility = View.VISIBLE
                        Log.i(TAG, "Got OAuth code, alreadyLoggedIn=$alreadyLoggedIn")
                        if (alreadyLoggedIn) {
                            authRepository.swapAuthCodeForTokens(code)
                            findNavController().popBackStack()
                        } else {
                            authRepository.loginWithOAuthCode(code)
                        }
                    } else if (error != null) {
                        Log.w(TAG, "OAuth error: $error")
                        showError(getString(R.string.error_oauth_denied))
                    }
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
            }
        }

        binding.webView.loadUrl(authUrl)

        if (!alreadyLoggedIn) {
            authRepository.authState.observe(viewLifecycleOwner) { state: AuthRepository.AuthState ->
                when (state) {
                    is AuthRepository.AuthState.LoggedIn -> {
                        findNavController().navigate(R.id.action_auth_web_to_home)
                    }
                    is AuthRepository.AuthState.Error -> {
                        showError(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.webView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        super.onDestroyView()
        _binding = null
    }
}
