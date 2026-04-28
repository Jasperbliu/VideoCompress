package com.cloudwinbuddy.videocompress.util

import android.content.Context
import android.os.StatFs
import com.cloudwinbuddy.videocompress.model.CompressParams
import com.cloudwinbuddy.videocompress.model.VideoMetadata

object StorageEstimator {
    fun estimateOutputBytes(metadata: VideoMetadata, params: CompressParams): Long {
        val seconds = metadata.durationMs.coerceAtLeast(1L) / 1000.0
        val totalBitrate = params.videoBitrate + params.audioBitrate
        return ((totalBitrate / 8.0) * seconds).toLong()
    }

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val stat = StatFs(context.cacheDir.absolutePath)
        return stat.availableBytes > requiredBytes + 30L * 1024L * 1024L
    }
}
