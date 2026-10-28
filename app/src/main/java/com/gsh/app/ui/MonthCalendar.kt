package com.gsh.app.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.DateUtils
import java.time.LocalDate

private const val TAG = "MonthCal"

/**
 * 自绘月历（替换官方 DatePicker——后者不支持在日期格子里加自定义标记）。
 * 周一开始；今天主色高亮、选中主色圆底；未来日期可按需禁用；
 * markOf 返回非空时在日数字下方显示小标记（如 💪 / 😴）。
 */
@Composable
fun MonthCalendar(
    selectedDate: String?,
    onSelectDate: (String) -> Unit,
    allowFuture: Boolean = false,
    markOf: (String) -> String? = { null }
) {
    val today = remember { DateUtils.today() }
    var anchor by remember(selectedDate) {
        mutableStateOf(DateUtils.parse(selectedDate ?: today).withDayOfMonth(1))
    }

    Column {
        // 月份切换头
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { anchor = anchor.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${anchor.year}年${anchor.monthValue}月",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            IconButton(onClick = { anchor = anchor.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }

        // 星期表头（周一开始）
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(
                    w,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 日期网格：固定 6 行、每行固定高度（不足的月份补空行），切月时尺寸纹丝不动
        val firstOffset = anchor.dayOfWeek.value - 1          // 周一=1 → 偏移 0
        val daysInMonth = anchor.lengthOfMonth()
        repeat(6) { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                repeat(7) { c ->
                    val day = r * 7 + c - firstOffset + 1
                    if (day in 1..daysInMonth) {
                        val dateStr = DateUtils.format(anchor.withDayOfMonth(day))
                        val isToday = dateStr == today
                        DayCell(
                            date = dateStr,
                            dayNum = day,
                            isToday = isToday,
                            isSelected = dateStr == selectedDate,
                            enabled = allowFuture || dateStr <= today,
                            // 今天由 DayCell 显示「今」标记，不显示训练/休息小图标
                            mark = markOf(dateStr),
                            onSelect = {
                                Log.d(TAG, "tap $dateStr")
                                onSelectDate(dateStr)
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: String,
    dayNum: Int,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    mark: String?,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(1.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(if (enabled) Modifier.clickable { onSelect() } else Modifier)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$dayNum",
            fontSize = 15.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        // 标记行：今天显示主色「今」，其余显示训练/休息小图标；无标记时占位空串保证每行等高
        val markText = when {
            isToday -> "今"
            mark != null -> mark
            else -> " "
        }
        Text(
            text = markText,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isToday && isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> Color.Unspecified
            }
        )
    }
}
