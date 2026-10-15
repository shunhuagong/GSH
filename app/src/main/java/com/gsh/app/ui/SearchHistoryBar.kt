package com.gsh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 搜索历史栏：横向滚动的关键词 + 清空按钮。
 * 只在 history 非空时展示，由调用方决定显隐时机。
 */
@Composable
fun SearchHistoryBar(
    history: List<String>,
    onTermClick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索历史",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("清空", fontSize = 12.sp)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history) { term ->
                AssistChip(
                    onClick = { onTermClick(term) },
                    label = { Text(term) }
                )
            }
        }
    }
}
