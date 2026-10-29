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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.Exercise
import com.gsh.app.data.TrainingEntry
import kotlinx.coroutines.launch

/**
 * 今日训练页：按动作卡片长组——选完动作即落页面出现卡片，
 * 点组行就地录入、逐组立即入库；待填组行是页面状态，离开即丢弃（零残留）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(vm: GshViewModel, onDone: () -> Unit) {
    val entries by vm.trainingToday.collectAsState()
    val exercises by vm.exercises.collectAsState()

    // 待填组行所在动作（页面状态，不进库）：空卡片离开页面自然丢弃
    val pendingExercises = remember { mutableStateListOf<Long>() }
    var showPicker by remember { mutableStateOf(false) }
    var showNewExercise by remember { mutableStateOf(false) }
    // 正在录入的组：动作 + 组号（第 N 组）
    var entryFor by remember { mutableStateOf<Pair<Exercise, Int>?>(null) }

    if (showPicker) {
        ExerciseSelectDialog(
            exercises = exercises,
            onPick = {
                showPicker = false
                if (it != null && it.id !in pendingExercises) pendingExercises.add(it.id)
            },
            onNewExercise = {
                showPicker = false
                showNewExercise = true
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showNewExercise) {
        NewExerciseDialog(
            exercises = exercises,
            onCreate = { name, group ->
                vm.addCustomExercise(name, group) { created ->
                    if (created != null) {
                        showNewExercise = false
                        if (created.id !in pendingExercises) pendingExercises.add(created.id)
                    }
                }
            },
            onDismiss = { showNewExercise = false }
        )
    }

    entryFor?.let { (exercise, setNo) ->
        SetEntryDialog(
            vm = vm,
            exercise = exercise,
            setNo = setNo,
            onSaved = {
                pendingExercises.remove(exercise.id)
                entryFor = null
            },
            onDismiss = { entryFor = null }
        )
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
            val cardIds = (entries.groupBy { it.exerciseId }.keys + pendingExercises).distinct()
            if (cardIds.isEmpty()) {
                Text(
                    "还没有训练记录，点下方按钮选动作开始",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            cardIds.forEach { exerciseId ->
                val exercise = exercises.find { it.id == exerciseId } ?: return@forEach
                val savedSets = entries.filter { it.exerciseId == exerciseId }
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${exercise.name}（${exercise.muscleGroup}）",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        // 已保存的组：组号按当天入库顺序，删除后自动补位
                        savedSets.forEachIndexed { setIndex, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "第 ${setIndex + 1} 组  ${entry.display}",
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { vm.deleteTrainingEntry(entry.id) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "删除该组")
                                }
                            }
                        }
                        // 待填组行：点击弹录入框；组号 = 已保存组数 + 1
                        if (exerciseId in pendingExercises) {
                            OutlinedButton(
                                onClick = { entryFor = exercise to (savedSets.size + 1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text("第 ${savedSets.size + 1} 组 · 点击填写")
                            }
                        }
                        // 整行宽加组：训练手上汗/镁粉场景不易误触
                        OutlinedButton(
                            onClick = {
                                if (exerciseId !in pendingExercises) {
                                    pendingExercises.add(exerciseId)
                                } else {
                                    entryFor = exercise to (savedSets.size + 1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (savedSets.isEmpty() && exerciseId !in pendingExercises) "＋ 记第一组" else "＋ 添加一组")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 添加训练（新动作）") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 单组录入框：点待填组行弹出；预填上次第 N 组（不足带上一组，无历史留空），
 * 填完确认立即保存这一组；取消行保留待填。
 */
@Composable
private fun SetEntryDialog(
    vm: GshViewModel,
    exercise: Exercise,
    setNo: Int,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }

    // 预填：上次该动作的第 setNo 组；上次不足 N 组则带上一组；无历史留空
    var lastSets by remember(exercise.id) { mutableStateOf<List<TrainingEntry>>(emptyList()) }
    LaunchedEffect(exercise.id) {
        lastSets = vm.lastSetsOf(exercise.id)
        val from = lastSets.getOrNull(setNo - 1) ?: lastSets.lastOrNull()
        if (from != null) {
            weight = from.weightKg?.let { trimNum(it) } ?: ""
            reps = from.reps?.toString() ?: ""
            duration = from.durationMin?.toString() ?: ""
        }
    }
    // 上次全是时长记录 → 计时动作：单框
    val isDurationType = lastSets.isNotEmpty() &&
        lastSets.all { it.weightKg == null && it.reps == null && it.durationMin != null }

    val w = weight.toDoubleOrNull()
    val r = reps.toIntOrNull()
    val d = duration.toIntOrNull()
    val hasStrength = w != null && w > 0 && r != null && r > 0
    val hasRepsOnly = weight.isBlank() && r != null && r > 0
    val hasDuration = weight.isBlank() && reps.isBlank() && d != null && d > 0
    val valid = hasStrength || hasRepsOnly || hasDuration

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${exercise.name} · 第 $setNo 组") },
        text = {
            Column {
                Text(
                    "自重动作重量可不填；计时动作只填时长",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isDurationType) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("时长") },
                        suffix = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                        label = { Text("时长（计时动作填这个）") },
                        suffix = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.addTrainingEntry(
                        exerciseId = exercise.id,
                        weightKg = if (hasStrength) w else null,
                        reps = if (hasStrength || hasRepsOnly) r else null,
                        durationMin = if (hasDuration) d else null
                    )
                    onSaved()
                },
                enabled = valid
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 动作选择：只放列表；底部整行宽「＋ 新建动作」入口。 */
@Composable
private fun ExerciseSelectDialog(
    exercises: List<Exercise>,
    onPick: (Exercise?) -> Unit,
    onNewExercise: () -> Unit,
    onDismiss: () -> Unit
) {
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
                OutlinedButton(
                    onClick = onNewExercise,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 新建动作") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("取消") }
        }
    )
}

/** 新建动作：独立对话框（名称 + 部位），重名提示；创建成功直接选中回到训练页。 */
@Composable
private fun NewExerciseDialog(
    exercises: List<Exercise>,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("腿") }
    val duplicated = exercises.any { it.name == name.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建动作") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("动作名称") },
                    isError = duplicated,
                    supportingText = {
                        if (duplicated) Text("已存在同名动作")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("腿", "背", "胸", "肩", "臂", "核心").forEach { g ->
                        TextButton(
                            onClick = { group = g },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                g,
                                fontSize = 13.sp,
                                color = if (group == g) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (group == g) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, group) },
                enabled = name.isNotBlank() && !duplicated
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
