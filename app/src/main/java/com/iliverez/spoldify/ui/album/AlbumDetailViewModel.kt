package com.iliverez.spoldify.ui.album

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlbumDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val browseRepository = BrowseRepository()

    private val _album = MutableLiveData<Album>()
    val album: LiveData<Album> = _album

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadAlbum(albumId: String) {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = browseRepository.getAlbum(albumId)
            launch(Dispatchers.Main) {
                _loading.value = false
                if (result.isSuccess) {
                    _album.value = result.getOrNull()
                } else {
                    _error.value = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to load album", result.exceptionOrNull())
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlbumDetailVM"
    }
}
