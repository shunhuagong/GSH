package com.gsh.app.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Photo

/** 「变化」页：按时间线记录体型照片，前后对比身材变化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(vm: GshViewModel) {
    val photos by vm.bodyPhotos.collectAsState()
    var viewingPhoto by remember { mutableStateOf<Photo?>(null) }

    val launchers = rememberPhotoLaunchers { uri, tempFile ->
        if (uri != null) vm.addBodyPhoto(uri, tempFile)
    }

    // 查看器：同一天照片左右滑动切换；删除后自动停在下一张，删光自动关闭
    viewingPhoto?.let { entry ->
        val group = photos.filter { it.date == entry.date }
        if (group.isNotEmpty()) {
            PhotoViewerDialog(
                photos = group,
                initialId = entry.id,
                fileOf = vm::photoFile,
                captionOf = { DateUtils.display(it.date) },
                onDelete = { photo -> vm.deletePhoto(photo) },
                onDismiss = { viewingPhoto = null }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("变化") })
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { launchers.takePhoto() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 拍照")
                    }
                    OutlinedButton(
                        onClick = { launchers.pickFromGallery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼 从相册选")
                    }
                }
            }
            if (photos.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp)
                    ) {
                        Text(
                            text = "拍一张现在的体型照片\n坚持记录，回头看自己的变化 💪",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // DAO 已按日期倒序返回，groupBy 保持出现顺序 → 新的日期在前
                photos.groupBy { it.date }.forEach { (date, dayPhotos) ->
                    item(key = date) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = DateUtils.display(date),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(dayPhotos, key = { it.id }) { photo ->
                                        PhotoThumb(
                                            file = vm.photoFile(photo),
                                            size = 110.dp,
                                            onClick = { viewingPhoto = photo }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
