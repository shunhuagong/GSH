package com.gsh.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.DateUtils
import java.time.LocalDate

/**
 * 训练进步看板：选动作看"每日最好成绩"的走势——
 * 重量动作看重量是否在涨、自重动作看次数是否在涨、计时动作看分钟。
 * 头部显示历史最好 + 较上次变化 + 🔥 新纪录角标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgressScreen(vm: GshViewModel, onDone: () -> Unit) {
    val counts by vm.exerciseCounts.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val progress by vm.progressData.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("📈 训练进步") },
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
            if (counts.isEmpty()) {
                Text(
                    "还没有训练记录，先去记一条吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                // 动作选择条：只列有记录的动作
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counts, key = { it.exerciseId }) { c ->
                        val name = exercises.find { it.id == c.exerciseId }?.name ?: return@items
                        val selected = progress?.exercise?.id == c.exerciseId
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setProgressExercise(c.exerciseId) },
                            label = { Text(name) }
                        )
                    }
                }

                progress?.let { data ->
                    Spacer(Modifier.height(12.dp))

                    // 头部成绩卡：历史最好 + 较上次变化 + 新纪录角标
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🏆 历史最好", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        data.bestPoint?.display ?: "",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    data.bestPoint?.let {
                                        Text(
                                            DateUtils.display(it.date),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (data.isRecord) {
                                    Text(
                                        "🔥 新纪录",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            data.diffText?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    it,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (it.startsWith("↑")) CarbsColor else FatColor
                                )
                            }
                            data.lastPoint?.let {
                                Text(
                                    "最近一次（%s）：%s".format(DateUtils.display(it.date), it.display),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 近90天折线：每日最好一组，一个有训练日一个点
                    val points = data.points.filter {
                        it.date >= DateUtils.format(LocalDate.now().minusDays(89))
                    }
                    if (points.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        val lineColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val minV = points.minOf { it.value }
                            val maxV = points.maxOf { it.value }
                            val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
                            val pad = 14f
                            fun point(i: Int): Offset {
                                val x = pad + (size.width - 2 * pad) * i / (points.size - 1)
                                val y = ((size.height - 2 * pad) *
                                    (1 - (points[i].value - minV) / range) + pad).toFloat()
                                return Offset(x, y)
                            }
                            val path = Path()
                            for (i in points.indices) {
                                val p = point(i)
                                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                            }
                            drawPath(path, lineColor, style = Stroke(width = 4f))
                            points.indices.forEach { i -> drawCircle(lineColor, radius = 5f, center = point(i)) }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                DateUtils.display(points.first().date),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                DateUtils.display(points.last().date),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "这个动作近90天只有 ${points.size} 次记录，再练几次就能看到走势",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
