package com.iliverez.spoldify.data.local

import android.content.Context
import android.content.SharedPreferences
import com.iliverez.spoldify.data.model.AudioQuality

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spoldify_prefs", Context.MODE_PRIVATE)

    var streamingQuality: AudioQuality
        get() = AudioQuality.entries.firstOrNull {
            it.name == prefs.getString(KEY_STREAMING_QUALITY, null)
        } ?: AudioQuality.NORMAL
        set(value) = prefs.edit().putString(KEY_STREAMING_QUALITY, value.name).apply()

    var downloadQuality: AudioQuality
        get() = AudioQuality.entries.firstOrNull {
            it.name == prefs.getString(KEY_DOWNLOAD_QUALITY, null)
        } ?: AudioQuality.HIGH
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_QUALITY, value.name).apply()

    var downloadWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_DOWNLOAD_WIFI_ONLY, value).apply()

    var offlineMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    var normalizeVolume: Boolean
        get() = prefs.getBoolean(KEY_NORMALIZE_VOLUME, true)
        set(value) = prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, value).apply()

    var maxStorageGb: Int
        get() = prefs.getInt(KEY_MAX_STORAGE_GB, 5)
        set(value) = prefs.edit().putInt(KEY_MAX_STORAGE_GB, value).apply()

    var storagePath: String
        get() = prefs.getString(KEY_STORAGE_PATH, null)
            ?: context.filesDir.resolve("downloads").absolutePath
        set(value) = prefs.edit().putString(KEY_STORAGE_PATH, value).apply()

    companion object {
        private const val KEY_STREAMING_QUALITY = "streaming_quality"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        private const val KEY_MAX_STORAGE_GB = "max_storage_gb"
        private const val KEY_STORAGE_PATH = "storage_path"
    }
}
