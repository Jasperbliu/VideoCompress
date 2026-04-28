package com.cloudwinbuddy.videocompress.model

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrate: Long,
    val sizeBytes: Long
)
