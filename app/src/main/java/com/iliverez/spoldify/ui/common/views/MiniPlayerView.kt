package com.iliverez.spoldify.ui.common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.PlaybackState
import com.iliverez.spoldify.util.ImageLoader

class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val ivArt: ImageView
    private val tvTrackName: TextView
    private val tvArtistName: TextView
    private val btnPlayPause: ImageButton

    private var playbackObserver: Observer<com.iliverez.spoldify.data.model.PlaybackInfo>? = null

    init {
        inflate(context, R.layout.view_mini_player, this)
        ivArt = findViewById(R.id.ivArt)
        tvTrackName = findViewById(R.id.tvTrackName)
        tvArtistName = findViewById(R.id.tvArtistName)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        btnPlayPause.setOnClickListener {
            val info = SpoldifyApp.instance.playerWrapper.playbackInfo.value
            when (info?.state) {
                PlaybackState.ERROR -> SpoldifyApp.instance.playerWrapper.retryAfterError()
                PlaybackState.PLAYING -> SpoldifyApp.instance.playerWrapper.pause()
                else -> SpoldifyApp.instance.playerWrapper.resume()
            }
        }
    }

    fun observePlayback(lifecycleOwner: LifecycleOwner) {
        playbackObserver = Observer { info ->
            val track = info.currentTrack
            tvTrackName.text = track?.name ?: ""
            tvArtistName.text = track?.artistName ?: ""
            ImageLoader.loadArtThumbnail(context, track?.artUri, ivArt, R.drawable.ic_music_placeholder)

            btnPlayPause.setImageResource(
                when (info.state) {
                    PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.LOADING -> R.drawable.ic_pause
                    else -> R.drawable.ic_play
                }
            )
            btnPlayPause.alpha = when (info.state) {
                PlaybackState.PLAYING, PlaybackState.PAUSED, PlaybackState.ERROR -> 1f
                PlaybackState.BUFFERING, PlaybackState.LOADING -> 0.5f
                else -> 0.4f
            }
        }
        SpoldifyApp.instance.playerWrapper.playbackInfo.observe(lifecycleOwner, playbackObserver!!)
    }

    fun stopObserving(lifecycleOwner: LifecycleOwner) {
        playbackObserver?.let {
            SpoldifyApp.instance.playerWrapper.playbackInfo.removeObserver(it)
        }
    }
}
