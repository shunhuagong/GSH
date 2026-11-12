package com.gsh.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.DateUtils
import com.gsh.app.data.WeightLog

/**
 * 身体趋势页（二级路由 body_trend，入口在「我」页）：体重 / 体脂率 / 骨骼肌
 * 三胶囊 tab 切换（切数据源不重载），各自画近 90 天 Canvas 折线——点上方标数值、
 * 日期统一标在图表底部一行（与各点 x 对齐），灰色参考值虚线（体重 30/60/90、
 * 体脂率 10/15/20、骨骼肌 25/30/35）、主题色描线、渐变填充；90 天窗口翻页箭头（无更早数据/到今天则置灰）；页右下
 * 「＋ 记录」保存当天身体数据（体重必填、其余可选，没测或 <=0 存 null 不画地板）。
 * 只做记录与展示，不做健康评价；不碰 profile 体重（营养目标独立）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyTrendScreen(vm: GshViewModel, onDone: () -> Unit) {
    val logs by vm.weightLogs.collectAsState()
    val today = DateUtils.today()
    // tab：0=体重 1=体脂率 2=骨骼肌（默认体重）
    var selected by remember { mutableStateOf(0) }
    // 90 天窗口翻页：windowEnd 为窗口右端日期，默认今天；左箭头向更早翻、右箭头向新翻
    var windowEnd by remember { mutableStateOf(today) }
    var showRecord by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    val metricNames = listOf("体重", "体脂率", "骨骼肌")
    // 各指标的数据序列：只取该指标有值的记录（没测为 null 不画点）
    val series: List<Pair<String, Double>> = when (selected) {
        0 -> logs.map { it.date to it.weightKg }
        1 -> logs.mapNotNull { l -> l.bodyFat?.let { l.date to it } }
        else -> logs.mapNotNull { l -> l.muscleKg?.let { l.date to it } }
    }
    val windowStart = DateUtils.format(DateUtils.parse(windowEnd).minusDays(89))
    val visible = series.filter { it.first >= windowStart && it.first <= windowEnd }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("📊 身体趋势") },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 三个胶囊 tab：选中主题色底白字，未选浅灰底黑字
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metricNames.forEachIndexed { index, name ->
                    MetricTab(
                        name = name,
                        selected = selected == index,
                        onClick = { selected = index }
                    )
                }
            }

            // 窗口翻页箭头（右上）：左=更早 90 天，右=更新 90 天；越界置灰
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = {
                        windowEnd = DateUtils.format(DateUtils.parse(windowEnd).minusDays(90))
                    },
                    enabled = series.any { it.first < windowStart }
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "更早")
                }
                IconButton(
                    onClick = {
                        windowEnd = DateUtils.format(DateUtils.parse(windowEnd).plusDays(90))
                            .takeIf { it <= today } ?: today
                    },
                    enabled = windowEnd < today
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "更新")
                }
            }

            if (series.isEmpty()) {
                // 该指标从未记录：引导空态
                Text(
                    "还没有${metricNames[selected]}记录，点下方记录",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else if (visible.isEmpty()) {
                Text(
                    "这个时间段没有${metricNames[selected]}记录，试试向右翻页",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                // 每指标固定参考值（用户 2026-11-12 拍板）：三条灰色虚线并标数值，
                // 轴范围 = 参考值首尾外扩 8%；数据超出参考范围时轴自动外扩兜底
                val refs = when (selected) {
                    0 -> listOf(30.0, 60.0, 90.0)   // 体重 kg
                    1 -> listOf(10.0, 15.0, 20.0)   // 体脂率 %
                    else -> listOf(25.0, 30.0, 35.0) // 骨骼肌 kg
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val refPad = (refs.last() - refs.first()) * 0.08
                    val minY = minOf(refs.first() - refPad, visible.minOf { it.second })
                    val maxY = maxOf(refs.last() + refPad, visible.maxOf { it.second })
                    val pad = 24f
                    fun yPos(v: Double): Float =
                        ((size.height - 2 * pad) * (1 - (v - minY) / (maxY - minY)) + pad).toFloat()
                    fun point(i: Int): Offset {
                        val x = if (visible.size == 1) size.width / 2f
                        else pad + (size.width - 2 * pad) * i / (visible.size - 1)
                        return Offset(x, yPos(visible[i].second))
                    }

                    // 参考值虚线 + 线端数值标注
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    val refGray = Color.Gray.copy(alpha = 0.55f)
                    refs.forEach { ref ->
                        val y = yPos(ref)
                        drawLine(
                            refGray,
                            start = Offset(pad, y),
                            end = Offset(size.width - pad, y),
                            strokeWidth = 1.5f,
                            pathEffect = dash
                        )
                        // 参考值数值标在虚线下方左端：避开数据点上方数值/下方日期的标注区
                        val refLayout = textMeasurer.measure(
                            trimNum(ref),
                            TextStyle(fontSize = 10.sp, color = Color.Gray)
                        )
                        drawText(
                            refLayout,
                            topLeft = Offset(
                                pad + 4f,
                                (y + 2f).coerceAtMost(size.height - refLayout.size.height.toFloat())
                            )
                        )
                    }

                    if (visible.size >= 2) {
                        val line = Path()
                        for (i in visible.indices) {
                            val p = point(i)
                            if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
                        }
                        drawPath(line, lineColor, style = Stroke(width = 4f))
                        // 渐变填充：折线以下从主题色 22% 渐隐到透明
                        val fill = Path().apply {
                            addPath(line)
                            lineTo(size.width - pad, size.height)
                            lineTo(pad, size.height)
                            close()
                        }
                        drawPath(
                            fill,
                            Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }
                    // 数据点 + 数值标注（点上方）+ 时间标注（点下方）
                    visible.indices.forEach { i ->
                        val p = point(i)
                        drawCircle(lineColor, radius = 5f, center = p)
                        val label = trimNum(visible[i].second)
                        val valueLayout = textMeasurer.measure(
                            label,
                            TextStyle(fontSize = 11.sp, color = lineColor, fontWeight = FontWeight.Bold)
                        )
                        drawText(
                            valueLayout,
                            topLeft = Offset(
                                p.x - valueLayout.size.width / 2f,
                                (p.y - valueLayout.size.height - 10f).coerceAtLeast(0f)
                            )
                        )
                        // 日期统一放在图表底部一行（横坐标位置，与各点 x 对齐），不跟随数据点浮动
                        val dateLayout = textMeasurer.measure(
                            visible[i].first.substring(5),
                            TextStyle(fontSize = 10.sp, color = Color.Gray)
                        )
                        drawText(
                            dateLayout,
                            topLeft = Offset(
                                p.x - dateLayout.size.width / 2f,
                                size.height - dateLayout.size.height.toFloat()
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showRecord = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Text("＋ 记录")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRecord) {
        RecordBodyMetricDialog(
            onSave = { weight, fat, muscle ->
                vm.saveBodyMetric(weight, fat, muscle)
                showRecord = false
            },
            onDismiss = { showRecord = false }
        )
    }
}

/** 指标胶囊 tab：选中主题色底白字，未选浅灰底黑字。 */
@Composable
private fun MetricTab(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 记录当天的身体数据：体重必填，体脂率/骨骼肌可选留空（没测或 <=0 存 null）。
 * 同日重复保存 = 覆盖当日记录；只记当天，不做历史补录。
 */
@Composable
private fun RecordBodyMetricDialog(
    onSave: (Double, Double?, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var weightText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var muscleText by remember { mutableStateOf("") }
    val weight = weightText.toDoubleOrNull()
    val canSave = weight != null && weight > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录身体数据") },
        text = {
            Column {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("体重") },
                    suffix = { Text(" kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    label = { Text("体脂率（可选）") },
                    suffix = { Text(" %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = muscleText,
                    onValueChange = { muscleText = it },
                    label = { Text("骨骼肌（可选）") },
                    suffix = { Text(" kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            weight!!,
                            fatText.toDoubleOrNull(),
                            muscleText.toDoubleOrNull()
                        )
                    }
                },
                enabled = canSave
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
