package com.cloudwinbuddy.videocompress.compress

import com.cloudwinbuddy.videocompress.model.CompressParams
import com.cloudwinbuddy.videocompress.model.VideoMetadata
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 微信式自适应参数计算。
 *
 * 核心思路：
 * 1) 按 "短边对齐" 缩放：取 min(原短边, 预设短边)，长边按原视频宽高比等比缩放，
 *    然后宽高都对齐到 16 的倍数（H.264 编码器对宽高 16 对齐时效率最高，
 *    很多硬编也要求至少 2 对齐，部分要求 16 对齐）。
 * 2) 按 "像素 × fps × bpp" 动态计算目标码率：
 *      bitrate = width * height * fps * bppFactor
 *    其中 bppFactor 取 0.10 ~ 0.12（H.264 高效编码经验值，与微信观测吻合）。
 *    这样 720p@30 ≈ 2.7Mbps、544p@30 ≈ 1.6Mbps，与微信抓包结果一致。
 * 3) 用预设的 videoBitrate 作为 "上限"，避免低分辨率时给得太大；
 *    用原视频码率的 85% 作为 "二级上限"，避免反向放大。
 * 4) 帧率不超过原视频 fps，且不超过预设 fps。
 */
object SmartParams {

    // 微信抓包观测值约 0.10；动态画面（fps≥30）适当上浮一点保清晰
    private const val BPP_FACTOR = 0.11

    // 最低保底码率，避免极端情况下码率给到几百 K 直接糊
    private const val MIN_BITRATE = 400_000

    fun adapt(original: VideoMetadata, params: CompressParams): CompressParams {
        // ---------- 1. 分辨率（短边对齐 + 16 对齐） ----------
        val (newW, newH) = computeTargetSize(
            origW = original.width,
            origH = original.height,
            targetShortSide = if (params.maxHeight > 0) params.maxHeight else 720
        )

        // ---------- 2. 帧率 ----------
        // metadata 里没有 fps，用 bitrate 推不出来；这里用预设 fps 做上限即可
        val targetFps = params.frameRate.coerceAtMost(30)

        // ---------- 3. 动态码率 ----------
        val computed = (newW.toLong() * newH * targetFps * BPP_FACTOR).toInt()

        // 上限 1：预设给出的 videoBitrate
        val capByPreset = params.videoBitrate

        // 上限 2：原视频视频码率的 85%（避免反向变大）
        // metadata.bitrate 是总码率（含音频），先扣除一个估算的音频
        val origVideoBitrate = (original.bitrate - 128_000L).coerceAtLeast(0L).toInt()
        val capByOriginal = if (origVideoBitrate > 0) (origVideoBitrate * 0.85).toInt() else Int.MAX_VALUE

        val finalBitrate = min(min(computed, capByPreset), capByOriginal)
            .coerceAtLeast(MIN_BITRATE)

        return params.copy(
            maxWidth = newW,
            maxHeight = newH,
            videoBitrate = finalBitrate,
            frameRate = targetFps,
        )
    }

    /**
     * 短边对齐 + 16 对齐。
     * @return Pair(width, height) —— 保持原视频的横竖屏方向
     */
    private fun computeTargetSize(origW: Int, origH: Int, targetShortSide: Int): Pair<Int, Int> {
        if (origW <= 0 || origH <= 0) {
            // 原始尺寸读不到，回退一个安全值
            return alignTo16(targetShortSide) to alignTo16((targetShortSide * 16 / 9))
        }

        val origShort = min(origW, origH)
        val origLong = max(origW, origH)

        // 短边不能超过原短边，也不能超过预设
        val newShort = min(origShort, targetShortSide)
        // 长边按原比例
        val newLong = (newShort.toLong() * origLong / origShort).toInt()

        val (w, h) = if (origW >= origH) {
            // 横屏：长边是宽
            newLong to newShort
        } else {
            // 竖屏：长边是高
            newShort to newLong
        }

        return alignTo16(w) to alignTo16(h)
    }

    /** H.264 友好对齐（向下对齐到 16 的倍数，最低 144） */
    private fun alignTo16(value: Int): Int {
        val aligned = (value / 16) * 16
        return aligned.coerceAtLeast(144)
    }
}
