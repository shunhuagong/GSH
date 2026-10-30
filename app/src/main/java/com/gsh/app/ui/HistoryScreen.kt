package com.gsh.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.ai.AiUiState
import com.gsh.app.data.DateUtils
import com.gsh.app.data.NutritionTargets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: GshViewModel, onDayClick: (String) -> Unit) {
    val days by vm.historyDays.collectAsState()
    val profile by vm.profile.collectAsState()
    val dayTypes by vm.dayTypes.collectAsState()
    val weeklyReport by vm.weeklyReport.collectAsState()

    if (weeklyReport != AiUiState.Idle) {
        AiAnalysisSheet(
            title = "AI 周报 · 近7天",
            state = weeklyReport,
            onDismiss = vm::dismissWeeklyReport
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("历史记录") })
        // AI 周报基于饮食数据，有饮食记录的日子才出按钮
        if (days.any { it.total != null }) {
            OutlinedButton(
                onClick = vm::requestWeeklyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("🤖 生成近7天 AI 周报")
            }
        }
        if (days.isEmpty()) {
            Text(
                text = "还没有历史记录，先去记录今天吃了什么吧",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(days, key = { it.date }) { day ->
                    val isTraining = dayTypes[day.date] ?: true
                    DailyTotalCard(
                        day = day,
                        isTraining = isTraining,
                        targets = NutritionTargets.from(profile, isTraining),
                        onClick = { onDayClick(day.date) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTotalCard(
    day: GshViewModel.HistoryDay,
    isTraining: Boolean,
    targets: NutritionTargets,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.display(day.date),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isTraining) "💪 训练日" else "😴 休息日",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            val total = day.total
            if (total != null) {
                Text(
                    text = "蛋白 %.1f g · 碳水 %.1f g · 脂肪 %.1f g · %.0f kcal".format(
                        total.protein, total.carbs, total.fat, total.calories
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                MiniBar("蛋白", total.protein, targets.protein, ProteinColor)
                Spacer(Modifier.height(4.dp))
                MiniBar("碳水", total.carbs, targets.carbs, CarbsColor)
                Spacer(Modifier.height(4.dp))
                MiniBar("脂肪", total.fat, targets.fat, FatColor)
            } else {
                // 只练了没吃的日子：营养行显示"—"
                Text(
                    text = "—",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (day.training.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "🏋️ " + day.training.joinToString(" · ") { "${it.first} ${it.second} 组" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniBar(label: String, value: Double, target: Double, color: androidx.compose.ui.graphics.Color) {
    val progress = if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(end = 6.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
        )
    }
}
