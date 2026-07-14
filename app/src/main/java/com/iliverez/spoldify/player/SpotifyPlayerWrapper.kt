package com.iliverez.spoldify.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.api.SpotifyApi
import com.iliverez.spoldify.data.model.PlaybackInfo
import com.iliverez.spoldify.data.model.PlaybackState
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.util.ConnectionMonitor
import xyz.gianlu.librespot.audio.MetadataWrapper
import xyz.gianlu.librespot.common.Utils
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.player.Player
import xyz.gianlu.librespot.player.PlayerConfiguration

class SpotifyPlayerWrapper {

    private val _playbackInfo = MutableLiveData(PlaybackInfo())
    val playbackInfo: LiveData<PlaybackInfo> = _playbackInfo

    private var player: Player? = null
    private var session: Session? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionUpdateRunnable: Runnable? = null
    private var hasAudioFocus = false

    private val prefetchWorker = TrackPrefetchWorker()
    private val webApi = SpotifyApi.fromApp()
    private val connectionMonitor = ConnectionMonitor(SpoldifyApp.instance)
    private var networkObserver: Observer<Boolean>? = null
    @Volatile
    private var wasConnected = true

    @Volatile
    private var isReady = false

    private var contextTracks: List<String> = emptyList()
    private var currentTrackIndex = -1
    private var lastSeedArtistId: String = ""
    private var lastSeedTrackId: String = ""
    private var lastSeedArtistName: String = ""
    @Volatile
    private var isFetchingRecommendation = false
    @Volatile
    private var pendingSeekMs: Long? = null
    private var lastSkipPreviousTimeMs: Long = 0L
    private var endCheckRunnable: Runnable? = null
    @Volatile
    private var panicRetryCount = 0
    private var panicRetryRunnable: Runnable? = null

