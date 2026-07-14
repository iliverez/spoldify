package com.iliverez.spoldify.player

import android.util.Log
import xyz.gianlu.librespot.audio.PlayableContentFeeder
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.TrackId
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TrackPrefetchWorker {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "track-prefetch").also { it.isDaemon = true }
    }
    private val cancelled = AtomicBoolean(false)
    private var currentGeneration = 0

    fun prefetchTracks(
        session: Session,
        trackUris: List<String>,
        currentTrackId: String
    ) {
        if (trackUris.isEmpty()) return

        cancelled.set(true)
        val generation = ++currentGeneration

        executor.execute {
            if (generation != currentGeneration) return@execute
            cancelled.set(false)
            try {
                doPrefetch(session, trackUris, cancelled)
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch failed", e)
            }
        }
    }

    private fun doPrefetch(
        session: Session,
        trackUris: List<String>,
        cancelled: AtomicBoolean
    ) {
        val toPrefetch = trackUris.take(MAX_PREFETCH)
        Log.d(TAG, "Starting sequential prefetch of ${toPrefetch.size} tracks")

        for ((index, uri) in toPrefetch.withIndex()) {
            if (cancelled.get()) break

            val trackId = extractTrackId(uri) ?: continue

            try {
                val fullyCached = prefetchSingleTrack(session, uri, trackId, cancelled)
                if (cancelled.get()) break
                if (fullyCached) {
                    Log.i(TAG, "Fully prefetched track $trackId (${index + 1}/${toPrefetch.size})")
                } else {
                    Log.i(TAG, "Partially prefetched track $trackId (${index + 1}/${toPrefetch.size})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prefetch $trackId: ${e.message}")
                if (cancelled.get()) break
            }
        }

        Log.d(TAG, "Prefetch pass completed")
    }

    private fun prefetchSingleTrack(
        session: Session,
        uri: String,
        trackId: String,
        cancelled: AtomicBoolean
    ): Boolean {
        val id = TrackId.fromUri(uri)
        val feeder = PlayableContentFeeder(session)
        val quality = getQuality()

        val qualityPicker = AudioQualityPicker { files ->
            quality.getMatches(files).firstOrNull()
        }

        val loaded = feeder.load(id, qualityPicker, true, null)
        val stream: InputStream = loaded.`in`.stream()
        var fullyRead = false

        try {
            val buffer = ByteArray(CHUNK_SIZE)
            while (true) {
                if (cancelled.get()) break
                val read = stream.read(buffer)
                if (read == -1) {
                    fullyRead = true
                    break
                }
                Thread.sleep(CHUNK_YIELD_MS)
            }
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }

        return fullyRead
    }

    fun cancel() {
        cancelled.set(true)
    }

    private fun extractTrackId(uri: String): String? {
        if (!uri.startsWith("spotify:track:")) return null
        val id = uri.removePrefix("spotify:track:")
        return id.takeIf { it.isNotEmpty() }
    }

    private fun getQuality(): AudioQuality {
        return try {
            val prefs = com.iliverez.spoldify.SpoldifyApp.instance.preferences
            when (prefs.streamingQuality) {
                com.iliverez.spoldify.data.model.AudioQuality.LOW -> AudioQuality.NORMAL
                com.iliverez.spoldify.data.model.AudioQuality.NORMAL -> AudioQuality.HIGH
                com.iliverez.spoldify.data.model.AudioQuality.HIGH -> AudioQuality.VERY_HIGH
            }
        } catch (_: Exception) {
            AudioQuality.HIGH
        }
    }

    companion object {
        private const val TAG = "TrackPrefetchWorker"
        private const val MAX_PREFETCH = 3
        private const val CHUNK_SIZE = 32 * 1024
        private const val CHUNK_YIELD_MS = 50L
    }
}
