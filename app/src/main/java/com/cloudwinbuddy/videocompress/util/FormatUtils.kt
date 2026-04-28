package com.cloudwinbuddy.videocompress.util

import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[i])
    }

    fun formatDurationMs(ms: Long): String {
        val total = (ms / 1000).toInt()
        val m = total / 60
        val s = total % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
