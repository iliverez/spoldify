package com.iliverez.spoldify.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.local.CredentialStorage
import com.iliverez.spoldify.data.repository.AuthRepository

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = SpoldifyApp.instance.authRepository
    private val credentialStorage = SpoldifyApp.instance.credentialStorage

    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Input)
    val uiState: LiveData<LoginUiState> = _uiState

    init {
        authRepository.authState.observeForever { state: AuthRepository.AuthState ->
            when (state) {
                is AuthRepository.AuthState.Idle -> {
                    _uiState.postValue(LoginUiState.Input)
                }
                is AuthRepository.AuthState.Loading -> {
                    _uiState.postValue(LoginUiState.Loading)
                }
                is AuthRepository.AuthState.LoggedIn -> {
                    _uiState.postValue(LoginUiState.Success)
                }
                is AuthRepository.AuthState.Error -> {
                    _uiState.postValue(LoginUiState.Error(state.message))
                }
            }
        }
    }

    fun login(username: String, password: String, rememberMe: Boolean) {
        if (_uiState.value is LoginUiState.Loading) return
        if (rememberMe) {
            credentialStorage.saveCredentials(username, password)
        }
        authRepository.login(username, password)
    }

    sealed class LoginUiState {
        data object Input : LoginUiState()
        data object Loading : LoginUiState()
        data object Success : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }
}
