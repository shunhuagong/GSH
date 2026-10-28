package com.gsh.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.MealEntry

/** 从历史某天复制饮食到今天：全屏月历选日期，实时预览内容后确认。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyMealScreen(vm: GshViewModel, onDone: () -> Unit) {
    val previewEntries by vm.copyPreviewEntries.collectAsState()
    val dayTypes by vm.dayTypes.collectAsState()
    val totals by vm.dailyTotals.collectAsState()
    val loggedDates = remember(totals) { totals.map { it.date }.toSet() }

    // 离开页面时清掉预览日期
    DisposableEffect(Unit) {
        onDispose { vm.setCopyPreviewDate(null) }
    }

    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) { vm.setCopyPreviewDate(selected) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("复制饮食到今天") },
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
            MonthCalendar(
                selectedDate = selected,
                onSelectDate = { selected = it },
                allowFuture = false,
                markOf = { date ->
                    if (date in loggedDates) {
                        if (dayTypes[date] ?: true) "💪" else "😴"
                    } else null
                },
                onToggleDayType = { date ->
                    vm.setDayType(date, !(dayTypes[date] ?: true))
                }
            )
            Spacer(Modifier.height(12.dp))
            when {
                selected == null -> Text(
                    "点击日期查看那天的饮食内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                previewEntries.isEmpty() -> Text(
                    "这一天没有饮食记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                else -> {
                    Text("将复制以下 ${previewEntries.size} 条：", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    previewEntries.forEach { e ->
                        Text(
                            "${MealEntry.mealName(e.mealType)} · ${e.foodName} " +
                                trimAmount(e.amount) + e.amountLabel,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val from = selected
                        if (from != null) {
                            vm.copyToToday(from) { }
                            onDone()
                        }
                    },
                    enabled = selected != null && previewEntries.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("确定复制") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun trimAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
