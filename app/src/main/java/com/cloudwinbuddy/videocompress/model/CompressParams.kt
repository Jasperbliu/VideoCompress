package com.cloudwinbuddy.videocompress.model

data class CompressParams(
    val maxWidth: Int,
    val maxHeight: Int,
    val videoBitrate: Int,
    val frameRate: Int,
    val audioBitrate: Int,
    val presetName: String,
    val videoCodec: VideoCodec = VideoCodec.H265,
)
