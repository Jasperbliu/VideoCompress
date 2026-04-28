package com.cloudwinbuddy.videocompress.model

import androidx.media3.common.MimeTypes

enum class VideoCodec(val displayName: String, val mimeType: String) {
    H264("H.264", MimeTypes.VIDEO_H264),
    H265("H.265 (HEVC)", MimeTypes.VIDEO_H265),
    AV1("AV1", MimeTypes.VIDEO_AV1)
}
