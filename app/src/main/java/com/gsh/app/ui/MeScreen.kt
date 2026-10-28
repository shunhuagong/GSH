package com.gsh.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gsh.app.R
import com.gsh.app.data.Photo

/**
 * 「我」页：极简——头像与右上角齿轮进设置。
 * 头像默认用启动插画，右下角 + 角标从相册选图更换。
 */
@Composable
fun MeScreen(vm: GshViewModel, onOpenSettings: () -> Unit) {
    val avatarPhoto: Photo? = vm.avatar.collectAsState().value

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
    }
}
