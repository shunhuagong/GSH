package com.gsh.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/** 「变化」页照片缩放固定规格（需求文档-变化页面-照片放大.md，不隐埋 magic number）。 */
private const val PHOTO_ZOOM_MIN = 1f           // 双指缩小下限：1 倍，不允许低于 1
private const val PHOTO_ZOOM_MAX = 8f            // 双指放大上限：8 倍
private const val PAN_SPEED_FACTOR = 2f         // 拖动跟手速比 = 缩放倍数 × 2（4× 时 1:8）
private const val EDGE_SWITCH_OVERFLOW_PX = 60f // 松手时累计手指溢出 ≥ 60px 才翻页（固定规格）
private const val EDGE_RUBBER_BAND = 0.25f      // 贴边继续拖：溢出仅 25% 橡皮筋位移，不中途切换
private const val EDGE_SPRING_BACK_MS = 150     // 松手溢出不足阈值：150ms 弹回最近边界

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
 *
 * zoomable=true（「变化」页）开启双指缩放（规格见文件顶部常量）：1.0~8 倍以照片为中心
 * 捏合、松手保持倍数、切走再切回回到 1 倍；放大后单指拖动跟手速比 = 倍数×2；
 * 贴边继续拖只累计溢出不位移切换——**松手时**累计 ≥60px 用 Pager 原生动画翻上一张/下一张，
 * 不足则 150ms 弹回边界（与 1× 横滑"松手落位"的体验一致）；手势处理沿用头像裁剪的
 * 捏合会话锁。1 倍时不拦截任何单指事件，左右横滑、点按关闭、删除逻辑与原来完全一致。
 */
@Composable
fun PhotoViewerDialog(
    photos: List<Photo>,
    initialId: Long,
    fileOf: (Photo) -> File,
    captionOf: (Photo) -> String,
    onDelete: (Photo) -> Unit,
    onDismiss: () -> Unit,
    zoomable: Boolean = false
) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = photos.indexOfFirst { it.id == initialId }.coerceAtLeast(0),
        pageCount = { photos.size }
    )

    // 双指缩放状态：倍数与平移只作用于当前照片
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // 切换照片（横滑/删除/边缘翻页）后倍数归 1：再切回来从原大小重新看
    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }

    // 删除最后一张后当前页码越界，收回新的末页
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty() && pagerState.currentPage > photos.lastIndex) {
            pagerState.animateScrollToPage(photos.lastIndex)
        }
    }

    val zoomModifier = if (!zoomable) Modifier else Modifier
        .graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
            translationX = zoomOffsetX
            translationY = zoomOffsetY
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                // 一旦明显捏合即锁定为"缩放会话"（同头像裁剪），忽略手指中心移动
                var zooming = false
                // 贴边拖动模型：baseX 恒钳在界内，stretch 是溢出的橡皮筋位移
                var baseX = zoomOffsetX
                var stretch = 0f
                var edgeOverflow = 0f   // 本次手势累计的手指方向溢出（手指 px 口径）
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    val maxX = (size.width * (zoomScale - 1f)) / 2f
                    val maxY = (size.height * (zoomScale - 1f)) / 2f
                    when {
                        pressed.size >= 2 -> {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom < 0.98f || zoom > 1.02f) zooming = true
                            zoomScale = (zoomScale * zoom).coerceIn(PHOTO_ZOOM_MIN, PHOTO_ZOOM_MAX)
                            if (!zooming) {
                                zoomOffsetX = (zoomOffsetX + pan.x).coerceIn(-maxX, maxX)
                                zoomOffsetY = (zoomOffsetY + pan.y).coerceIn(-maxY, maxY)
                                baseX = zoomOffsetX
                                stretch = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // 放大后单指拖动：跟手速比 = 倍数×2；主体钳在图片范围，
                        // 贴边继续拖只累计溢出 + 轻微拉伸，中途不切换
                        pressed.size == 1 && zoomScale > 1f -> {
                            val pan = event.calculatePan()
                            val speed = zoomScale * PAN_SPEED_FACTOR
                            val desiredX = baseX + pan.x * speed
                            val newBaseX = desiredX.coerceIn(-maxX, maxX)
                            val overflow = desiredX - newBaseX
                            val overflowFinger = overflow / speed
                            edgeOverflow = if (overflowFinger == 0f) 0f else edgeOverflow + overflowFinger
                            stretch = (stretch + overflow) * EDGE_RUBBER_BAND
                            baseX = newBaseX
                            zoomOffsetX = baseX + stretch
                            zoomOffsetY = (zoomOffsetY + pan.y * speed).coerceIn(-maxY, maxY)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                // 松手判定（与 1× 横滑翻页的时机一致）：手指没松开不切，松手才落位
                if (abs(edgeOverflow) >= EDGE_SWITCH_OVERFLOW_PX) {
                    val target = pagerState.currentPage + if (edgeOverflow > 0f) -1 else 1
                    if (target in 0 until pagerState.pageCount) {
                        // Pager 原生动画翻页；翻过去新照片从 1× 开始
                        scope.launch { pagerState.animateScrollToPage(target) }
                    } else {
                        // 已是第一张/最后一张：弹回边界不切换
                        val boundX = (size.width * (zoomScale - 1f)) / 2f
                        val anim = Animatable(zoomOffsetX)
                        scope.launch {
                            anim.animateTo(zoomOffsetX.coerceIn(-boundX, boundX), tween(EDGE_SPRING_BACK_MS)) {
                                zoomOffsetX = value
                            }
                        }
                    }
                } else if (stretch != 0f) {
                    // 差一点、没切：150ms 弹回最近边界位置
                    val boundX = (size.width * (zoomScale - 1f)) / 2f
                    val anim = Animatable(zoomOffsetX)
                    scope.launch {
                        anim.animateTo(zoomOffsetX.coerceIn(-boundX, boundX), tween(EDGE_SPRING_BACK_MS)) {
                            zoomOffsetX = value
                        }
                    }
                }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .then(zoomModifier)
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
