package com.gsh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
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
                        // 组号为显示层概念：当天该动作按入库顺序编号，删除后自动补位
                        list.forEachIndexed { setIndex, entry ->
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
 * 添加训练：第一步只选动作；选完进入该动作的多组卡片——
 * 组行编辑、整行宽加组、原子保存全部 / 取消零残留。
 * 与饮食记录同为逐条模式，但一次可存多组。
 */
@Composable
fun AddTrainingDialog(vm: GshViewModel, onDismiss: () -> Unit) {
    val exercises by vm.exercises.collectAsState()
    var selected by remember { mutableStateOf<Exercise?>(null) }

    if (selected == null) {
        // 第一步：选动作（取消 = 关闭整个添加）
        ExerciseSelectDialog(
            exercises = exercises,
            onPick = { if (it != null) selected = it else onDismiss() },
            onAddCustom = { name, group, done ->
                vm.addCustomExercise(name, group) { created ->
                    done(created)
                    if (created != null) selected = created
                }
            },
            onDismiss = onDismiss
        )
        return
    }
    MultiSetCardDialog(
        vm = vm,
        exercise = selected!!,
        onChangeExercise = { selected = null },
        onDismiss = onDismiss
    )
}

/** 单组草稿行：三个文本输入（重量 / 次数 / 时长），按合法组合解析。 */
private class DraftRow {
    var weight by mutableStateOf("")
    var reps by mutableStateOf("")
    var duration by mutableStateOf("")

    /** 全空行：保存时跳过 */
    fun isBlankRow() = weight.isBlank() && reps.isBlank() && duration.isBlank()

    /** 解析为 (weight, reps, duration)：合法返回组合，全空/脏组合返回 null */
    fun parse(): Triple<Double?, Int?, Int?>? {
        val w = weight.toDoubleOrNull()
        val r = reps.toIntOrNull()
        val d = duration.toIntOrNull()
        val hasStrength = w != null && w > 0 && r != null && r > 0
        val hasRepsOnly = weight.isBlank() && r != null && r > 0
        val hasDuration = weight.isBlank() && reps.isBlank() && d != null && d > 0
        return when {
            hasStrength -> Triple(w, r, null)
            hasRepsOnly -> Triple(null, r, null)
            hasDuration -> Triple(null, null, d)
            else -> null
        }
    }
}

/** 第二步：某动作的多组卡片。 */
@Composable
private fun MultiSetCardDialog(
    vm: GshViewModel,
    exercise: Exercise,
    onChangeExercise: () -> Unit,
    onDismiss: () -> Unit
) {
    // 上次该动作的全部组（带过规则：第 N 组带上次第 N 组，不足带上一组）
    var lastSets by remember(exercise.id) { mutableStateOf<List<TrainingEntry>>(emptyList()) }
    LaunchedEffect(exercise.id) {
        lastSets = vm.lastSetsOf(exercise.id)
    }

    // 上次全是时长记录 → 计时动作：组行精简为时长单框
    val isDurationType = lastSets.isNotEmpty() &&
        lastSets.all { it.weightKg == null && it.reps == null && it.durationMin != null }

    val rows = remember(exercise.id) { mutableStateListOf<DraftRow>() }

    fun bring(from: TrainingEntry) {
        rows.add(
            DraftRow().apply {
                weight = from.weightKg?.let { trimNum(it) } ?: ""
                reps = from.reps?.toString() ?: ""
                duration = from.durationMin?.toString() ?: ""
            }
        )
    }

    fun addRow() {
        val n = rows.size + 1
        val from = lastSets.getOrNull(n - 1) ?: lastSets.lastOrNull()
        if (from != null) bring(from) else rows.add(DraftRow())
    }

    // 打开卡片：第 1 组自动带过上次第 1 组
    LaunchedEffect(exercise.id, lastSets) {
        if (rows.isEmpty() && lastSets.isNotEmpty()) bring(lastSets.first())
    }

    val parsed = rows.mapNotNull { it.parse() }
    val hasDirty = rows.any { !it.isBlankRow() && it.parse() == null }
    val valid = parsed.isNotEmpty() && !hasDirty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onChangeExercise) {
                    Text("换动作", fontSize = 13.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (lastSets.isNotEmpty()) {
                    val bestDisplay = lastSets.maxByOrNull { it.weightKg ?: 0.0 }?.display ?: ""
                    TextButton(onClick = {
                        rows.clear()
                        lastSets.forEach { bring(it) }
                    }) {
                        Text(
                            "上次 ${lastSets.size} 组，最好 $bestDisplay（点击全部带过）",
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        "自重动作重量可不填；计时动作只填时长",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "第 ${index + 1} 组",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isDurationType) {
                            OutlinedTextField(
                                value = row.duration,
                                onValueChange = { row.duration = it },
                                label = { Text("时长") },
                                suffix = { Text("分钟") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = { row.weight = it },
                                label = { Text("重量") },
                                suffix = { Text("kg") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.reps,
                                onValueChange = { row.reps = it },
                                label = { Text("次数") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.duration,
                                onValueChange = { row.duration = it },
                                label = { Text("时长") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = { rows.removeAt(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "删除该组",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 整行宽加组按钮：训练手汗/镁粉场景下不易误触
                OutlinedButton(
                    onClick = { addRow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (rows.isEmpty()) "＋ 记一组" else "＋ 添加一组")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.saveTrainingEntries(exercise.id, parsed)
                    onDismiss()
                },
                enabled = valid
            ) { Text("保存全部") }
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
