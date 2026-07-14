package com.iliverez.spoldify.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.databinding.FragmentLoginBinding
import com.iliverez.spoldify.ui.login.LoginViewModel.LoginUiState
import com.iliverez.spoldify.zeroconf.ZeroconfManager

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.btnConnectPair.setOnClickListener {
            SpoldifyApp.instance.zeroconfManager.start()
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        binding.btnOAuth.setOnClickListener {
            val authRepository = SpoldifyApp.instance.authRepository
            val (_, authUrl) = authRepository.createOAuthFlow()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        }

        binding.tvError.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun attemptLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (username.isBlank()) {
            binding.tilUsername.error = getString(R.string.error_empty_credentials)
            return
        }
        if (password.isBlank()) {
            binding.tilPassword.error = getString(R.string.error_empty_credentials)
            return
        }

        binding.tilUsername.error = null
        binding.tilPassword.error = null

        val rememberMe = binding.cbRememberMe.isChecked
        viewModel.login(username, password, rememberMe)
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoginUiState.Input -> showInputState()
                is LoginUiState.Loading -> showLoadingState()
                is LoginUiState.Success -> navigateToHome()
                is LoginUiState.Error -> showErrorState(state.message)
            }
        }

        SpoldifyApp.instance.zeroconfManager.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                ZeroconfManager.ZeroconfState.IDLE -> {
                    binding.connectStatusContainer.visibility = View.GONE
                    binding.btnConnectPair.isEnabled = true
                }
                ZeroconfManager.ZeroconfState.STARTING -> {
                    binding.connectStatusContainer.visibility = View.VISIBLE
                    binding.connectProgressBar.visibility = View.VISIBLE
                    binding.tvConnectStatus.text = getString(R.string.connect_starting)
                    binding.btnConnectPair.isEnabled = false
                }
                ZeroconfManager.ZeroconfState.WAITING -> {
                    binding.connectStatusContainer.visibility = View.VISIBLE
                    binding.connectProgressBar.visibility = View.VISIBLE
                    binding.tvConnectStatus.text = getString(R.string.connect_waiting)
                    binding.btnConnectPair.isEnabled = false
                }
                ZeroconfManager.ZeroconfState.CONNECTED -> {
                    binding.connectStatusContainer.visibility = View.GONE
                    binding.btnConnectPair.isEnabled = false
                }
                ZeroconfManager.ZeroconfState.ERROR -> {
                    binding.connectStatusContainer.visibility = View.VISIBLE
                    binding.connectProgressBar.visibility = View.GONE
                    binding.tvConnectStatus.text = getString(R.string.connect_error)
                    binding.btnConnectPair.isEnabled = true
                }
            }
        }
    }

    private fun showInputState() {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        binding.btnOAuth.isEnabled = true
        binding.etUsername.isEnabled = true
        binding.etPassword.isEnabled = true
        binding.tvError.visibility = View.GONE
    }

    private fun showLoadingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnOAuth.isEnabled = false
        binding.etUsername.isEnabled = false
        binding.etPassword.isEnabled = false
        binding.tvError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        binding.btnOAuth.isEnabled = true
        binding.etUsername.isEnabled = true
        binding.etPassword.isEnabled = true
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    private fun navigateToHome() {
        binding.progressBar.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
