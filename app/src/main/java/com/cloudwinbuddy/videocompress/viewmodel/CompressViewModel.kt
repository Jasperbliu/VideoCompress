package com.cloudwinbuddy.videocompress.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudwinbuddy.videocompress.compress.CodecSupport
import com.cloudwinbuddy.videocompress.compress.CompressPresets
import com.cloudwinbuddy.videocompress.compress.SmartParams
import com.cloudwinbuddy.videocompress.compress.VideoCompressor
import com.cloudwinbuddy.videocompress.model.CompressParams
import com.cloudwinbuddy.videocompress.model.CompressUiState
import com.cloudwinbuddy.videocompress.model.VideoCodec
import com.cloudwinbuddy.videocompress.model.VideoItem
import com.cloudwinbuddy.videocompress.service.CompressForegroundService
import com.cloudwinbuddy.videocompress.util.MediaStoreHelper
import com.cloudwinbuddy.videocompress.util.StorageEstimator
import com.cloudwinbuddy.videocompress.util.VideoMetadataReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompressViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val compressor = VideoCompressor(app)

    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    init {
        // 启动时探测设备实际可用的编码器，UI 仅展示这些选项
        val supported = CodecSupport.supportedEncoders()
            .toList()
            .sortedBy { VideoCodec.values().indexOf(it) }
        Log.d(TAG, "device supported codecs=${supported.map { it.name }}")
        // 默认优先使用 H265（HEVC），同等画质下码率约为 H264 的 50-70%
        // 若设备不支持 H265，则降级为 H264
        val defaultCodec = if (VideoCodec.H265 in supported) VideoCodec.H265 else VideoCodec.H264
        _uiState.update {
            it.copy(
                supportedCodecs = supported,
                currentParams = it.currentParams.copy(videoCodec = defaultCodec),
            )
        }
    }

    companion object {
        private const val TAG = "VCompress"
    }

    fun onVideosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val items = uris.map { uri ->
                val metadata = runCatching { VideoMetadataReader.read(app, uri) }.getOrNull()
                VideoItem(uri = uri, metadata = metadata)
            }
            _uiState.update {
                it.copy(
                    videos = items,
                    statusMessage = "已选择 ${items.size} 个视频",
                )
            }
        }
    }

    fun setPreset(params: CompressParams) {
        // 切换预设时保留当前已选编码格式
        _uiState.update { state ->
            state.copy(currentParams = params.copy(videoCodec = state.currentParams.videoCodec))
        }
    }

    fun setCodec(codec: VideoCodec) {
        if (!CodecSupport.isSupported(codec)) {
            Log.w(TAG, "setCodec ignored: $codec not supported on this device")
            return
        }
        _uiState.update { it.copy(currentParams = it.currentParams.copy(videoCodec = codec)) }
    }

    fun setBitrate(bitrate: Int) {
        _uiState.update {
            it.copy(currentParams = it.currentParams.copy(videoBitrate = bitrate, presetName = "自定义"))
        }
    }

    fun setFrameRate(frameRate: Int) {
        _uiState.update {
            it.copy(currentParams = it.currentParams.copy(frameRate = frameRate, presetName = "自定义"))
        }
    }

    fun startCompress() {
        // 若上一轮已全部完成，则在新一轮开始前：
        // 1) 重置每个 item 的压缩状态（保留 uri / metadata），让其重新参与压缩
        // 2) 删除上一轮已写入相册的输出文件，由本轮覆盖
        val current = uiState.value.videos
        val allDone = current.isNotEmpty() && current.all { it.isDone }
        if (allDone) {
            current.forEach { item ->
                item.outputUri?.let { out ->
                    val ok = MediaStoreHelper.deleteSavedVideo(app, out)
                    Log.d(TAG, "rerun: delete previous output uri=$out ok=$ok")
                }
            }
            _uiState.update { state ->
                state.copy(
                    videos = state.videos.map {
                        it.copy(
                            progress = 0f,
                            outputUri = null,
                            outputSizeBytes = 0L,
                            fellBackToOriginal = false,
                            isProcessing = false,
                            isDone = false,
                            error = null,
                        )
                    }
                )
            }
        }

        val videos = uiState.value.videos.filter { !it.isDone }
        if (videos.isEmpty()) return

        val params = uiState.value.currentParams

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, statusMessage = "开始批量压缩") }
            
            try {
                videos.forEachIndexed { index, item ->
                    if (!uiState.value.isCompressing) return@forEachIndexed

                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos.map {
                                if (it.uri == item.uri) it.copy(isProcessing = true) else it
                            },
                            statusMessage = "正在压缩第 ${index + 1}/${videos.size} 个"
                        )
                    }

                    val result = runCatching {
                        // 智能调参：避免目标码率/分辨率超过原视频导致体积变大
                        val effectiveParams: CompressParams = item.metadata?.let { meta ->
                            SmartParams.adapt(meta, params).also { adapted ->
                                Log.d(TAG, "[${index + 1}] adapted: orig=${meta.width}x${meta.height}@${meta.bitrate}bps -> videoBitrate=${adapted.videoBitrate}")
                            }
                        } ?: params

                        // 空间检查
                        item.metadata?.let { meta ->
                            val estimated = StorageEstimator.estimateOutputBytes(meta, effectiveParams)
                            Log.d(TAG, "[${index + 1}/${videos.size}] originalSize=${meta.sizeBytes} estimatedOutput=$estimated")
                            if (!StorageEstimator.hasEnoughSpace(app, estimated)) {
                                throw Exception("空间不足")
                            }
                        }

                        Log.d(TAG, "[${index + 1}] start compress params=$effectiveParams codec=${effectiveParams.videoCodec.name}")
                        val file = runCatching {
                            compressor.compress(item.uri, effectiveParams) { progress ->
                                updateVideoProgress(item.uri, progress)
                                val overallProgress = ((index + progress) / videos.size * 100).toInt()
                                CompressForegroundService.start(app, overallProgress)
                            }
                        }.getOrElse { firstError ->
                            // 第一次失败：若用户选了 H265/AV1，先回退到 H264 同参数重试
                            if (effectiveParams.videoCodec != VideoCodec.H264) {
                                Log.w(TAG, "[${index + 1}] codec=${effectiveParams.videoCodec.name} failed, fallback to H264", firstError)
                                runCatching {
                                    compressor.compress(
                                        item.uri,
                                        effectiveParams.copy(videoCodec = VideoCodec.H264)
                                    ) { progress -> updateVideoProgress(item.uri, progress) }
                                }.getOrElse { secondError ->
                                    Log.w(TAG, "[${index + 1}] H264 fallback also failed, retry with MIN_SIZE", secondError)
                                    compressor.compress(item.uri, CompressPresets.MIN_SIZE) { progress ->
                                        updateVideoProgress(item.uri, progress)
                                    }
                                }
                            } else {
                                Log.w(TAG, "[${index + 1}] first compress failed, retry with MIN_SIZE", firstError)
                                compressor.compress(item.uri, CompressPresets.MIN_SIZE) { progress ->
                                    updateVideoProgress(item.uri, progress)
                                }
                            }
                        }

                        val outputSize = file.length()
                        val originalSize = item.metadata?.sizeBytes ?: 0L
                        Log.d(TAG, "[${index + 1}] compress done size=$outputSize original=$originalSize")

                        // 压缩后体积 ≥ 原文件 → 直接跳过，不写入相册
                        if (originalSize in 1..outputSize) {
                            Log.w(TAG, "[${index + 1}] compressed >= original, skip writing")
                            file.delete()
                            Triple<Uri?, Long, Boolean>(null, originalSize, true)
                        } else {
                            val finalName = "VC_${System.currentTimeMillis()}_$index.mp4"
                            val savedUri = try {
                                MediaStoreHelper.saveVideoToGallery(app, file, finalName)
                            } catch (saveError: Throwable) {
                                Log.e(TAG, "[${index + 1}] save FAILED, cache kept at ${file.absolutePath}", saveError)
                                throw saveError
                            }
                            Log.d(TAG, "[${index + 1}] saved uri=$savedUri")
                            file.delete()
                            Triple<Uri?, Long, Boolean>(savedUri, outputSize, false)
                        }
                    }

                    result.exceptionOrNull()?.let {
                        Log.e(TAG, "[${index + 1}] item failed", it)
                    }

                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos.map {
                                if (it.uri == item.uri) {
                                    val triple = result.getOrNull()
                                    it.copy(
                                        isProcessing = false,
                                        isDone = true,
                                        progress = 1f,
                                        outputUri = triple?.first,
                                        outputSizeBytes = triple?.second ?: 0L,
                                        fellBackToOriginal = triple?.third == true,
                                        error = result.exceptionOrNull()?.let { e ->
                                            "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}"
                                        }
                                    )
                                } else it
                            }
                        )
                    }
                }
                _uiState.update { it.copy(isCompressing = false, statusMessage = "批量压缩完成") }
            } catch (e: Exception) {
                Log.e(TAG, "batch compress interrupted", e)
                _uiState.update { it.copy(isCompressing = false, statusMessage = "压缩中断：${e.message}") }
            } finally {
                CompressForegroundService.stop(app)
            }
        }
    }

    private fun updateVideoProgress(uri: Uri, progress: Float) {
        _uiState.update { state ->
            state.copy(
                videos = state.videos.map {
                    if (it.uri == uri) it.copy(progress = progress) else it
                }
            )
        }
    }

    fun cancelCompress() {
        compressor.cancel()
        _uiState.update { it.copy(isCompressing = false, statusMessage = "已取消压缩") }
        CompressForegroundService.stop(app)
    }
}
