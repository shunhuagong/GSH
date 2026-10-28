package com.gsh.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gsh.app.R
import com.gsh.app.data.BmiLevel
import com.gsh.app.data.Gender
import com.gsh.app.data.HealthMetrics
import com.gsh.app.data.Photo

/**
 * 「我」页：个人信息与健康指标的展示位。
 * 头像默认用启动插画，点击从相册选图更换；编辑入口在右上角齿轮 → 设置。
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
        // 右上角齿轮进设置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 头像：相册选的（点击更换）；未设置时用启动插画作默认头像
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable { launchers.pickFromGallery() },
                contentAlignment = Alignment.Center
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
            Spacer(Modifier.height(4.dp))
            Text(
                "点击头像可更换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 个人信息卡
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${trimNum(profile.weightKg)} kg",
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                    if (infoLine.isNotEmpty()) {
                        Text(
                            infoLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "去设置页完善性别 / 身高 / 年龄",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onOpenSettings() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🔥 连续打卡 $streak 天",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 健康指标卡（BMI / 基础代谢），点击进设置编辑
            if (health != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettings() }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "BMI %.1f".format(health.bmi),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                health.bmiLevel.label,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = when (health.bmiLevel) {
                                    BmiLevel.HEALTHY -> CarbsColor
                                    BmiLevel.UNDERWEIGHT, BmiLevel.OVERWEIGHT -> FatColor
                                    BmiLevel.OBESE -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "健康体重范围：%.1f ~ %.1f kg"
                                .format(health.healthyWeightRange.start, health.healthyWeightRange.endInclusive),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (health.bmr != null) {
                            Text(
                                "基础代谢约 %.0f kcal/天（Mifflin-St Jeor 公式）"
                                    .format(health.bmr),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "填写性别与年龄后可查看基础代谢",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "增肌期肌肉量大，BMI 偏高属正常，仅供参考",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("健康指标", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "填写身高与体重后，这里会展示 BMI 分级与健康体重范围。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenSettings) {
                            Text("去设置填写")
                        }
                    }
                }
            }
        }
    }
}
