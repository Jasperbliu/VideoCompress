package com.cloudwinbuddy.videocompress.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

object ShareHelper {
    private const val TAG = "VCompress"

    fun shareVideo(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享压缩后视频"))
    }

    /**
     * 打开系统相册（图片 + 视频集合）。
     *
     * 注意：不要使用 ACTION_VIEW + `video` MIME + EXTERNAL_CONTENT_URI，
     * 这种组合在小米/部分厂商上会优先匹配 "小米视频" 等播放器，而不是相册。
     *
     * 策略：
     * 1. 优先使用 Intent.CATEGORY_APP_GALLERY 标准入口（Android 官方推荐）
     * 2. 其次按已知包名直接拉起厂商相册（小米/华为/OPPO/vivo/三星/Google Photos）
     * 3. 再用 ACTION_VIEW + `image` MIME 让系统选择图库（图库一般同时承载视频）
     * 4. 最后退化到 GET_CONTENT 选择器
     */
    fun openSystemGallery(context: Context) {
        // 1) 标准 APP_GALLERY 类目
        val galleryCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_GALLERY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(galleryCategoryIntent)
            Log.d(TAG, "openSystemGallery: launched via CATEGORY_APP_GALLERY")
            return
        } catch (_: ActivityNotFoundException) {
            // fallthrough
        }

        // 2) 直接尝试常见相册包名的 LAUNCHER Intent
        val galleryPackages = listOf(
            "com.miui.gallery",          // 小米相册
            "com.huawei.photos",         // 华为图库
            "com.android.gallery3d",     // AOSP / 部分定制
            "com.coloros.gallery3d",     // OPPO
            "com.oppo.gallery3d",        // OPPO 旧版
            "com.vivo.gallery",          // vivo
            "com.sec.android.gallery3d", // 三星
            "com.google.android.apps.photos", // Google Photos
        )
        val pm = context.packageManager
        for (pkg in galleryPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                Log.d(TAG, "openSystemGallery: launched $pkg")
                return
            } catch (e: Throwable) {
                Log.w(TAG, "openSystemGallery: launch $pkg failed", e)
            }
        }

        // 3) ACTION_VIEW + "image/*"：相册类 App 一般注册了 image MIME，而播放器只注册 video MIME
        val viewImage = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewImage)
            return
        } catch (_: ActivityNotFoundException) {
            // fallthrough
        }

        // 4) 退化方案：用 GET_CONTENT 让用户选择相册 App 浏览
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(pickIntent, "打开相册"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用的相册应用", Toast.LENGTH_SHORT).show()
        }
    }
}
