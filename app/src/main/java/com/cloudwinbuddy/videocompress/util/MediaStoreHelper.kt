package com.cloudwinbuddy.videocompress.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import java.io.File

object MediaStoreHelper {

    private const val TAG = "VCompress.Save"

    /**
     * 注意：小米/MIUI 相册对自定义 DCIM 子目录扫描不友好。
     * 使用 Movies/VideoCompress/ 写入，小米相册的"视频"分类会扫描该目录。
     * 同时在 Q 以下会写入真实文件并触发 MediaScanner，Q+ 也会回查真实路径再触发一次扫描以兜底。
     */
    private const val BUCKET_NAME = "VideoCompress"
    private const val RELATIVE_DIR = "Movies/$BUCKET_NAME/"

    fun saveVideoToGallery(context: Context, sourceFile: File, displayName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceFile, displayName)
        } else {
            saveViaLegacyFile(context, sourceFile, displayName)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, sourceFile: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val nowSec = System.currentTimeMillis() / 1000
        val nowMs = System.currentTimeMillis()

        // 提前读出宽高/时长，写入 MediaStore 元信息，避免相册因元信息缺失忽略
        val (width, height, durationMs) = readBasicVideoInfo(sourceFile)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.TITLE, displayName.substringBeforeLast('.'))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Video.Media.BUCKET_DISPLAY_NAME, BUCKET_NAME)
            put(MediaStore.Video.Media.DATE_ADDED, nowSec)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
            put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
            put(MediaStore.Video.Media.SIZE, sourceFile.length())
            if (width > 0) put(MediaStore.Video.Media.WIDTH, width)
            if (height > 0) put(MediaStore.Video.Media.HEIGHT, height)
            if (durationMs > 0) put(MediaStore.Video.Media.DURATION, durationMs)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = requireNotNull(resolver.insert(collection, values)) { "创建媒体条目失败" }
        Log.d(TAG, "MediaStore.insert -> $uri (relative=$RELATIVE_DIR file=${sourceFile.absolutePath} size=${sourceFile.length()})")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    val copied = input.copyTo(output)
                    Log.d(TAG, "Copied $copied bytes into MediaStore uri=$uri")
                }
            } ?: throw IllegalStateException("无法打开输出流")

            val complete = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            val updated = resolver.update(uri, complete, null, null)
            Log.d(TAG, "Cleared IS_PENDING rows=$updated uri=$uri")
        } catch (t: Throwable) {
            Log.e(TAG, "Write to MediaStore FAILED uri=$uri", t)
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        // 兜底：回查真实路径并主动触发 MediaScanner，
        // 解决小米相册不识别 MediaStore 新增条目的问题
        val realPath = queryRealPath(context, uri)
        Log.d(TAG, "Resolved real path=$realPath for uri=$uri")
        if (!realPath.isNullOrBlank()) {
            triggerMediaScan(context, realPath)
        }

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(context: Context, sourceFile: File, displayName: String): Uri {
        val moviesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val targetDir = File(moviesRoot, BUCKET_NAME).apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(targetDir, displayName)

        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        triggerMediaScan(context, targetFile.absolutePath)
        return targetFile.toUri()
    }

    private fun queryRealPath(context: Context, uri: Uri): String? {
        return runCatching {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun triggerMediaScan(context: Context, absolutePath: String) {
        // 1. 标准 MediaScanner 扫描（系统相册依赖）
        runCatching {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(absolutePath),
                arrayOf("video/mp4"),
                null
            )
        }
        // 2. 兼容小米：广播 ACTION_MEDIA_SCANNER_SCAN_FILE（在 Q 之前有效，Q+ 系统已忽略，但小米相册仍可能监听）
        runCatching {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(File(absolutePath))
            }
            context.applicationContext.sendBroadcast(intent)
        }
    }

    private fun readBasicVideoInfo(file: File): Triple<Int, Int, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(w, h, d)
        } catch (_: Throwable) {
            Triple(0, 0, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 删除指定的相册视频条目。
     * - MediaStore content uri：使用 ContentResolver.delete
     * - file:// uri：直接删除文件
     * 返回是否删除成功（不存在也算 false，但不会抛异常）。
     */
    fun deleteSavedVideo(context: Context, uri: Uri): Boolean {
        return runCatching {
            when (uri.scheme) {
                "content" -> {
                    // 先记录真实路径用于扫描刷新
                    val realPath = queryRealPath(context, uri)
                    val rows = context.contentResolver.delete(uri, null, null)
                    Log.d(TAG, "deleteSavedVideo content uri=$uri rows=$rows realPath=$realPath")
                    if (!realPath.isNullOrBlank()) {
                        // 触发扫描刷新，避免相册仍残留缓存
                        triggerMediaScan(context, realPath)
                    }
                    rows > 0
                }
                "file" -> {
                    val path = uri.path
                    if (!path.isNullOrBlank()) {
                        val f = File(path)
                        val ok = if (f.exists()) f.delete() else false
                        Log.d(TAG, "deleteSavedVideo file=$path ok=$ok")
                        if (ok) triggerMediaScan(context, path)
                        ok
                    } else false
                }
                else -> {
                    Log.w(TAG, "deleteSavedVideo unsupported scheme uri=$uri")
                    false
                }
            }
        }.getOrElse {
            Log.e(TAG, "deleteSavedVideo failed uri=$uri", it)
            false
        }
    }

    /**
     * 把任意 content:// uri 内容复制到 app cache 目录中，得到一个本地 File，
     * 用于压缩失败回退或对比保存时的统一处理。
     */
    fun copyUriToCache(context: Context, uri: Uri, fileName: String): File {
        val out = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开输入流: $uri" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}
