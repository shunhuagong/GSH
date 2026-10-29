package com.gsh.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 训练中页面的单组草稿（重量 kg × 次数），文本态便于直接编辑。 */
private class DraftSet(initialWeight: String, initialReps: String) {
    var weightText by mutableStateOf(initialWeight)
    var repsText by mutableStateOf(initialReps)
}

/** 某动作的录入区块草稿。 */
private class DraftExercise(
    val exercise: Exercise,
    val sets: MutableList<DraftSet>
)

/**
 * 训练中录入页：进入即开始计时；按动作加组，每组重量×次数；
 * 「上次」一键带过历史成绩；完成保存并自动把今天标记为训练日。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(vm: GshViewModel, onDone: () -> Unit) {
    val exercises by vm.exercises.collectAsState()
    val drafts = remember { mutableStateListOf<DraftExercise>() }
    val scope = rememberCoroutineScope()

    // 计时：进入页面开始，每秒刷新
    val startTime = remember { System.currentTimeMillis() }
    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - startTime) / 1000L).toInt()
            delay(1000)
        }
    }
    val timerText = "%d:%02d:%02d".format(elapsedSec / 3600, elapsedSec % 3600 / 60, elapsedSec % 60)

    // 有记录时拦截返回，防误退丢数据
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = drafts.isNotEmpty()) { showExitConfirm = true }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            text = { Text("训练还没保存，退出将丢失已记录的内容。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onDone()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("继续训练") }
            }
        )
    }

    // 添加动作对话框
    var showExercisePicker by remember { mutableStateOf(false) }
    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = exercises,
            usedNames = drafts.map { it.exercise.name }.toSet(),
            onPick = { exercise ->
                showExercisePicker = false
                if (exercise != null) {
                    drafts.add(DraftExercise(exercise, mutableListOf()))
                }
            },
            onAddCustom = { name, group, onDone2 ->
                vm.addCustomExercise(name, group) { created ->
                    onDone2(created)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("🏋️ 训练中 · $timerText") },
            navigationIcon = {
                IconButton(onClick = {
                    if (drafts.isEmpty()) onDone() else { showExitConfirm = true }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            drafts.forEachIndexed { blockIndex, block ->
                Spacer(Modifier.height(12.dp))
                ExerciseBlock(
                    block = block,
                    onBringLast = {
                        scope.launch {
                            val last = vm.lastSetsOf(block.exercise.id)
                            if (last.isNotEmpty()) {
                                block.sets.clear()
                                last.forEach { (w, r) ->
                                    block.sets.add(DraftSet(trimNum(w), "$r"))
                                }
                            }
                        }
                    },
                    onRemove = { drafts.removeAt(blockIndex) }
                )
            }

            if (drafts.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "添加动作开始记录，进入页面即开始计时",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showExercisePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加动作")
            }

            val totalSets = drafts.sumOf { it.sets.size }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val valid = drafts.associate { block ->
                        block.exercise.id to block.sets.mapNotNull { s ->
                            val w = s.weightText.toDoubleOrNull()
                            val r = s.repsText.toIntOrNull()
                            if (w != null && w > 0 && r != null && r > 0) w to r else null
                        }.filter { it.second > 0 }
                    }.filterValues { it.isNotEmpty() }
                    if (valid.isNotEmpty()) {
                        vm.saveWorkout(elapsedSec / 60, valid)
                        onDone()
                    }
                },
                enabled = totalSets > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (totalSets > 0) "完成训练（$totalSets 组）" else "完成训练")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 单个动作的录入区块：上次带过 + 组行编辑 + 加一组。 */
@Composable
private fun ExerciseBlock(
    block: DraftExercise,
    onBringLast: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(block.exercise.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        block.exercise.muscleGroup,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onBringLast) { Text("上次带过") }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "移除动作", modifier = Modifier.size(16.dp))
                }
            }

            block.sets.forEachIndexed { setIndex, set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${setIndex + 1}.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = set.weightText,
                        onValueChange = { set.weightText = it },
                        label = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text("×", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = set.repsText,
                        onValueChange = { set.repsText = it },
                        label = { Text("次") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { block.sets.removeAt(setIndex) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "删除该组", modifier = Modifier.size(14.dp))
                    }
                }
            }

            // 加一组：预填上一组数据，通常只改次数
            val lastSet = block.sets.lastOrNull()
            OutlinedButton(onClick = {
                block.sets.add(DraftSet(lastSet?.weightText ?: "", lastSet?.repsText ?: ""))
            }) {
                Text(if (block.sets.isEmpty()) "＋ 记一组" else "＋ 加一组")
            }
        }
    }
}

/** 选动作对话框：内置列表 + 新建自定义（名称 + 部位）。 */
@Composable
private fun ExercisePickerDialog(
    exercises: List<Exercise>,
    usedNames: Set<String>,
    onPick: (Exercise?) -> Unit,
    onAddCustom: (String, String, (Exercise?) -> Unit) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customGroup by remember { mutableStateOf("腿") }
    AlertDialog(
        onDismissRequest = { onPick(null) },
        title = { Text("选择动作") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                exercises.forEach { e ->
                    val used = e.name in usedNames
                    TextButton(
                        onClick = { onPick(e) },
                        enabled = !used,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(e.name, fontSize = 15.sp)
                            Text(
                                if (used) "已添加" else e.muscleGroup,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("新建动作", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("动作名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("腿", "背", "胸", "肩", "臂", "核心").forEach { g ->
                        TextButton(
                            onClick = { customGroup = g },
                            modifier = Modifier.width(52.dp)
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
                    onClick = { onAddCustom(customName, customGroup) { created -> onPick(created) } },
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
