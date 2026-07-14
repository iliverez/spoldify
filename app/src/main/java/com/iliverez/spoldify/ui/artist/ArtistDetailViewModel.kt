package com.iliverez.spoldify.ui.artist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val browseRepository = BrowseRepository()

    private val _artist = MutableLiveData<Artist>()
    val artist: LiveData<Artist> = _artist

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _loadingMoreAlbums = MutableLiveData(false)
    val loadingMoreAlbums: LiveData<Boolean> = _loadingMoreAlbums

    fun loadArtist(artistId: String) {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = browseRepository.getArtist(artistId)
            launch(Dispatchers.Main) {
                _loading.value = false
                if (result.isSuccess) {
                    _artist.value = result.getOrNull()
                } else {
                    _error.value = result.exceptionOrNull()?.message
                    Log.e(TAG, "Failed to load artist", result.exceptionOrNull())
                }
            }
        }
    }

    fun loadAllAlbums(artistId: String) {
        _loadingMoreAlbums.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = browseRepository.getArtistAlbums(artistId)
            launch(Dispatchers.Main) {
                _loadingMoreAlbums.value = false
                if (result.isSuccess) {
                    val currentArtist = _artist.value
                    val newAlbums = result.getOrNull().orEmpty()
                    if (currentArtist != null && newAlbums.isNotEmpty()) {
                        _artist.value = currentArtist.copy(albums = newAlbums)
                    }
                } else {
                    _error.value = result.exceptionOrNull()?.message
                }
            }
        }
    }

    companion object {
        private const val TAG = "ArtistDetailVM"
    }
}
