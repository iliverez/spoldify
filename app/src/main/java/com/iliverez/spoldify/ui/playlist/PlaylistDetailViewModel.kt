package com.iliverez.spoldify.ui.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val browseRepository = BrowseRepository()

    private val _playlist = MutableLiveData<Playlist>()
    val playlist: LiveData<Playlist> = _playlist

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadPlaylist(playlistId: String) {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = browseRepository.getPlaylist(playlistId)
            launch(Dispatchers.Main) {
                _loading.value = false
                if (result.isSuccess) {
                    _playlist.value = result.getOrNull()
                } else {
                    _error.value = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to load playlist", result.exceptionOrNull())
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlaylistDetailVM"
    }
}
