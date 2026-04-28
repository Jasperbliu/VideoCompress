package com.cloudwinbuddy.videocompress.compress

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.cloudwinbuddy.videocompress.model.CompressParams
import com.cloudwinbuddy.videocompress.model.VideoCodec
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var transformer: Transformer? = null

    /**
     * Media3 Transformer 严格要求所有 build / start / getProgress / cancel 调用
     * 必须在创建它的同一个 Looper 线程上执行（默认主线程）。
     * 因此这里全程在主线程 Handler 上调度，编码本身由 Transformer 内部
     * 在系统编码器线程异步进行，不会阻塞 UI。
     */
    suspend fun compress(
        inputUri: Uri,
        params: CompressParams,
        onProgress: (Float) -> Unit,
    ): File {
        val output = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        if (!output.parentFile!!.exists()) output.parentFile!!.mkdirs()

        return suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                try {
                    // 关键编码参数（对齐微信策略）：
                    //  - VBR 模式（BITRATE_MODE_VBR）：静态画面少用码率、动态画面用足，主观画质更高
                    //  - GOP = 2s 关键帧（与微信一致，兼顾 seek 与体积）
                    //  - 按所选编码格式设置 profile/level（H264 用 High@4.0, H265 用 Main@4.0,
                    //    AV1 不主动指定让编码器选默认）
                    val videoEncoderSettingsBuilder = VideoEncoderSettings.Builder()
                        .setBitrate(params.videoBitrate)
                        .setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                        .setiFrameIntervalSeconds(2f)

                    when (params.videoCodec) {
                        VideoCodec.H264 -> {
                            videoEncoderSettingsBuilder.setEncodingProfileLevel(
                                android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                                android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4
                            )
                        }
                        VideoCodec.H265 -> {
                            videoEncoderSettingsBuilder.setEncodingProfileLevel(
                                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                                android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4
                            )
                        }
                        VideoCodec.AV1 -> {
                            // AV1 编码器对 profile/level 支持参差，交给编码器默认
                        }
                    }
                    val videoEncoderSettings = videoEncoderSettingsBuilder.build()

                    val audioEncoderSettings = AudioEncoderSettings.Builder()
                        .setBitrate(params.audioBitrate)
                        .build()

                    val encoderFactory = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(videoEncoderSettings)
                        .setRequestedAudioEncoderSettings(audioEncoderSettings)
                        .build()

                    val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                        .setFrameRate(params.frameRate)
                        .setEffects(
                            Effects(
                                emptyList(),
                                listOf(
                                    Presentation.createForWidthAndHeight(
                                        params.maxWidth,
                                        params.maxHeight,
                                        Presentation.LAYOUT_SCALE_TO_FIT
                                    )
                                )
                            )
                        )
                        .build()

                    Log.d(TAG, "Transformer build codec=${params.videoCodec.name} mime=${params.videoCodec.mimeType}")

                    val localTransformer = Transformer.Builder(context)
                        .setLooper(Looper.getMainLooper())
                        .setEncoderFactory(encoderFactory)
                        .setVideoMimeType(params.videoCodec.mimeType)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                Log.d(TAG, "Transformer onCompleted output=${output.absolutePath} size=${output.length()}")
                                if (continuation.isActive) continuation.resume(output)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                Log.e(TAG, "Transformer onError code=${exportException.errorCode}", exportException)
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        })
                        .build()

                    transformer = localTransformer
                    Log.d(TAG, "Transformer.start -> ${output.absolutePath}")
                    localTransformer.start(editedMediaItem, output.absolutePath)

                    // 周期性轮询进度（也必须在主线程 Handler 上）
                    val progressHolder = ProgressHolder()
                    val pollRunnable = object : Runnable {
                        override fun run() {
                            if (!continuation.isActive) return
                            try {
                                val state = localTransformer.getProgress(progressHolder)
                                if (state != Transformer.PROGRESS_STATE_NOT_STARTED &&
                                    state != Transformer.PROGRESS_STATE_UNAVAILABLE
                                ) {
                                    onProgress((progressHolder.progress / 100f).coerceIn(0f, 1f))
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "getProgress failed", t)
                            }
                            mainHandler.postDelayed(this, 250)
                        }
                    }
                    mainHandler.postDelayed(pollRunnable, 250)

                    continuation.invokeOnCancellation {
                        mainHandler.removeCallbacks(pollRunnable)
                        mainHandler.post {
                            runCatching { localTransformer.cancel() }
                            runCatching { output.delete() }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Transformer setup failed", t)
                    if (continuation.isActive) continuation.resumeWithException(t)
                }
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            runCatching { transformer?.cancel() }
        }
    }

    companion object {
        private const val TAG = "VCompress"
    }
}
