package com.gsh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.Exercise
import com.gsh.app.data.TrainingEntry

/**
 * 训练记录页（二级）：今天的训练条目按动作分组展示，可删可加；
 * 与饮食记录同为"逐条添加、即存即生效"模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(vm: GshViewModel, onDone: () -> Unit) {
    val entries by vm.trainingToday.collectAsState()
    val exercises by vm.exercises.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddTrainingDialog(vm = vm, onDismiss = { showAdd = false })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("🏋️ 今日训练") },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    "还没有训练记录，点下方按钮添加一条",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            entries.groupBy { it.exerciseId }.forEach { (exerciseId, list) ->
                val exercise = exercises.find { it.id == exerciseId }
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            exercise?.name ?: "未知动作",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        list.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.display,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { vm.deleteTrainingEntry(entry.id) },
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.padding(0.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 添加训练") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 添加一条训练的对话框（今日页 / 训练记录页共用）：
 * 选动作（内置 + 自定义新建）→ 填重量×次数（器械）或时长分钟（计时）→ 保存即入库。
 */
@Composable
fun AddTrainingDialog(vm: GshViewModel, onDismiss: () -> Unit) {
    val exercises by vm.exercises.collectAsState()
    var selected by remember { mutableStateOf(exercises.firstOrNull()) }
    var showPicker by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }

    // 选中动作后显示"上次"成绩，一键带过
    var lastEntry by remember { mutableStateOf<TrainingEntry?>(null) }
    LaunchedEffect(selected?.id) {
        lastEntry = selected?.let { vm.lastEntryOf(it.id) }
    }

    if (showPicker && selected != null) {
        ExerciseSelectDialog(
            exercises = exercises,
            onPick = {
                if (it != null) selected = it
                showPicker = false
            },
            onAddCustom = { name, group, done ->
                vm.addCustomExercise(name, group) { created ->
                    done(created)
                    if (created != null) selected = created
                }
            },
            onDismiss = { showPicker = false }
        )
    }

    val w = weight.toDoubleOrNull()
    val r = reps.toIntOrNull()
    val d = duration.toIntOrNull()
    val hasStrength = w != null && w > 0 && r != null && r > 0
    val hasDuration = d != null && d > 0
    val valid = selected != null && (hasStrength || hasDuration)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加训练") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selected?.name ?: "选择动作", fontWeight = FontWeight.Bold)
                }
                lastEntry?.let {
                    TextButton(onClick = {
                        weight = it.weightKg?.let { kg -> trimNum(kg) } ?: ""
                        reps = it.reps?.toString() ?: ""
                        duration = it.durationMin?.toString() ?: ""
                    }) {
                        Text("上次：${it.display}（点击带过）", fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("重量") },
                        suffix = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("次数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("时长（计时动作填这个，如平板支撑）") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ex = selected ?: return@TextButton
                    vm.addTrainingEntry(
                        exerciseId = ex.id,
                        weightKg = if (hasStrength) w else null,
                        reps = if (hasStrength) r else null,
                        durationMin = if (!hasStrength && hasDuration) d else null
                    )
                    onDismiss()
                },
                enabled = valid
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 选动作对话框：内置列表 + 新建自定义。 */
@Composable
private fun ExerciseSelectDialog(
    exercises: List<Exercise>,
    onPick: (Exercise?) -> Unit,
    onAddCustom: (String, String, (Exercise?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customGroup by remember { mutableStateOf("腿") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择动作") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                exercises.forEach { e ->
                    TextButton(
                        onClick = { onPick(e) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${e.name}（${e.muscleGroup}）", fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("新建动作", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("动作名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("腿", "背", "胸", "肩", "臂", "核心").forEach { g ->
                        TextButton(
                            onClick = { customGroup = g },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                g,
                                fontSize = 13.sp,
                                color = if (customGroup == g) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (customGroup == g) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        onAddCustom(customName, customGroup) { created ->
                            onPick(created)
                        }
                    },
                    enabled = customName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("添加") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("取消") }
        }
    )
}
