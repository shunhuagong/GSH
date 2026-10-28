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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gsh.app.R
import com.gsh.app.data.BmiLevel
import com.gsh.app.data.Gender
import com.gsh.app.data.HealthMetrics
import com.gsh.app.data.Photo

/**
 * 「我」页：头像 + 右侧身体信息小字（底部并齐），齿轮进设置编辑。
 * 头像默认用启动插画，右下角 + 角标从相册选图更换。
 */
@Composable
fun MeScreen(vm: GshViewModel, onOpenSettings: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val streak by vm.streak.collectAsState()
    val avatarPhoto: Photo? = vm.avatar.collectAsState().value
    val health = HealthMetrics.from(profile)

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

    val genderLabel = when (profile.gender) {
        Gender.MALE -> "男"
        Gender.FEMALE -> "女"
        null -> null
    }
    val heightLabel = if (profile.heightCm > 0) "${trimNum(profile.heightCm)} cm" else null
    val ageLabel = profile.age()?.let { "$it 岁" }
    val infoLine = listOfNotNull(genderLabel, heightLabel, ageLabel).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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

            Spacer(Modifier.width(12.dp))

            // 身体信息小字，整体与头像底部并齐
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.BottomVertically)
            ) {
                val info = buildString {
                    append(trimNum(profile.weightKg)).append(" kg")
                    if (infoLine.isNotEmpty()) append(" · ").append(infoLine)
                }
                Text(info, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(3.dp))
                if (health != null) {
                    Text(
                        buildAnnotatedString {
                            append("BMI %.1f（".format(health.bmi))
                            withStyle(SpanStyle(color = bmiColor(health.bmiLevel), fontWeight = FontWeight.Bold)) {
                                append(health.bmiLevel.label)
                            }
                            append("）")
                            health.bmr?.let { append(" · 基础代谢约 %.0f kcal/天".format(it)) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "去设置填写身高，可查看 BMI 与基础代谢",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onOpenSettings() }
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "🔥 连续打卡 $streak 天",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.Top)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }
        Text(
            "点击 + 更换头像",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
}

/** BMI 分级配色：健康绿 / 偏瘦、超重橙 / 肥胖红，与设置页口径一致。 */
@Composable
private fun bmiColor(level: BmiLevel) = when (level) {
    BmiLevel.HEALTHY -> CarbsColor
    BmiLevel.UNDERWEIGHT, BmiLevel.OVERWEIGHT -> FatColor
    BmiLevel.OBESE -> MaterialTheme.colorScheme.error
}
