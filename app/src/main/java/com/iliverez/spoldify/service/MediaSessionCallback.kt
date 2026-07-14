package com.iliverez.spoldify.service

import android.support.v4.media.session.MediaSessionCompat

class MediaSessionCallback : MediaSessionCompat.Callback() {

    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onSkipToNext: (() -> Unit)? = null
    var onSkipToPrevious: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null

    override fun onPlay() {
        onPlay?.invoke()
    }

    override fun onPause() {
        onPause?.invoke()
    }

    override fun onSkipToNext() {
        onSkipToNext?.invoke()
    }

    override fun onSkipToPrevious() {
        onSkipToPrevious?.invoke()
    }

    override fun onStop() {
        onStop?.invoke()
    }

    override fun onSeekTo(pos: Long) {
        onSeekTo?.invoke(pos)
    }
}
