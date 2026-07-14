package com.iliverez.spoldify.ui.nowplaying

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.PlaybackState
import com.iliverez.spoldify.data.model.RepeatMode
import com.iliverez.spoldify.databinding.FragmentNowPlayingBinding
import com.iliverez.spoldify.util.ImageLoader
import java.util.Locale

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NowPlayingViewModel by viewModels()
    private var isSeeking = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnPlayPause.setOnClickListener {
            val state = viewModel.playbackInfo.value?.state
            if (state == PlaybackState.ERROR) {
                SpoldifyApp.instance.playerWrapper.retryAfterError()
            } else {
                viewModel.playPause()
            }
        }
        binding.btnNext.setOnClickListener { viewModel.skipNext() }
        binding.btnPrev.setOnClickListener { viewModel.skipPrevious() }
        binding.btnShuffle.setOnClickListener { viewModel.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { viewModel.cycleRepeatMode() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val progress = seekBar?.progress?.toLong() ?: return
                viewModel.seekTo(progress)
            }
        })

        viewModel.playbackInfo.observe(viewLifecycleOwner) { info ->
            val track = info.currentTrack

            binding.tvTrackTitle.text = track?.name ?: ""
            binding.tvArtistName.text = track?.artistName ?: ""
            binding.tvAlbumName.text = track?.albumName ?: ""

            ImageLoader.loadArt(requireContext(), track?.artUri, binding.ivAlbumArt, R.drawable.ic_music_placeholder)

            val isBuffering = info.state == PlaybackState.BUFFERING || info.state == PlaybackState.LOADING
            val isActive = info.state == PlaybackState.PLAYING || info.state == PlaybackState.PAUSED
            val isError = info.state == PlaybackState.ERROR

            binding.btnPlayPause.setImageResource(
                when {
                    isError -> R.drawable.ic_play
                    info.state == PlaybackState.PLAYING || info.state == PlaybackState.BUFFERING || info.state == PlaybackState.LOADING -> R.drawable.ic_pause
                    else -> R.drawable.ic_play
                }
            )
            binding.btnPlayPause.isEnabled = info.state != PlaybackState.IDLE
            binding.btnPlayPause.alpha = when {
                isError -> 1f
                isActive -> 1f
                isBuffering -> 0.5f
                else -> 0.4f
            }

            val controlsEnabled = isActive || isError
            binding.btnNext.isEnabled = controlsEnabled
            binding.btnPrev.isEnabled = controlsEnabled
            binding.btnShuffle.isEnabled = controlsEnabled
            binding.btnRepeat.isEnabled = controlsEnabled
            binding.seekBar.isEnabled = isActive

            binding.pbBuffering.visibility = if (isBuffering) View.VISIBLE else View.GONE

            val activeColor = ContextCompat.getColor(requireContext(), R.color.spotify_green)
            val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            binding.btnShuffle.setColorFilter(if (info.shuffleEnabled) activeColor else inactiveColor)

            val repeatColor = when (info.repeatMode) {
                RepeatMode.OFF -> inactiveColor
                RepeatMode.ALL, RepeatMode.ONE -> activeColor
            }
            binding.btnRepeat.setColorFilter(repeatColor)
            binding.btnRepeat.setImageResource(
                if (info.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
            )

            if (!isSeeking) {
                binding.seekBar.max = info.durationMs.toInt().coerceAtLeast(1)
                binding.seekBar.progress = info.positionMs.toInt().coerceAtMost(info.durationMs.toInt())
            }

            binding.tvCurrentTime.text = formatTime(info.positionMs)
            binding.tvDuration.text = formatTime(info.durationMs)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
