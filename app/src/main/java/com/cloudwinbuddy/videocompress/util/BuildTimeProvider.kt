package com.cloudwinbuddy.videocompress.util

import android.content.Context
import com.cloudwinbuddy.videocompress.BuildConfig

/**
 * 构建时间提供者：
 * 1. 优先返回 BuildConfig.BUILD_TIME（构建时由 Gradle 注入）
 * 2. 其次回退到 assets/build_time.txt（构建时由 writeBuildTimeFile 任务写入）
 */
object BuildTimeProvider {

    fun get(context: Context): String {
        val fromBuildConfig = runCatching { BuildConfig.BUILD_TIME }.getOrNull()
        if (!fromBuildConfig.isNullOrBlank()) return fromBuildConfig

        return runCatching {
            context.assets.open("build_time.txt").bufferedReader().use { it.readText().trim() }
        }.getOrDefault("unknown")
    }
}
