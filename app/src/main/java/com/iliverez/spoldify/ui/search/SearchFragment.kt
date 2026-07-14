package com.iliverez.spoldify.ui.search

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.data.model.SearchResultItem
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private val TAG = "SearchFragment"
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: SearchResultsAdapter
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearch()
        setupFilterChips()
        setupRecyclerView()
        observeResults()
        Log.i(TAG, "SearchFragment created")
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString()
        if (query.isNotBlank()) {
            viewModel.search(query, getCurrentFilter())
        }
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchRunnable?.let { debounceHandler.removeCallbacks(it) }
                performSearch()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { debounceHandler.removeCallbacks(it) }
                val query = s?.toString() ?: ""
                if (query.length >= 3) {
                    searchRunnable = Runnable { performSearch() }
                    debounceHandler.postDelayed(searchRunnable!!, 500)
                } else if (query.isBlank()) {
                    viewModel.search("")
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            performSearch()
        }
    }

    private fun getCurrentFilter(): SearchFilter {
        return when (binding.chipGroupFilter.checkedChipId) {
            R.id.chipTracks -> SearchFilter.TRACKS
            R.id.chipAlbums -> SearchFilter.ALBUMS
            R.id.chipArtists -> SearchFilter.ARTISTS
            R.id.chipPlaylists -> SearchFilter.PLAYLISTS
            else -> SearchFilter.ALL
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchResultsAdapter(
            onTrackClick = { track ->
                SpoldifyApp.instance.playerWrapper.play("spotify:track:${track.id}")
                findNavController().navigate(R.id.action_search_to_now_playing)
            },
            onAlbumClick = { album ->
                val bundle = Bundle().apply { putString("albumId", album.id) }
                findNavController().navigate(R.id.action_search_to_album, bundle)
            },
            onArtistClick = { artist ->
                val bundle = Bundle().apply { putString("artistId", artist.id) }
                findNavController().navigate(R.id.action_search_to_artist, bundle)
            },
            onPlaylistClick = { playlist ->
                val bundle = Bundle().apply { putString("playlistId", playlist.id) }
                findNavController().navigate(R.id.action_search_to_playlist, bundle)
            }
        )
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = adapter
    }

    private fun observeResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            val items = mutableListOf<SearchResultItem>()
            val filter = getCurrentFilter()

            if (filter == SearchFilter.ALL || filter == SearchFilter.TRACKS) {
                results.tracks.forEach { items.add(SearchResultItem.TrackItem(it)) }
            }
            if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
                results.albums.forEach { items.add(SearchResultItem.AlbumItem(it)) }
            }
            if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
                results.artists.forEach { items.add(SearchResultItem.ArtistItem(it)) }
            }
            if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
                results.playlists.forEach { items.add(SearchResultItem.PlaylistItem(it)) }
            }

            adapter.submitList(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { debounceHandler.removeCallbacks(it) }
        _binding = null
    }
}

enum class SearchFilter { ALL, TRACKS, ALBUMS, ARTISTS, PLAYLISTS }
