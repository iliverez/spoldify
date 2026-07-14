package com.iliverez.spoldify.ui.nowplaying

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.PlaybackInfo
import com.iliverez.spoldify.data.model.PlaybackState
import com.iliverez.spoldify.data.repository.PlayerRepository

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {

    private val playerRepository = PlayerRepository()

    val playbackInfo: LiveData<PlaybackInfo> get() = SpoldifyApp.instance.playerWrapper.playbackInfo

    fun playPause() {
        val info = playbackInfo.value ?: return
        if (info.state == PlaybackState.PLAYING) {
            SpoldifyApp.instance.playerWrapper.pause()
        } else {
            SpoldifyApp.instance.playerWrapper.resume()
        }
    }

    fun skipNext() = SpoldifyApp.instance.playerWrapper.skipNext()
    fun skipPrevious() = SpoldifyApp.instance.playerWrapper.skipPrevious()
    fun seekTo(positionMs: Long) = SpoldifyApp.instance.playerWrapper.seekTo(positionMs)
    fun toggleShuffle() = playerRepository.toggleShuffle()
    fun cycleRepeatMode() = playerRepository.cycleRepeatMode()
}
