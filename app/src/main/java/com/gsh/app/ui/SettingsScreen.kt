package com.gsh.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gsh.app.data.AiConfig
import com.gsh.app.data.BmiLevel
import com.gsh.app.data.Gender
import com.gsh.app.data.HealthMetrics
import com.gsh.app.data.UserProfile
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GshViewModel) {
    val profile by vm.profile.collectAsState()
    val aiConfig by vm.aiConfig.collectAsState()

    var weight by remember(profile) { mutableStateOf(trimNum(profile.weightKg)) }
    var height by remember(profile) {
        mutableStateOf(if (profile.heightCm > 0) trimNum(profile.heightCm) else "")
    }
    // 输入年龄，内部换算成出生年存储（明年打开自动 +1，无需手动改）
    var age by remember(profile) { mutableStateOf(profile.age()?.toString() ?: "") }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var protein by remember(profile) { mutableStateOf(trimNum(profile.proteinPerKg)) }
    var carbsTraining by remember(profile) { mutableStateOf(trimNum(profile.carbsTrainingPerKg)) }
    var carbsRest by remember(profile) { mutableStateOf(trimNum(profile.carbsRestPerKg)) }
    var fat by remember(profile) { mutableStateOf(trimNum(profile.fatPerKg)) }

    var aiKey by remember(aiConfig) { mutableStateOf(aiConfig.apiKey) }
    var aiBaseUrl by remember(aiConfig) { mutableStateOf(aiConfig.baseUrl) }
    var aiModel by remember(aiConfig) { mutableStateOf(aiConfig.model) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val dynamicColor by vm.dynamicColor.collectAsState()
    val context = LocalContext.current

    exportError?.let { message ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text("好") }
            }
        )
    }

    val w = weight.toDoubleOrNull()
    val h = height.toDoubleOrNull()
    val ageNum = age.toIntOrNull()
    val p = protein.toDoubleOrNull()
    val ct = carbsTraining.toDoubleOrNull()
    val cr = carbsRest.toDoubleOrNull()
    val f = fat.toDoubleOrNull()
    // 身高 / 年龄为选填：留空视为未填写，填了则必须在合理范围
    val heightFilled = h != null && h in 50.0..280.0
    val heightValid = height.isBlank() || heightFilled
    val ageValid = age.isBlank() || (ageNum != null && ageNum in 10..100)
    val valid = w != null && w > 0 && p != null && p > 0 &&
        ct != null && ct > 0 && cr != null && cr > 0 && f != null && f >= 0 &&
        heightValid && ageValid

    // 根据当前输入实时计算健康指标（BMI / 分级 / 基础代谢）
    val health = if (w != null && w > 0 && heightFilled) {
        HealthMetrics.from(
            UserProfile(
                weightKg = w, heightCm = h!!, gender = gender,
                birthYear = if (ageNum != null) LocalDate.now().year - ageNum else 0
            )
        )
    } else null

    // 根据当前输入实时计算两套目标
    val tProtein = if (w != null && p != null) w * p else 0.0
    val tCarbsTraining = if (w != null && ct != null) w * ct else 0.0
    val tCarbsRest = if (w != null && cr != null) w * cr else 0.0
    val tFat = if (w != null && f != null) w * f else 0.0
    val tCaloriesTraining = tProtein * 4 + tCarbsTraining * 4 + tFat * 9
    val tCaloriesRest = tProtein * 4 + tCarbsRest * 4 + tFat * 9

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("设置") })

        Column(modifier = Modifier.padding(16.dp)) {
            Text("外观", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("动态取色")
                    Text(
                        "跟随系统壁纸自动配色（Material You，Android 12+），关闭后使用默认绿色主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = vm::setDynamicColor)
            }

            Spacer(Modifier.height(16.dp))
            Text("身体信息", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("体重") },
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("性别")
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = gender == Gender.MALE,
                    onClick = { gender = Gender.MALE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("男") }
                SegmentedButton(
                    selected = gender == Gender.FEMALE,
                    onClick = { gender = Gender.FEMALE },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("女") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("身高") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("年龄") },
                    suffix = { Text("岁") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (health != null) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
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
                            "该身高的健康体重范围：%.1f ~ %.1f kg"
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
                                style = MaterialTheme.typography.bodySmall,
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
            }

            Spacer(Modifier.height(16.dp))
            Text("增肌营养系数", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "目标 = 体重 × 每公斤摄入量；常见增肌参考：蛋白 1.6~2.2、" +
                    "碳水训练日 4~6 / 休息日 2.5~3.5、脂肪 0.6~1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = protein,
                onValueChange = { protein = it },
                label = { Text("蛋白质") },
                suffix = { Text("g/kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = carbsTraining,
                onValueChange = { carbsTraining = it },
                label = { Text("碳水（训练日）") },
                suffix = { Text("g/kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = carbsRest,
                onValueChange = { carbsRest = it },
                label = { Text("碳水（休息日）") },
                suffix = { Text("g/kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = fat,
                onValueChange = { fat = it },
                label = { Text("脂肪") },
                suffix = { Text("g/kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("每日营养目标", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("💪 训练日", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "蛋白 %.0f g · 碳水 %.0f g · 脂肪 %.0f g · %.0f kcal"
                            .format(tProtein, tCarbsTraining, tFat, tCaloriesTraining),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("😴 休息日", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "蛋白 %.0f g · 碳水 %.0f g · 脂肪 %.0f g · %.0f kcal"
                            .format(tProtein, tCarbsRest, tFat, tCaloriesRest),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.updateProfile(
                        UserProfile(
                            weightKg = w!!, heightCm = if (heightFilled) h!! else 0.0,
                            gender = gender,
                            birthYear = if (age.isNotBlank() && ageNum != null)
                                LocalDate.now().year - ageNum else 0,
                            proteinPerKg = p!!, carbsTrainingPerKg = ct!!,
                            carbsRestPerKg = cr!!, fatPerKg = f!!
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }

            Spacer(Modifier.height(24.dp))
            Text("AI 分析服务", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "默认智谱 BigModel（glm-4-flash 免费，在 bigmodel.cn 注册获取 API Key），" +
                    "也支持 DeepSeek 等 OpenAI 兼容服务，修改接口地址与模型名即可。" +
                    "开启后，饮食营养数据会发送给你配置的服务商用于分析。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiKey,
                onValueChange = { aiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it },
                label = { Text("接口地址（OpenAI 兼容）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiModel,
                onValueChange = { aiModel = it },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.updateAiConfig(
                        AiConfig(baseUrl = aiBaseUrl.trim(), apiKey = aiKey.trim(), model = aiModel.trim())
                    )
                },
                enabled = aiKey.isNotBlank() && aiBaseUrl.isNotBlank() && aiModel.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 AI 配置")
            }

            Spacer(Modifier.height(24.dp))
            Text("数据备份", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "导出全部饮食记录、训练日标记与身体信息，可通过微信、QQ 或邮件发送保存，防止换机丢失。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.exportCsv { file ->
                            if (file == null) exportError = "导出失败，请重试"
                            else shareFile(context, file, "text/csv")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 CSV")
                }
                OutlinedButton(
                    onClick = {
                        vm.exportJson { file ->
                            if (file == null) exportError = "导出失败，请重试"
                            else shareFile(context, file, "application/json")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 JSON")
                }
            }
        }
    }
}

/** 整数不带小数点；小数先四舍五入到两位，去掉 Float 存储带来的精度尾巴（如 0.8 读回 0.800000011…）。 */
private fun trimNum(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** 用系统分享面板发出导出文件。 */
private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "导出数据"))
}
