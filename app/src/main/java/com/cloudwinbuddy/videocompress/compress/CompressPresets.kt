package com.cloudwinbuddy.videocompress.compress

import com.cloudwinbuddy.videocompress.model.CompressParams

/**
 * 预设参数说明（参考微信）：
 *  - maxWidth / maxHeight 不再是"硬目标分辨率"，而是 SmartParams 计算出来的最终值；
 *    预设里只填一个"短边目标"（保存在 maxHeight 字段里以兼容现有数据结构，
 *    但 SmartParams.adapt() 会按"短边对齐"策略重新计算实际宽高，并对齐到 16）。
 *  - videoBitrate 在预设中只是"上限保险"，实际码率由 SmartParams 按
 *    像素 × fps × bppFactor 动态计算（微信策略）。
 */
object CompressPresets {

    // 朋友圈级别：短边 544，对应 ~1.6 Mbps（30fps）
    val WECHAT_COMPACT = CompressParams(
        maxWidth = 0,            // 由 SmartParams 计算
        maxHeight = 544,         // 短边目标
        videoBitrate = 2_500_000, // 上限
        frameRate = 30,
        audioBitrate = 64_000,
        presetName = "朋友圈-体积优先"
    )

    // 高清模式：短边 720，对应 ~3.0 Mbps（30fps）
    val WECHAT_QUALITY = CompressParams(
        maxWidth = 0,
        maxHeight = 720,
        videoBitrate = 4_500_000,
        frameRate = 30,
        audioBitrate = 96_000,
        presetName = "朋友圈-画质优先"
    )

    // 极限压缩：短边 432，对应 ~1.0 Mbps（24fps）
    val MIN_SIZE = CompressParams(
        maxWidth = 0,
        maxHeight = 432,
        videoBitrate = 1_200_000,
        frameRate = 24,
        audioBitrate = 48_000,
        presetName = "极限压缩"
    )

    val ALL = listOf(WECHAT_COMPACT, WECHAT_QUALITY, MIN_SIZE)
}
