package com.iliverez.spoldify.ui.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iliverez.spoldify.data.model.SearchResults
import com.iliverez.spoldify.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SearchViewModel"
    private val searchRepository = SearchRepository()

    private val _searchResults = MutableLiveData(SearchResults())
    val searchResults: LiveData<SearchResults> = _searchResults

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private var searchJob: Job? = null

    fun search(query: String, filter: SearchFilter = SearchFilter.ALL) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = SearchResults()
            _isSearching.value = false
            return
        }
        Log.d(TAG, "Searching for: '$query' filter=$filter")
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.postValue(true)
            val result = if (filter == SearchFilter.ALL) {
                searchRepository.search(query, "track,album,artist,playlist", 10, 0)
            } else {
                val type = when (filter) {
                    SearchFilter.TRACKS -> "track"
                    SearchFilter.ALBUMS -> "album"
                    SearchFilter.ARTISTS -> "artist"
                    SearchFilter.PLAYLISTS -> "playlist"
                    SearchFilter.ALL -> "track,album,artist,playlist"
                }
                searchRepository.searchPaginated(query, type, 3)
            }
            result.onSuccess {
                Log.d(TAG, "Search results: ${it.tracks.size} tracks, ${it.albums.size} albums, ${it.artists.size} artists, ${it.playlists.size} playlists")
                _searchResults.postValue(it)
            }.onFailure {
                Log.e(TAG, "Search failed: ${it.message}")
            }
            _isSearching.postValue(false)
        }
    }
}
