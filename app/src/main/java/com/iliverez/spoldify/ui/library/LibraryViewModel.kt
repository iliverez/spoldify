package com.iliverez.spoldify.ui.library

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.UserLibrary
import com.iliverez.spoldify.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val browseRepository = BrowseRepository()

    private val _library = MutableLiveData(UserLibrary())
    val library: LiveData<UserLibrary> = _library

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = browseRepository.getUserLibrary()
            launch(Dispatchers.Main) {
                _loading.value = false
                if (result.isSuccess) {
                    _library.value = result.getOrNull()
                } else {
                    _error.value = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to load library", result.exceptionOrNull())
                }
            }
        }
    }

    companion object {
        private const val TAG = "LibraryVM"
    }
}
