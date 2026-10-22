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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gsh.app.data.Photo
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
 * 全屏看大图：黑色底，左右滑动切换同组照片（同餐次/同日期），
 * 点任意处关闭，右上角删除（删一张后自动停在下一张，组内删光由调用方关闭），
 * 左下角显示日期与第几张。
 */
@Composable
fun PhotoViewerDialog(
    photos: List<Photo>,
    initialId: Long,
    fileOf: (Photo) -> File,
    captionOf: (Photo) -> String,
    onDelete: (Photo) -> Unit,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = photos.indexOfFirst { it.id == initialId }.coerceAtLeast(0),
        pageCount = { photos.size }
    )

    // 删除最后一张后当前页码越界，收回新的末页
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty() && pagerState.currentPage > photos.lastIndex) {
            pagerState.animateScrollToPage(photos.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            ) { page ->
                AsyncImage(
                    model = fileOf(photos[page]),
                    contentDescription = "照片大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            TextButton(
                onClick = { photos.getOrNull(pagerState.currentPage)?.let(onDelete) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 8.dp)
            ) {
                Text("删除", color = Color.White, fontWeight = FontWeight.Bold)
            }
            val page = pagerState.currentPage.coerceIn(0, photos.lastIndex)
            Text(
                text = captionOf(photos[page]) + " · ${page + 1}/${photos.size}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}
