package com.iliverez.spoldify.data.local

import android.content.Context
import java.io.File

class DownloadManager(private val context: Context) {

    fun getDownloadDir(): File {
        val prefs = AppPreferences(context)
        val dir = File(prefs.storagePath)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getUsedSpaceBytes(): Long {
        return getDownloadDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getUsedSpaceMb(): Long = getUsedSpaceBytes() / (1024 * 1024)

    fun isTrackDownloaded(trackId: String): Boolean {
        return File(getDownloadDir(), "$trackId.ogg").exists()
    }

    fun getTrackFile(trackId: String): File {
        return File(getDownloadDir(), "$trackId.ogg")
    }

    fun deleteTrack(trackId: String): Boolean {
        val file = getTrackFile(trackId)
        return file.delete()
    }

    fun enforceStorageLimit() {
        val prefs = AppPreferences(context)
        val maxBytes = prefs.maxStorageGb.toLong() * 1024 * 1024 * 1024
        val used = getUsedSpaceBytes()
        if (used <= maxBytes) return

        val files = getDownloadDir().listFiles()
            ?.sortedBy { it.lastModified() }
            ?: return

        var freed = 0L
        for (file in files) {
            if (used - freed <= maxBytes) break
            freed += file.length()
            file.delete()
        }
    }
}
