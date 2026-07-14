package com.iliverez.spoldify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.iliverez.spoldify.R
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Artist
import com.iliverez.spoldify.data.model.Playlist
import com.iliverez.spoldify.databinding.FragmentLibraryBinding
import com.iliverez.spoldify.ui.common.adapters.AlbumGridAdapter
import com.iliverez.spoldify.ui.common.adapters.ArtistListAdapter
import com.iliverez.spoldify.ui.common.adapters.PlaylistListAdapter

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels()

    private lateinit var playlistAdapter: PlaylistListAdapter
    private lateinit var albumAdapter: AlbumGridAdapter
    private lateinit var artistAdapter: ArtistListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupTabs()
        observeViewModel()
    }

    private fun setupAdapters() {
        playlistAdapter = PlaylistListAdapter { playlist ->
            val bundle = Bundle().apply { putString("playlistId", playlist.id) }
            findNavController().navigate(R.id.action_library_to_playlist, bundle)
        }
        albumAdapter = AlbumGridAdapter { album ->
            val bundle = Bundle().apply { putString("albumId", album.id) }
            findNavController().navigate(R.id.action_library_to_album, bundle)
        }
        artistAdapter = ArtistListAdapter { artist ->
            val bundle = Bundle().apply { putString("artistId", artist.id) }
            findNavController().navigate(R.id.action_library_to_artist, bundle)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                updateContent(tab.position)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun updateContent(position: Int) {
        val library = viewModel.library.value ?: return

        val rv = binding.rvContent
        rv.layoutManager = LinearLayoutManager(requireContext())

        when (position) {
            0 -> rv.adapter = playlistAdapter.apply { submitList(library.playlists) }
            1 -> rv.adapter = albumAdapter.apply { submitList(library.savedAlbums) }
            2 -> rv.adapter = artistAdapter.apply { submitList(library.followedArtists) }
        }
    }

    private fun observeViewModel() {
        viewModel.library.observe(viewLifecycleOwner) {
            updateContent(binding.tabLayout.selectedTabPosition)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
