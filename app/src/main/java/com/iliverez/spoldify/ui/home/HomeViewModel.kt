package com.iliverez.spoldify.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.data.repository.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HomeViewModel"
    private val homeRepository = HomeRepository()

    private val _recentlyPlayed = MutableLiveData<List<Track>>()
    val recentlyPlayed: LiveData<List<Track>> = _recentlyPlayed

    private val _jumpBackIn = MutableLiveData<List<Album>>()
    val jumpBackIn: LiveData<List<Album>> = _jumpBackIn

    private val _madeForYou = MutableLiveData<List<Album>>()
    val madeForYou: LiveData<List<Album>> = _madeForYou

    private val _radioMix = MutableLiveData<List<Album>>()
    val radioMix: LiveData<List<Album>> = _radioMix

    private val _newReleases = MutableLiveData<List<Album>>()
    val newReleases: LiveData<List<Album>> = _newReleases

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        Log.i(TAG, "Spoldify build v2026.06.01d")
        loadHome()
    }

    fun clearCacheAndReload() {
        homeRepository.clearCache()
        _isLoading.value = false
        loadHome()
    }

    fun loadHome() {
        if (_isLoading.value == true) return
        _isLoading.value = true
        _error.value = null
        Log.i(TAG, "loadHome() called")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timeout = 30_000L

                val wave1 = withTimeoutOrNull(timeout) {
                    homeRepository.fetchWave1()
                }

                if (wave1 == null) {
                    Log.e(TAG, "Wave1 timed out")
                    _error.postValue("Timed out loading content")
                    return@launch
                }

                Log.i(TAG, "Wave1 done: ${wave1.topTracks.size} top tracks, " +
                        "${wave1.topArtistIds.size} artist seeds, " +
                        "${wave1.recentlyPlayed.size} recent")

                val wave2 = withTimeoutOrNull(timeout) {
                    homeRepository.fetchWave2(wave1)
                }

                if (wave2 == null) {
                    Log.e(TAG, "Wave2 timed out")
                    _error.postValue("Timed out loading recommendations")
                    return@launch
                }

                wave2.madeForYou?.let {
                    Log.i(TAG, "MadeForYou: ${it.items.size} items")
                    _madeForYou.postValue(it.items)
                }
                wave2.recentlyPlayed?.let {
                    Log.i(TAG, "RecentlyPlayed: ${it.items.size} tracks")
                    _recentlyPlayed.postValue(it.items)
                }
                wave2.jumpBackIn?.let {
                    Log.i(TAG, "JumpBackIn: ${it.items.size} albums")
                    _jumpBackIn.postValue(it.items)
                }
                wave2.radioMix?.let {
                    Log.i(TAG, "RadioMix: ${it.items.size} items")
                    _radioMix.postValue(it.items)
                }
                wave2.newReleases?.let {
                    Log.i(TAG, "NewReleases: ${it.items.size} items")
                    _newReleases.postValue(it.items)
                }

                val hasData = wave2.madeForYou != null || wave2.recentlyPlayed != null ||
                        wave2.radioMix != null || wave2.newReleases != null
                if (!hasData) {
                    Log.w(TAG, "No home data loaded")
                    _error.postValue("No content available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadHome failed", e)
                _error.postValue(e.message)
            }
            _isLoading.postValue(false)
        }
    }
}
