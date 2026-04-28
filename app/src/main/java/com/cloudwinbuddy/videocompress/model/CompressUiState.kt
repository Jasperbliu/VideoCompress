package com.cloudwinbuddy.videocompress.model

import android.net.Uri

data class VideoItem(
    val uri: Uri,
    val metadata: VideoMetadata? = null,
    val progress: Float = 0f,
    val outputUri: Uri? = null,
    val outputSizeBytes: Long = 0L,
    val fellBackToOriginal: Boolean = false,
    val isProcessing: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

data class CompressUiState(
    val videos: List<VideoItem> = emptyList(),
    val currentParams: CompressParams = com.cloudwinbuddy.videocompress.compress.CompressPresets.WECHAT_COMPACT,
    val isCompressing: Boolean = false,
    val statusMessage: String? = null,
    val supportedCodecs: List<VideoCodec> = listOf(VideoCodec.H264),
) {
    val canShareAll: Boolean get() = videos.any { it.outputUri != null }
    val totalProgress: Float
        get() = if (videos.isEmpty()) 0f else videos.sumOf { it.progress.toDouble() }.toFloat() / videos.size
}
