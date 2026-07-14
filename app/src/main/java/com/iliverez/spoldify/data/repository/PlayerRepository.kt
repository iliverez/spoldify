package com.iliverez.spoldify.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.PlaybackInfo
import com.iliverez.spoldify.data.model.PlaybackState
import com.iliverez.spoldify.data.model.RepeatMode
import com.iliverez.spoldify.data.model.Track

class PlayerRepository {

    private val wrapper get() = SpoldifyApp.instance.playerWrapper

    val playbackInfo: LiveData<PlaybackInfo> get() = wrapper.playbackInfo

    fun play(uri: String) = wrapper.play(uri)
    fun playWithContext(trackIndex: Int, tracks: List<Track>) {
        if (trackIndex < 0 || trackIndex >= tracks.size) return
        val firstUri = "spotify:track:${tracks[trackIndex].id}"
        val queueUris = tracks.subList(trackIndex + 1, tracks.size).map { "spotify:track:${it.id}" }
        wrapper.playWithContext(firstUri, queueUris)
    }
    fun resume() = wrapper.resume()
    fun pause() = wrapper.pause()
    fun skipNext() = wrapper.skipNext()
    fun skipPrevious() = wrapper.skipPrevious()

    fun updatePlaybackState(state: PlaybackState) {
        val current = wrapper.playbackInfo.value ?: return
        (wrapper.playbackInfo as? MutableLiveData)?.value = current.copy(state = state)
    }

    fun toggleShuffle() {
        val current = wrapper.playbackInfo.value ?: return
        wrapper.setShuffle(!current.shuffleEnabled)
    }

    fun cycleRepeatMode() {
        val current = wrapper.playbackInfo.value ?: return
        val modes = RepeatMode.entries
        val nextIndex = (modes.indexOf(current.repeatMode) + 1) % modes.size
        (wrapper.playbackInfo as? MutableLiveData)?.value = current.copy(repeatMode = modes[nextIndex])
    }
}
