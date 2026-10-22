package com.gsh.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

/**
 * 拍照 / 相册选图的启动器封装：
 * - 拍照走系统相机（TakePicture + FileProvider 临时文件），无需相机权限；
 * - 选图走系统照片选择器（PickVisualMedia），无需存储权限。
 * onResult 统一回调：uri 为空表示用户取消；tempFile 仅拍照时非空，
 * 调用方在照片导入完成后负责删除临时文件。
 */
class PhotoLaunchers(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit
)

@Composable
fun rememberPhotoLaunchers(onResult: (Uri?, File?) -> Unit): PhotoLaunchers {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Uri, File>?>(null) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = pending
        pending = null
        if (success && capture != null && capture.second.exists()) {
            onResult(capture.first, capture.second)
        } else {
            capture?.second?.delete()
            onResult(null, null)
        }
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onResult(uri, null) }

    return PhotoLaunchers(
        takePhoto = {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            pending = uri to file
            camera.launch(uri)
        },
        pickFromGallery = {
            gallery.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )
}

/** 照片缩略图：圆角方块，点击回调。 */
@Composable
fun PhotoThumb(
    file: File,
    size: Dp,
    onClick: () -> Unit
) {
    AsyncImage(
        model = file,
        contentDescription = "照片",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    )
}

/**
 * 全屏看大图：黑色底，点任意处关闭，右上角删除，左下角显示日期等信息。
 */
@Composable
fun PhotoViewerDialog(
    file: File,
    caption: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            AsyncImage(
                model = file,
                contentDescription = "照片大图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 8.dp)
            ) {
                Text("删除", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}
