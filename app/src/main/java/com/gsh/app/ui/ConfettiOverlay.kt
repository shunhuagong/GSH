package com.gsh.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

private class ConfettiParticle(
    val startX: Float,        // 横向起点（0~1 屏宽比例）
    val velocityX: Float,      // 横向漂移速度（每秒屏宽比例）
    val fall: Float,          // 下落速度（每秒屏高比例）
    val size: Float,          // 粒子尺寸 px
    val color: Color,
    val rotationSpeed: Float,
    val initialRotation: Float
)

/**
 * 全达标庆祝彩带。trigger 每变化一次播放一轮；绘制层不拦截点击。
 */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
            Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
            Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFFFF9800),
            Color(0xFFFFC107)
        )
        List(60) {
            ConfettiParticle(
                startX = Random.nextFloat(),
                velocityX = (Random.nextFloat() - 0.5f) * 0.4f,
                fall = 0.55f + Random.nextFloat() * 0.7f,
                size = 10f + Random.nextFloat() * 8f,
                color = colors[Random.nextInt(colors.size)],
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                initialRotation = Random.nextFloat() * 360f
            )
        }
    }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1600, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        // 最后 1/4 时间段内渐隐消失
        val alpha = if (p < 0.75f) 1f else 1f - (p - 0.75f) / 0.25f
        particles.forEach { particle ->
            // p² 模拟重力加速下落
            val x = (particle.startX + particle.velocityX * p) * size.width
            val y = particle.size + particle.fall * p * p * size.height * 1.2f
            rotate(
                degrees = particle.initialRotation + particle.rotationSpeed * p,
                pivot = Offset(x, y)
            ) {
                drawRoundRect(
                    color = particle.color.copy(alpha = alpha),
                    topLeft = Offset(x - particle.size / 2, y - particle.size / 2),
                    size = Size(particle.size, particle.size * 0.6f),
                    cornerRadius = CornerRadius(particle.size / 4)
                )
            }
        }
    }
}
