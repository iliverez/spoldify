package com.iliverez.spoldify

import android.app.Application
import android.os.Build
import android.util.Log
import com.iliverez.spoldify.data.local.AppPreferences
import com.iliverez.spoldify.data.local.CredentialStorage
import com.iliverez.spoldify.data.repository.AuthRepository
import com.iliverez.spoldify.data.repository.PlayerRepository
import com.iliverez.spoldify.player.SpotifyPlayerWrapper
import com.iliverez.spoldify.zeroconf.ZeroconfManager
import xyz.gianlu.librespot.android.sink.AndroidSinkOutput
import xyz.gianlu.librespot.audio.decoders.Decoders
import xyz.gianlu.librespot.audio.format.SuperAudioFormat
import xyz.gianlu.librespot.player.PlayerConfiguration
import xyz.gianlu.librespot.player.decoders.AndroidNativeDecoder
import xyz.gianlu.librespot.player.decoders.TremoloVorbisDecoder
import com.iliverez.spoldify.data.model.AudioQuality as AppAudioQuality

class SpoldifyApp : Application() {

    val authRepository: AuthRepository by lazy { AuthRepository.getInstance(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val credentialStorage: CredentialStorage by lazy { CredentialStorage(this) }
    val playerRepository: PlayerRepository by lazy { PlayerRepository() }
    val playerWrapper: SpotifyPlayerWrapper by lazy { SpotifyPlayerWrapper() }
    val zeroconfManager: ZeroconfManager by lazy { ZeroconfManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerDecoders()
    }

    private fun registerDecoders() {
        try {
            Decoders.registerDecoder(SuperAudioFormat.VORBIS, 0, AndroidNativeDecoder::class.java)
            Decoders.registerDecoder(SuperAudioFormat.MP3, 0, AndroidNativeDecoder::class.java)
            if (isArm()) {
                Decoders.registerDecoder(SuperAudioFormat.VORBIS, 0, TremoloVorbisDecoder::class.java)
                Log.i(TAG, "Using ARM optimized Vorbis decoder")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register decoders", e)
        }
    }

    private fun isArm(): Boolean {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        return primaryAbi.contains("arm")
    }

    fun createPlayerConfiguration(): PlayerConfiguration {
        val librespotQuality = when (preferences.streamingQuality) {
            AppAudioQuality.LOW -> xyz.gianlu.librespot.audio.decoders.AudioQuality.NORMAL
            AppAudioQuality.NORMAL -> xyz.gianlu.librespot.audio.decoders.AudioQuality.HIGH
            AppAudioQuality.HIGH -> xyz.gianlu.librespot.audio.decoders.AudioQuality.VERY_HIGH
        }
        return PlayerConfiguration.Builder()
            .setOutput(PlayerConfiguration.AudioOutput.CUSTOM)
            .setOutputClass(AndroidSinkOutput::class.java.name)
            .setPreferredQuality(librespotQuality)
            .setBypassSinkVolume(preferences.normalizeVolume)
            .build()
    }

    companion object {
        private const val TAG = "SpoldifyApp"
        lateinit var instance: SpoldifyApp
            private set
    }
}
