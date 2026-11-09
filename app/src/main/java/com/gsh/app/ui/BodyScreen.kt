package com.gsh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Photo

/** 「变化」页：按时间线记录体型照片，前后对比身材变化。 */
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
                onDismiss = { viewingPhoto = null },
                zoomable = true
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitle("变化")
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = DateUtils.display(date),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 当天照片超过可见的 3 张：日期行右端角标提示未显示张数
                                    // 黑色40%透明胶囊底（高20dp、宽随文字自适应，两位数如+10也完整）+白字10sp不加粗
                                    if (dayPhotos.size > 3) {
                                        Box(
                                            modifier = Modifier
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .padding(horizontal = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "+${dayPhotos.size - 3}",
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
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
