package com.iliverez.spoldify.ui.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.databinding.FragmentPlaylistDetailBinding
import com.iliverez.spoldify.ui.common.adapters.TrackListAdapter

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlaylistDetailViewModel by viewModels()
    private lateinit var trackAdapter: TrackListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        trackAdapter = TrackListAdapter { track ->
            val tracks = viewModel.playlist.value?.tracks ?: return@TrackListAdapter
            val index = tracks.indexOfFirst { it.id == track.id }
            if (index >= 0) {
                SpoldifyApp.instance.playerRepository.playWithContext(index, tracks)
            }
        }
        binding.rvTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTracks.adapter = trackAdapter

        viewModel.playlist.observe(viewLifecycleOwner) { playlist ->
            binding.tvPlaylistName.text = playlist.name
            binding.tvOwner.text = playlist.ownerName
            binding.tvTrackCount.text = "${playlist.trackCount} tracks"
            trackAdapter.submitList(playlist.tracks)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        val playlistId = arguments?.getString("playlistId") ?: return
        viewModel.loadPlaylist(playlistId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
