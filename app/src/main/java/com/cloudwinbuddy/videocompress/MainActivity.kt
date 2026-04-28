package com.cloudwinbuddy.videocompress

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.cloudwinbuddy.videocompress.ui.screen.HomeScreen
import com.cloudwinbuddy.videocompress.ui.theme.VideoCompressTheme
import com.cloudwinbuddy.videocompress.util.PermissionHelper
import com.cloudwinbuddy.videocompress.util.ShareHelper
import com.cloudwinbuddy.videocompress.viewmodel.CompressViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CompressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val photoPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.onVideosSelected(uris)
            }
        }

        // 传统的相册选择器（针对小米等手机优化，优先使用 ACTION_PICK）
        val galleryPicker = registerForActivityResult(object : ActivityResultContract<String, List<Uri>>() {
            override fun createIntent(context: Context, input: String): Intent {
                // 技巧：使用 Images.Media.EXTERNAL_CONTENT_URI 即使我们要选的是视频
                // 这在小米等设备上能更稳定地唤起“相册”应用而非“小米视频”
                return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, input)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                val uris = mutableListOf<Uri>()
                if (resultCode == RESULT_OK && intent != null) {
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    } ?: intent.data?.let { uri ->
                        uris.add(uri)
                    }
                }
                return uris
            }
        }) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.onVideosSelected(uris)
            }
        }

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                galleryPicker.launch("video/*")
            }
        }

        setContent {
            VideoCompressTheme {
                Surface {
                    HomeScreen(
                        viewModel = viewModel,
                        onPickVideo = {
                            // 优先使用系统照片选择器 (Photo Picker)
                            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else {
                                // 备选方案：使用 ACTION_PICK 确保打开的是相册而非文件管理器，这在小米手机上体验更好
                                if (!PermissionHelper.hasReadVideoPermission(this)) {
                                    permissionLauncher.launch(PermissionHelper.readVideoPermission())
                                } else {
                                    galleryPicker.launch("video/*")
                                }
                            }
                        },
                        onShare = { uri -> ShareHelper.shareVideo(this, uri) },
                    )
                }
            }
        }
    }
}
