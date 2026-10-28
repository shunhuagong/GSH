package com.gsh.app.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gsh.app.R
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Photo

/**
 * 「我」页：极简——头像与右上角齿轮进设置。
 * 头像默认用启动插画，右下角 + 角标从相册选图更换。
 */
@Composable
fun MeScreen(vm: GshViewModel, onOpenSettings: () -> Unit) {
    val avatarPhoto: Photo? = vm.avatar.collectAsState().value
    val stats by vm.last30Stats.collectAsState()
    val profile by vm.profile.collectAsState()
    val weightLogs by vm.weightLogs.collectAsState()

    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    // 选完图先弹裁剪界面：缩放/拖动确认后才保存
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val launchers = rememberPhotoLaunchers { uri, _ ->
        uri?.let { cropUri = it }
    }
    cropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onConfirm = { bitmap ->
                vm.changeAvatar(bitmap)
                cropUri = null
            },
            onDismiss = { cropUri = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 挖孔屏的系统状态栏 inset 比可见状态栏高，行整体上移贴近可见顶部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 头像本体仅展示；更换头像的唯一入口是右下角 + 角标
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                ) {
                    if (avatarPhoto != null) {
                        AsyncImage(
                            model = vm.photoFile(avatarPhoto),
                            contentDescription = "头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.splash_illustration),
                            contentDescription = "默认头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { launchers.pickFromGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "更换头像",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }

        // 近30天成绩：打卡天数 / 蛋白达标 / 训练日
        Spacer(Modifier.height(32.dp))
        Text(
            "🔥 近30天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            StatBlock("饮食打卡", "${stats.loggedDays} 天", Modifier.weight(1f))
            StatBlock("蛋白达标", "${stats.proteinHitDays} 天", Modifier.weight(1f))
            StatBlock("训练日", "${stats.trainingDays} 天", Modifier.weight(1f))
            StatBlock("休息日", "${stats.restDays} 天", Modifier.weight(1f))
        }

        // 体重：当前值、较上次变化、近90天折线
        val ninetyDaysAgo = DateUtils.format(DateUtils.parse(DateUtils.today()).minusDays(89))
        val recentLogs = weightLogs.filter { it.date >= ninetyDaysAgo }
        val latestLog = weightLogs.lastOrNull()
        val currentWeight = latestLog?.weightKg ?: profile.weightKg
        val prevLog = weightLogs.getOrNull(weightLogs.size - 2)
        val weightDiff = if (latestLog != null && prevLog != null)
            latestLog.weightKg - prevLog.weightKg else null

        Spacer(Modifier.height(32.dp))
        Text(
            "⚖️ 体重",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    trimNum(currentWeight),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    " kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                weightDiff?.let {
                    Text(
                        if (it >= 0) " ↑%.1f".format(it) else " ↓%.1f".format(it),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (it >= 0) CarbsColor else FatColor,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
            OutlinedButton(onClick = {
                weightInput = trimNum(currentWeight)
                showWeightDialog = true
            }) {
                Text("记体重")
            }
        }

        if (recentLogs.size >= 2) {
            val lineColor = MaterialTheme.colorScheme.primary
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 16.dp)
            ) {
                val minW = recentLogs.minOf { it.weightKg }
                val maxW = recentLogs.maxOf { it.weightKg }
                val range = (maxW - minW).takeIf { it > 0.01 } ?: 1.0
                val pad = 12f
                fun point(i: Int): Offset {
                    val x = pad + (size.width - 2 * pad) * i / (recentLogs.size - 1)
                    val y = ((size.height - 2 * pad) *
                        (1 - (recentLogs[i].weightKg - minW) / range) + pad).toFloat()
                    return Offset(x, y)
                }
                val path = Path()
                for (i in recentLogs.indices) {
                    val p = point(i)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, lineColor, style = Stroke(width = 4f))
                recentLogs.indices.forEach { i -> drawCircle(lineColor, radius = 5f, center = point(i)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    DateUtils.display(recentLogs.first().date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    DateUtils.display(recentLogs.last().date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "多记几次体重，生成变化曲线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        if (showWeightDialog) {
            AlertDialog(
                onDismissRequest = { showWeightDialog = false },
                title = { Text("记录今日体重") },
                text = {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("体重") },
                        suffix = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        weightInput.toDoubleOrNull()?.let { w ->
                            if (w > 0) vm.recordWeight(w)
                        }
                        showWeightDialog = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showWeightDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

/** 三列成绩块：大数字 + 灰色小标签。 */
@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
