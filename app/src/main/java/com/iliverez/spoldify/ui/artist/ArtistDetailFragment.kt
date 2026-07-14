package com.iliverez.spoldify.ui.artist

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
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.databinding.FragmentArtistDetailBinding
import com.iliverez.spoldify.ui.common.adapters.AlbumGridAdapter
import com.iliverez.spoldify.ui.common.adapters.TrackListAdapter

class ArtistDetailFragment : Fragment() {

    private var _binding: FragmentArtistDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArtistDetailViewModel by viewModels()
    private lateinit var topTracksAdapter: TrackListAdapter
    private lateinit var albumsAdapter: AlbumGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArtistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topTracksAdapter = TrackListAdapter { track ->
            val tracks = viewModel.artist.value?.topTracks ?: return@TrackListAdapter
            val index = tracks.indexOfFirst { it.id == track.id }
            if (index >= 0) {
                SpoldifyApp.instance.playerRepository.playWithContext(index, tracks)
            }
        }
        binding.rvTopTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTopTracks.adapter = topTracksAdapter

        albumsAdapter = AlbumGridAdapter { album ->
            val bundle = Bundle().apply { putString("albumId", album.id) }
            findNavController().navigate(R.id.action_artist_to_album, bundle)
        }
        binding.rvAlbums.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvAlbums.adapter = albumsAdapter

        viewModel.artist.observe(viewLifecycleOwner) { artist ->
            binding.tvArtistName.text = artist.name
            topTracksAdapter.submitList(artist.topTracks)
            albumsAdapter.submitList(artist.albums)
            binding.btnShowAllAlbums.visibility = if (artist.albums.size >= 10) View.GONE else View.VISIBLE
        }

        viewModel.loadingMoreAlbums.observe(viewLifecycleOwner) { loading ->
            binding.btnShowAllAlbums.text = if (loading) "Loading..." else "Show all albums"
            binding.btnShowAllAlbums.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        val artistId = arguments?.getString("artistId") ?: return
        viewModel.loadArtist(artistId)

        binding.btnShowAllAlbums.setOnClickListener {
            viewModel.loadAllAlbums(artistId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
