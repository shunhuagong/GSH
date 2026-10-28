package com.gsh.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.gsh.app.data.BmiLevel
import com.gsh.app.data.Gender
import com.gsh.app.data.HealthMetrics

/**
 * 「我」页：个人信息与健康指标的展示位。
 * 头像取最新体型照片（点击进「变化」页）；编辑入口在右上角齿轮 → 设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(vm: GshViewModel, onOpenSettings: () -> Unit, onOpenBody: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val streak by vm.streak.collectAsState()
    val bodyPhotos by vm.bodyPhotos.collectAsState()
    val latestBodyPhoto = bodyPhotos.firstOrNull()
    val health = HealthMetrics.from(profile)

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
    ) {
        TopAppBar(
            title = { Text("我") },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))

            // 头像：最新体型照，点击进「变化」页；无照片用占位图标
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenBody() },
                contentAlignment = Alignment.Center
            ) {
                if (latestBodyPhoto != null) {
                    AsyncImage(
                        model = vm.photoFile(latestBodyPhoto),
                        contentDescription = "最新体型照",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (latestBodyPhoto == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "拍一张体型照，作为你的头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
