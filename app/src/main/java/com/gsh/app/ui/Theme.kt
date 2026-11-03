package com.gsh.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0B7),
    secondary = Color(0xFF556B2F),
    tertiary = Color(0xFF00897B),
    surfaceVariant = Color(0xFFEFF5EF),
    background = Color(0xFFFBFDF7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFAED581),
    tertiary = Color(0xFF4DB6AC)
)

/** 三大营养素对应的固定配色，亮暗主题下均易识别。 */
val ProteinColor = Color(0xFF1976D2)
val CarbsColor = Color(0xFF43A047)
val FatColor = Color(0xFFF57C00)

/** 「历史 / 变化 / 食物库」共用的紧凑页头：状态栏内边距 + 顶部 10dp，三页标题高度一致且比 TopAppBar 贴顶。 */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
    )
}

@Composable
fun GshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Material You 动态取色：Android 12+ 跟随系统壁纸配色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
