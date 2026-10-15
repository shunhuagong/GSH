package com.gsh.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.ai.AiUiState

/** AI 分析结果的通用弹层：加载中 / 错误提示 / 分析文本。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisSheet(title: String, state: AiUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 160.dp, max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(12.dp))
            when (state) {
                is AiUiState.Loading -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在分析…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is AiUiState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
                is AiUiState.Success -> Text(
                    text = state.text,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                else -> {}
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
