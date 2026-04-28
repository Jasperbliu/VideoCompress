package com.cloudwinbuddy.videocompress.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    fun readVideoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasReadVideoPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            readVideoPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }
}
