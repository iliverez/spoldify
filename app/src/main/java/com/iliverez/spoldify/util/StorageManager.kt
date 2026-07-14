package com.iliverez.spoldify.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.text.DecimalFormat

object StorageManager {

    fun getInternalStorageStats(context: Context): StorageStats {
        val stat = StatFs(context.filesDir.absolutePath)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        return StorageStats(available, total)
    }

    fun getExternalStorageStats(): StorageStats? {
        val external = Environment.getExternalStorageDirectory()
        if (!external.exists()) return null
        val stat = StatFs(external.absolutePath)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        return StorageStats(available, total)
    }

    fun formatSize(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1073741824 -> "${df.format(bytes.toDouble() / 1073741824)} GB"
            bytes >= 1048576 -> "${df.format(bytes.toDouble() / 1048576)} MB"
            bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
            else -> "$bytes B"
        }
    }

    data class StorageStats(val availableBytes: Long, val totalBytes: Long)
}