    fun initialize(session: Session) {
        if (player != null) {
            release()
        }
        this.session = session

        Thread {
            try {
                val config = SpoldifyApp.instance.createPlayerConfiguration()
                val p = Player(config, session)
                p.addEventsListener(object : Player.EventsListener {
                    override fun onContextChanged(p: Player, uri: String) {
                        Log.d(TAG, "Context changed: $uri")
                    }
                    override fun onTrackChanged(p: Player, id: PlayableId, meta: MetadataWrapper?, autoPlay: Boolean) {
                        Log.i(TAG, "Track changed, autoPlay=$autoPlay")
                        endCheckRunnable?.let { handler.removeCallbacks(it); endCheckRunnable = null }
                        panicRetryRunnable?.let { handler.removeCallbacks(it); panicRetryRunnable = null }
                        panicRetryCount = 0
                        if (meta != null && meta.isTrack) {
                            val track = metaToTrack(meta)
                            lastSeedArtistId = track.artistId
                            lastSeedTrackId = track.id
                            lastSeedArtistName = track.artistName
                            updateCurrentTrackIndex(track.id)
                            triggerPrefetch()
                            handler.post {
                                stopPositionUpdates()
                                requestAudioFocus()
                                _playbackInfo.value = PlaybackInfo(
                                    state = PlaybackState.PLAYING,
                                    currentTrack = track,
                                    positionMs = 0L,
                                    durationMs = meta.duration().toLong(),
                                    shuffleEnabled = _playbackInfo.value?.shuffleEnabled ?: false,
                                    repeatMode = _playbackInfo.value?.repeatMode ?: com.iliverez.spoldify.data.model.RepeatMode.OFF
                                )
                                startPositionUpdates()
                            }
                        }
                    }
                    override fun onPlaybackEnded(p: Player) {
                        Log.i(TAG, "Playback ended, will auto-play next")
                        handler.post { stopPositionUpdates() }
                        val check = Runnable {
                            val state = _playbackInfo.value?.state
                            if (state != PlaybackState.PLAYING && state != PlaybackState.LOADING && state != PlaybackState.BUFFERING) {
                                Log.i(TAG, "Auto-playing next track")
                                skipNext()
                            }
                        }
                        endCheckRunnable = check
                        handler.postDelayed(check, END_CHECK_DELAY_MS)
                    }
                    override fun onPlaybackPaused(p: Player, pos: Long) {
                        Log.i(TAG, "Playback paused at ${pos}ms")
                        handler.post {
                            stopPositionUpdates()
                            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.PAUSED, positionMs = pos)
                        }
                    }
                    override fun onPlaybackResumed(p: Player, pos: Long) {
                        Log.i(TAG, "Playback resumed at ${pos}ms")
                        requestAudioFocus()
                        panicRetryCount = 0
                        panicRetryRunnable?.let { handler.removeCallbacks(it); panicRetryRunnable = null }
                        handler.post {
                            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.PLAYING, positionMs = pos)
                            startPositionUpdates()
                        }
                    }
                    override fun onPlaybackFailed(p: Player, e: Exception) {
                        Log.e(TAG, "Playback failed: ${e.javaClass.simpleName}: ${e.message}", e)
                        abandonAudioFocus()
                        handler.post {
                            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.ERROR)
                        }
                    }
                    override fun onTrackSeeked(p: Player, pos: Long) {
                        handler.post {
                            _playbackInfo.value = _playbackInfo.value?.copy(positionMs = pos, state = PlaybackState.PLAYING)
                            startPositionUpdates()
                        }
                    }
                    override fun onMetadataAvailable(p: Player, meta: MetadataWrapper) {
                        if (meta.isTrack) {
                            val track = metaToTrack(meta)
                            Log.d(TAG, "Metadata available: ${track.name} by ${track.artistName}")
                            handler.post {
                                _playbackInfo.value = _playbackInfo.value?.copy(
                                    currentTrack = track,
                                    durationMs = meta.duration().toLong()
                                )
                            }
                        }
                    }
                    override fun onPlaybackHaltStateChanged(p: Player, halted: Boolean, pos: Long) {
                        Log.d(TAG, "Halt state changed: halted=$halted pos=$pos")
                        handler.post {
                            if (halted) {
                                _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.BUFFERING, positionMs = pos)
                            } else {
                                _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.PLAYING, positionMs = pos)
                                startPositionUpdates()
                            }
                        }
                    }
                    override fun onInactiveSession(p: Player, timeout: Boolean) {
                        Log.w(TAG, "Session inactive, timeout=$timeout")
                    }
                    override fun onVolumeChanged(p: Player, volume: Float) {}
                    override fun onStartedLoading(p: Player) {
                        Log.i(TAG, "Started loading track")
                        requestAudioFocus()
                        handler.post {
                            val current = _playbackInfo.value ?: return@post
                            if (current.state != PlaybackState.PLAYING) {
                                _playbackInfo.value = current.copy(state = PlaybackState.LOADING)
                            }
                        }
                    }
                    override fun onFinishedLoading(p: Player) {
                        Log.i(TAG, "Finished loading track")
                        panicRetryCount = 0
                        panicRetryRunnable?.let { handler.removeCallbacks(it); panicRetryRunnable = null }
                        val seek = pendingSeekMs
                        if (seek != null) {
                            pendingSeekMs = null
                            Thread { p.seek(seek.toInt()) }.start()
                        }
                        handler.post {
                            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.PLAYING)
                            startPositionUpdates()
                        }
                    }
                    override fun onPanicState(p: Player) {
                        Log.e(TAG, "Player entered panic state! Retry $panicRetryCount/$MAX_PANIC_RETRIES")
                        handler.post {
                            stopPositionUpdates()
                            if (panicRetryCount < MAX_PANIC_RETRIES) {
                                panicRetryCount++
                                _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.BUFFERING)
                                val retry = Runnable {
                                    val p2 = player ?: return@Runnable
                                    Log.i(TAG, "Retrying playback after panic (attempt $panicRetryCount)")
                                    try {
                                        p2.play()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Panic retry play failed, trying load", e)
                                        val uri = _playbackInfo.value?.currentTrack?.let {
                                            "spotify:track:${it.id}"
                                        }
                                        if (uri != null) {
                                            Thread { p2.load(uri, true, false) }.start()
                                        }
                                    }
                                }
                                panicRetryRunnable = retry
                                handler.postDelayed(retry, PANIC_RETRY_DELAY_MS)
                            } else {
                                Log.e(TAG, "Panic recovery exhausted, setting ERROR state")
                                panicRetryCount = 0
                                _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.ERROR)
                            }
                        }
                    }
                })

                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
                    while (!p.isReady) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) { return@Thread }
                    }
                } else {
                    p.waitReady()
                }

                player = p
                isReady = true
                handler.post {
                    Log.i(TAG, "Player initialized and ready")
                    setupNetworkObserver()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize player", e)
            }
        }.start()
    }

    fun play(uri: String) {
        contextTracks = emptyList()
        currentTrackIndex = -1
        prefetchWorker.cancel()
        val p = player ?: return
        requestAudioFocus()
        Thread { p.load(uri, true, false) }.start()
        _playbackInfo.postValue(_playbackInfo.value?.copy(state = PlaybackState.LOADING))
    }

    fun playWithContext(firstTrackUri: String, queueUris: List<String>) {
        val allUris = listOf(firstTrackUri) + queueUris
        contextTracks = allUris
        currentTrackIndex = 0

        val shuffle = _playbackInfo.value?.shuffleEnabled ?: false
        val orderedQueue = if (shuffle) queueUris.shuffled() else queueUris

        val p = player ?: return
        requestAudioFocus()
        Thread {
            p.load(firstTrackUri, true, false)
            for (uri in orderedQueue) {
                try {
                    p.addToQueue(uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add to queue: $uri", e)
                }
            }
            triggerPrefetch()
        }.start()
        _playbackInfo.postValue(_playbackInfo.value?.copy(state = PlaybackState.LOADING))
    }

    fun resume() {
        val p = player ?: return
        requestAudioFocus()
        Thread { p.play() }.start()
    }

    fun pause() {
        val p = player ?: return
        Thread { p.pause() }.start()
        stopPositionUpdates()
    }

    fun retryAfterError() {
        val p = player ?: return
        val info = _playbackInfo.value ?: return
        if (info.state != PlaybackState.ERROR) return
        val uri = info.currentTrack?.let { "spotify:track:${it.id}" } ?: return
        panicRetryCount = 0
        requestAudioFocus()
        _playbackInfo.value = info.copy(state = PlaybackState.LOADING)
        Thread { p.load(uri, true, false) }.start()
    }

    fun skipNext() {
        val nextIndex = currentTrackIndex + 1
        if (contextTracks.isNotEmpty() && currentTrackIndex >= 0 && nextIndex < contextTracks.size) {
            val nextUri = contextTracks[nextIndex]
            val p = player ?: return
            requestAudioFocus()
            stopPositionUpdates()
            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.LOADING)
            Thread {
                try {
                    p.load(nextUri, true, false)
                    for (i in nextIndex + 1 until contextTracks.size) {
                        try { p.addToQueue(contextTracks[i]) }
                        catch (e: Exception) { Log.w(TAG, "Failed to add to queue", e) }
                    }
                    triggerPrefetch()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to skip next", e)
                }
            }.start()
        } else {
            playRandomRecommendation()
        }
    }

    private fun playRandomRecommendation() {
        if (isFetchingRecommendation) {
            Log.w(TAG, "Already fetching a recommendation, skipping")
            return
        }
        val currentInfo = _playbackInfo.value
        val seedArtist = currentInfo?.currentTrack?.artistId?.ifBlank { null }
            ?: lastSeedArtistId.ifBlank { null }
        val seedTrack = currentInfo?.currentTrack?.id?.ifBlank { null }
            ?: lastSeedTrackId.ifBlank { null }
        val artistName = currentInfo?.currentTrack?.artistName?.ifBlank { null }
            ?: lastSeedArtistName.ifBlank { null }

        if (seedArtist == null && seedTrack == null && artistName == null) {
            Log.w(TAG, "No seed info available for recommendations")
            return
        }

        isFetchingRecommendation = true
        requestAudioFocus()
        stopPositionUpdates()
        _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.LOADING)
        Log.i(TAG, "Fetching recommendation: seedArtist=$seedArtist, seedTrack=$seedTrack, artistName=$artistName")

        Thread {
            try {
                var chosenId: String? = null

                for (attempt in 1..MAX_RECOMMENDATION_RETRIES) {
                    chosenId = fetchRecommendationId(seedArtist, seedTrack, artistName, currentInfo?.currentTrack?.id ?: lastSeedTrackId)
                    if (chosenId != null) break
                    if (attempt < MAX_RECOMMENDATION_RETRIES) {
                        val delay = RECOMMENDATION_RETRY_BASE_MS * (1L shl (attempt - 1))
                        Log.w(TAG, "Recommendation attempt $attempt failed, retrying in ${delay}ms")
                        Thread.sleep(delay)
                    }
                }

                if (chosenId != null) {
                    contextTracks = emptyList()
                    currentTrackIndex = -1
                    val p = player
                    if (p != null) {
                        p.load("spotify:track:$chosenId", true, false)
                    } else {
                        handler.post {
                            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.IDLE)
                        }
                    }
                } else {
                    Log.w(TAG, "All recommendation attempts failed")
                    handler.post {
                        _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.IDLE)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch recommendation", e)
                handler.post {
                    _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.IDLE)
                }
            } finally {
                isFetchingRecommendation = false
            }
        }.start()
    }

    private fun fetchRecommendationId(
        seedArtist: String?,
        seedTrack: String?,
        artistName: String?,
        currentTrackId: String
    ): String? {
        if (seedArtist != null || seedTrack != null) {
            try {
                val json = webApi.getRecommendations(
                    seedArtists = seedArtist,
                    seedTracks = seedTrack,
                    limit = 20
                )
                val tracksArray = json.getAsJsonArray("tracks")
                val candidates = tracksArray?.filter { element ->
                    val id = element.asJsonObject.getAsJsonPrimitive("id")?.asString
                    id != null && id != currentTrackId
                } ?: emptyList()
                val chosen = candidates.randomOrNull()?.asJsonObject
                    ?.getAsJsonPrimitive("id")?.asString
                if (chosen != null) {
                    Log.i(TAG, "Recommendations API returned ${tracksArray?.size() ?: 0} tracks, chosen: $chosen")
                    return chosen
                }
            } catch (e: Exception) {
                Log.w(TAG, "Recommendations API failed, trying search fallback", e)
            }
        }

        if (artistName != null) {
            try {
                val json = webApi.search("artist:$artistName", "track", 10)
                val items = json.getAsJsonObject("tracks")?.getAsJsonArray("items")
                val candidates = items?.filter { element ->
                    val id = element.asJsonObject.getAsJsonPrimitive("id")?.asString
                    id != null && id != currentTrackId
                } ?: emptyList()
                val chosen = candidates.randomOrNull()?.asJsonObject
                    ?.getAsJsonPrimitive("id")?.asString
                if (chosen != null) {
                    Log.i(TAG, "Search fallback returned ${items?.size() ?: 0} tracks, chosen: $chosen")
                    return chosen
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search fallback also failed", e)
            }
        }

        return null
    }

    fun skipPrevious() {
        val p = player ?: return
        val now = System.currentTimeMillis()

        if (now - lastSkipPreviousTimeMs < 2000 && currentTrackIndex > 0) {
            lastSkipPreviousTimeMs = 0L
            val prevIndex = currentTrackIndex - 1
            val prevUri = contextTracks[prevIndex]
            requestAudioFocus()
            stopPositionUpdates()
            _playbackInfo.value = _playbackInfo.value?.copy(state = PlaybackState.LOADING)
            Thread {
                try {
                    p.load(prevUri, true, false)
                    for (i in prevIndex + 1 until contextTracks.size) {
                        try { p.addToQueue(contextTracks[i]) }
                        catch (e: Exception) { Log.w(TAG, "Failed to add to queue", e) }
                    }
                    triggerPrefetch()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load previous track", e)
                }
            }.start()
        } else {
            lastSkipPreviousTimeMs = now
            seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        _playbackInfo.value = _playbackInfo.value?.copy(positionMs = positionMs, state = PlaybackState.PLAYING)
        startPositionUpdates()
        Thread {
            try { p.seek(positionMs.toInt()) }
            catch (e: Exception) { Log.e(TAG, "Failed to seek", e) }
        }.start()
    }

    fun setShuffle(enabled: Boolean) {
        val info = _playbackInfo.value ?: return
        _playbackInfo.postValue(info.copy(shuffleEnabled = enabled))

        if (contextTracks.isEmpty() || currentTrackIndex < 0) return

        val currentUri = contextTracks[currentTrackIndex]
        val currentPosition = info.positionMs
        val remainingOriginal = contextTracks.subList(currentTrackIndex + 1, contextTracks.size)

        val orderedQueue = if (enabled) remainingOriginal.shuffled() else remainingOriginal.toList()

        prefetchWorker.cancel()

        val p = player ?: return
        pendingSeekMs = currentPosition
        requestAudioFocus()
        Thread {
            p.load(currentUri, true, false)
            for (uri in orderedQueue) {
                try {
                    p.addToQueue(uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add to queue: $uri", e)
                }
            }
            triggerPrefetch()
        }.start()
        _playbackInfo.postValue(info.copy(state = PlaybackState.LOADING, shuffleEnabled = enabled))
    }

    private fun setupNetworkObserver() {
        networkObserver?.let { connectionMonitor.isConnected.removeObserver(it) }
        networkObserver = Observer { connected ->
            val wasDisconnected = wasConnected && !connected
            wasConnected = connected
            if (wasDisconnected) {
                Log.i(TAG, "Network lost during playback")
            }
            if (connected && !wasDisconnected) {
                val state = _playbackInfo.value?.state
                if (state == PlaybackState.ERROR) {
                    Log.i(TAG, "Network restored, retrying after error")
                    retryAfterError()
                }
            }
        }
        connectionMonitor.isConnected.observeForever(networkObserver!!)
    }

    fun release() {
        stopPositionUpdates()
        endCheckRunnable?.let { handler.removeCallbacks(it); endCheckRunnable = null }
        panicRetryRunnable?.let { handler.removeCallbacks(it); panicRetryRunnable = null }
        networkObserver?.let { connectionMonitor.isConnected.removeObserver(it); networkObserver = null }
        abandonAudioFocus()
        contextTracks = emptyList()
        currentTrackIndex = -1
        pendingSeekMs = null
        isFetchingRecommendation = false
        panicRetryCount = 0
        lastSeedArtistId = ""
        lastSeedTrackId = ""
        lastSeedArtistName = ""
        prefetchWorker.cancel()
        val p = player ?: return
        player = null
        isReady = false
        Thread {
            try {
                p.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing player", e)
            }
        }.start()
        _playbackInfo.postValue(PlaybackInfo())
    }

    private fun updateCurrentTrackIndex(trackId: String) {
        if (contextTracks.isEmpty()) return
        val uri = "spotify:track:$trackId"
        val idx = contextTracks.indexOf(uri)
        if (idx >= 0) {
            currentTrackIndex = idx
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val am = SpoldifyApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val result = am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "Audio focus request: $hasAudioFocus")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        try {
            val am = SpoldifyApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocus(null)
            hasAudioFocus = false
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus", e)
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateRunnable = object : Runnable {
            override fun run() {
                val current = _playbackInfo.value ?: return
                if (current.state == PlaybackState.PLAYING && current.positionMs < current.durationMs) {
                    _playbackInfo.value = current.copy(positionMs = current.positionMs + 1000)
                }
                positionUpdateRunnable?.let { handler.postDelayed(it, 1000) }
            }
        }
        handler.postDelayed(positionUpdateRunnable!!, 1000)
    }

    private fun stopPositionUpdates() {
        positionUpdateRunnable?.let { handler.removeCallbacks(it) }
        positionUpdateRunnable = null
    }

    private fun metaToTrack(meta: MetadataWrapper): Track {
        val track = meta.track
        val firstArtist = if (track.artistCount > 0) track.getArtist(0) else null
        val album = if (track.hasAlbum()) track.album else null
        val coverHex = album?.coverGroup?.imageList?.maxByOrNull { it.size.number }?.fileId
            ?.let { Utils.bytesToHex(it) }

        return Track(
            id = String(PlayableId.BASE62.encode(track.gid.toByteArray(), 22)),
            name = meta.name,
            artistName = firstArtist?.name ?: "Unknown",
            albumName = meta.albumName ?: "",
            albumId = if (album != null) String(PlayableId.BASE62.encode(album.gid.toByteArray(), 22)) else "",
            artistId = if (firstArtist != null) String(PlayableId.BASE62.encode(firstArtist.gid.toByteArray(), 22)) else "",
            durationMs = meta.duration().toLong(),
            artUri = coverHex?.let { "https://i.scdn.co/image/${it.lowercase()}" }
        )
    }

    private fun triggerPrefetch() {
        val sess = session ?: return
        if (contextTracks.isEmpty() || currentTrackIndex < 0) return
        val currentTrackId = _playbackInfo.value?.currentTrack?.id ?: return
        val upcoming = contextTracks.subList(
            currentTrackIndex + 1,
            minOf(currentTrackIndex + 1 + MAX_PREFETCH, contextTracks.size)
        )
        if (upcoming.isEmpty()) return
        prefetchWorker.prefetchTracks(sess, upcoming, currentTrackId)
    }

    companion object {
        private const val TAG = "SpotifyPlayerWrapper"
        private const val MAX_PREFETCH = 3
        private const val END_CHECK_DELAY_MS = 500L
        private const val MAX_PANIC_RETRIES = 2
        private const val PANIC_RETRY_DELAY_MS = 2000L
        private const val MAX_RECOMMENDATION_RETRIES = 3
        private const val RECOMMENDATION_RETRY_BASE_MS = 3000L
    }
}
