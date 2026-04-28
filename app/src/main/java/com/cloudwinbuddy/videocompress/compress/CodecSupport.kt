package com.cloudwinbuddy.videocompress.compress

import android.media.MediaCodecList
import com.cloudwinbuddy.videocompress.model.VideoCodec

object CodecSupport {
    fun supportedEncoders(): Set<VideoCodec> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = mutableSetOf<VideoCodec>()
        codecs.add(VideoCodec.H264) // 绝大多数设备支持

        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                when {
                    type.equals("video/hevc", ignoreCase = true) -> codecs.add(VideoCodec.H265)
                    type.equals("video/av01", ignoreCase = true) -> codecs.add(VideoCodec.AV1)
                }
            }
        }
        return codecs
    }

    fun isSupported(codec: VideoCodec): Boolean {
        return supportedEncoders().contains(codec)
    }
}
