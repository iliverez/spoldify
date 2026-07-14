package com.iliverez.spoldify.ui.album

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
import com.iliverez.spoldify.databinding.FragmentAlbumDetailBinding
import com.iliverez.spoldify.ui.common.adapters.TrackListAdapter

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumDetailViewModel by viewModels()
    private lateinit var trackAdapter: TrackListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        trackAdapter = TrackListAdapter { track ->
            val tracks = viewModel.album.value?.tracks ?: return@TrackListAdapter
            val index = tracks.indexOfFirst { it.id == track.id }
            if (index >= 0) {
                SpoldifyApp.instance.playerRepository.playWithContext(index, tracks)
            }
        }
        binding.rvTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTracks.adapter = trackAdapter

        viewModel.album.observe(viewLifecycleOwner) { album ->
            binding.tvAlbumName.text = album.name
            binding.tvArtistName.text = album.artistName
            binding.tvArtistName.setOnClickListener {
                val bundle = Bundle().apply { putString("artistId", album.artistId) }
                findNavController().navigate(R.id.action_album_to_artist, bundle)
            }
            binding.tvYear.text = album.year ?: ""
            trackAdapter.submitList(album.tracks)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        val albumId = arguments?.getString("albumId") ?: return
        viewModel.loadAlbum(albumId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
