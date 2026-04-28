package com.cloudwinbuddy.videocompress.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cloudwinbuddy.videocompress.BuildConfig
import com.cloudwinbuddy.videocompress.compress.CompressPresets
import com.cloudwinbuddy.videocompress.model.VideoCodec
import com.cloudwinbuddy.videocompress.model.VideoItem
import com.cloudwinbuddy.videocompress.util.BuildTimeProvider
import com.cloudwinbuddy.videocompress.util.FormatUtils
import com.cloudwinbuddy.videocompress.util.ShareHelper
import com.cloudwinbuddy.videocompress.viewmodel.CompressViewModel

private enum class Page { Home, VideoList }

@Composable
fun HomeScreen(
    viewModel: CompressViewModel,
    onPickVideo: () -> Unit,
    onShare: (Uri) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var page by remember { mutableStateOf(Page.Home) }

    // 开始压缩时自动跳转到视频列表页
    LaunchedEffect(uiState.isCompressing) {
        if (uiState.isCompressing) {
            page = Page.VideoList
        }
    }

    // 当视频列表为空时（例如重置），强制回到主页
    LaunchedEffect(uiState.videos.isEmpty()) {
        if (uiState.videos.isEmpty() && page == Page.VideoList) {
            page = Page.Home
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == Page.VideoList) {
                    // 进入列表页：新页面从右侧滑入，旧页面向左滑出（“右滑进入”）
                    (slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(280)) { -it / 4 } + fadeOut(tween(280)))
                } else {
                    // 返回主页：主页从左侧滑入，列表页向右滑出（“左滑返回”）
                    (slideInHorizontally(animationSpec = tween(280)) { -it / 4 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(tween(280)))
                }
            },
            label = "page-transition",
        ) { current ->
            when (current) {
                Page.Home -> HomeContent(
                    viewModel = viewModel,
                    onPickVideo = onPickVideo,
                    onOpenVideoList = { page = Page.VideoList },
                )
                Page.VideoList -> VideoListPage(
                    videos = uiState.videos,
                    onShare = onShare,
                    onBack = { page = Page.Home },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(
    viewModel: CompressViewModel,
    onPickVideo: () -> Unit,
    onOpenVideoList: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val buildInfo = remember {
        if (BuildConfig.DEBUG) {
            BuildTimeProvider.get(context)
        } else {
            "v${BuildConfig.VERSION_NAME}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "构建版本号：$buildInfo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(text = "批量视频压缩", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
            Text("选择视频 (多选)")
        }

        Button(
            onClick = { ShareHelper.openSystemGallery(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("打开系统相册")
        }

        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("压缩预设")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetButton(
                        label = "体积",
                        selected = uiState.currentParams.presetName == CompressPresets.WECHAT_COMPACT.presetName,
                        onClick = { viewModel.setPreset(CompressPresets.WECHAT_COMPACT) },
                    )
                    PresetButton(
                        label = "画质",
                        selected = uiState.currentParams.presetName == CompressPresets.WECHAT_QUALITY.presetName,
                        onClick = { viewModel.setPreset(CompressPresets.WECHAT_QUALITY) },
                    )
                    PresetButton(
                        label = "极限",
                        selected = uiState.currentParams.presetName == CompressPresets.MIN_SIZE.presetName,
                        onClick = { viewModel.setPreset(CompressPresets.MIN_SIZE) },
                    )
                }

                Text("当前：${uiState.currentParams.presetName}")
                Text("短边目标：${uiState.currentParams.maxHeight}px")
                Text("码率上限：${"%.1f".format(uiState.currentParams.videoBitrate / 1_000_000f)} Mbps")
                Slider(
                    value = uiState.currentParams.videoBitrate.toFloat(),
                    onValueChange = { viewModel.setBitrate(it.toInt()) },
                    valueRange = 1_000_000f..8_000_000f,
                )

                Text("帧率上限：${uiState.currentParams.frameRate} fps")
                Slider(
                    value = uiState.currentParams.frameRate.toFloat(),
                    onValueChange = { viewModel.setFrameRate(it.toInt().coerceIn(15, 60)) },
                    valueRange = 15f..60f,
                )

                Text("视频编码：${uiState.currentParams.videoCodec.displayName}")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.supportedCodecs.forEach { codec ->
                        val selected = uiState.currentParams.videoCodec == codec
                        Button(
                            onClick = { viewModel.setCodec(codec) },
                            colors = if (selected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = codec.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
                val unsupported = VideoCodec.values().filterNot { it in uiState.supportedCodecs }
                if (unsupported.isNotEmpty()) {
                    Text(
                        text = "本机不支持：${unsupported.joinToString { it.displayName }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "H.265 体积约为 H.264 的 60%，AV1 更小但兼容性差；不支持时会自动回退到 H.264",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isCompressing) {
            Button(onClick = viewModel::cancelCompress, modifier = Modifier.fillMaxWidth()) {
                Text("取消批量任务")
            }
        } else {
            Button(
                onClick = viewModel::startCompress,
                enabled = uiState.videos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始压缩 (${uiState.videos.size} 个)")
            }
        }

        uiState.statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.videos.isNotEmpty()) {
            Button(
                onClick = onOpenVideoList,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Text("查看视频列表 (${uiState.videos.size}) →")
            }
        }
    }
}

@Composable
private fun VideoListPage(
    videos: List<VideoItem>,
    onShare: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    // 系统返回键 → 回到主页
    BackHandler(onBack = onBack)

    // 手势左滑（横向拖动累计超过阈值）→ 回到主页
    var dragAccum by remember { mutableStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAccum += delta
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragAccum = 0f },
                onDragStopped = {
                    if (dragAccum < -120f) onBack()
                    dragAccum = 0f
                },
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("← 返回")
            }
            Text(
                text = "视频列表 (${videos.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videos) { item ->
                VideoItemRow(item, onShare)
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun VideoItemRow(item: VideoItem, onShare: (Uri) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.error != null) MaterialTheme.colorScheme.errorContainer 
            else if (item.isDone) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val name = item.uri.lastPathSegment ?: "未知视频"
            Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            
            item.metadata?.let { meta ->
                Text(
                    text = "${meta.width}x${meta.height} | ${FormatUtils.formatDurationMs(meta.durationMs)} | ${FormatUtils.formatBytes(meta.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (item.isProcessing) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("进度: ${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            } else if (item.isDone) {
                if (item.fellBackToOriginal) {
                    Text("状态: 已跳过（压缩后体积未减小，未写入相册）", color = MaterialTheme.colorScheme.tertiary)
                } else {
                    Text("状态: 已完成", color = MaterialTheme.colorScheme.secondary)
                }
                val originalSize = item.metadata?.sizeBytes ?: 0L
                if (!item.fellBackToOriginal && originalSize > 0 && item.outputSizeBytes > 0) {
                    val saved = originalSize - item.outputSizeBytes
                    val savedPercent = (saved.toDouble() / originalSize * 100).toInt()
                    Text(
                        text = "压缩前: ${FormatUtils.formatBytes(originalSize)} → 压缩后: ${FormatUtils.formatBytes(item.outputSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (saved > 0) {
                        Text(
                            text = "节省: ${FormatUtils.formatBytes(saved)} ($savedPercent%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item.outputUri?.let { output ->
                    Button(
                        onClick = { onShare(output) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("分享", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (item.error != null) {
                Text("错误: ${item.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
