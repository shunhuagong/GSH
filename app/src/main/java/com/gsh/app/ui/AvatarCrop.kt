package com.gsh.app.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val CROP_DECODE_DIMENSION = 2048

/**
 * 头像裁剪：从相册选完图先进入此全屏界面——
 * 拖动 / 双指缩放调整取景，圆框即最终头像效果，点「确认」才保存，取消不落库。
 */
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodeForCrop(context.contentResolver, uri) }
    }
    val bmp = bitmap
    if (bmp == null) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val frame = min(screenW * 0.82f, screenH * 0.44f)   // 圆形裁剪框直径（px）
            val cx = screenW / 2
            val cy = screenH * 0.40f                            // 框中心，底部留按钮位

            // 初始状态：图短边刚好撑满框；scale 是相对初始的倍率，offset 是图中心相对框中心的偏移
            val baseScale = frame / min(bmp.width, bmp.height).toFloat()
            var scale by remember(bmp) { mutableFloatStateOf(1f) }
            var offsetX by remember(bmp) { mutableFloatStateOf(0f) }
            var offsetY by remember(bmp) { mutableFloatStateOf(0f) }

            fun clampOffset() {
                val drawW = bmp.width * baseScale * scale
                val drawH = bmp.height * baseScale * scale
                offsetX = offsetX.coerceIn(-(drawW - frame) / 2f, (drawW - frame) / 2f)
                offsetY = offsetY.coerceIn(-(drawH - frame) / 2f, (drawH - frame) / 2f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bmp) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                when {
                                    // 双指捏合：只缩放，锚点固定在图片中心（放大从中心向外扩），
                                    // 忽略捏合时手指中心漂移带来的平移干扰
                                    pressed.size >= 2 -> {
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                        event.changes.forEach { it.consume() }
                                    }
                                    // 单指拖动：只平移取景
                                    pressed.size == 1 -> {
                                        val pan = event.calculatePan()
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        clampOffset()
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val drawW = bmp.width * baseScale * scale
                val drawH = bmp.height * baseScale * scale
                val density = LocalDensity.current
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset { IntOffset((cx + offsetX - drawW / 2).roundToInt(), (cy + offsetY - drawH / 2).roundToInt()) }
                        .size(
                            with(density) { drawW.toDp() },
                            with(density) { drawH.toDp() }
                        )
                )

                // 遮罩：全屏半透明黑挖出圆孔，圆框白描边，即最终头像预览效果
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mask = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addOval(Rect(cx - frame / 2, cy - frame / 2, cx + frame / 2, cy + frame / 2))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(mask, Color.Black.copy(alpha = 0.55f))
                    drawCircle(
                        color = Color.White,
                        radius = frame / 2,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Text(
                    "拖动、双指缩放调整头像",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color.White, fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            // 圆框（frame px）除以总缩放映射回原图坐标，居中裁正方形
                            val totalScale = baseScale * scale
                            val srcSide = (frame / totalScale).toInt().coerceAtMost(min(bmp.width, bmp.height))
                            val srcLeft = (bmp.width / 2f - offsetX / totalScale - srcSide / 2f)
                                .roundToInt().coerceIn(0, bmp.width - srcSide)
                            val srcTop = (bmp.height / 2f - offsetY / totalScale - srcSide / 2f)
                                .roundToInt().coerceIn(0, bmp.height - srcSide)
                            onConfirm(Bitmap.createBitmap(bmp, srcLeft, srcTop, srcSide, srcSide))
                        }
                    ) {
                        Text("确认", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/**
 * 解码待裁剪图：按 CROP_DECODE_DIMENSION 采样控制内存，再按 EXIF 转正，
 * 与仓库层照片导入同一套口径。
 */
private fun decodeForCrop(resolver: ContentResolver, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= CROP_DECODE_DIMENSION ||
        bounds.outHeight / (sample * 2) >= CROP_DECODE_DIMENSION
    ) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null

    val rotation = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)
    if (rotation != 0) {
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        if (rotated !== bitmap) {
            bitmap.recycle()
            bitmap = rotated
        }
    }
    return bitmap
}
