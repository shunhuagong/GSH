package com.gsh.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.Exercise
import com.gsh.app.data.TrainingEntry

/**
 * 今日训练页：按动作卡片长组——选完动作即落页面出现卡片，
 * 点组行就地录入、逐组立即入库；待填组行是页面状态，离开即丢弃（零残留）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(vm: GshViewModel, onDone: () -> Unit) {
    val entries by vm.trainingOfDate.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    // 标题跟日期：今天显示"今日训练"，其他日期显示"MM-dd 训练"
    val isToday = selectedDate == com.gsh.app.data.DateUtils.today()
    val title = if (isToday) "🏋️ 今日训练" else "🏋️ ${selectedDate.substring(5)} 训练"

    // 待填组行（页面状态，不进库）：离开页面自然丢弃，空卡片零残留
    val pendingRows = remember { mutableStateListOf<PendingRow>() }
    // 折叠的卡片（页面局部 UI 状态，不进库）
    val collapsedIds = remember { mutableStateListOf<Long>() }
    var showPicker by remember { mutableStateOf(false) }
    var showNewExercise by remember { mutableStateOf(false) }

    fun addPendingRow(exerciseId: Long) {
        if (pendingRows.none { it.exerciseId == exerciseId }) {
            pendingRows.add(PendingRow(exerciseId))
        }
    }

    if (showPicker) {
        ExerciseSelectDialog(
            exercises = exercises,
            onPick = {
                showPicker = false
                if (it != null) addPendingRow(it.id)
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
            onCreate = { name, group, recordType ->
                vm.addCustomExercise(name, group, recordType) { created ->
                    if (created != null) {
                        showNewExercise = false
                        addPendingRow(created.id)
                    }
                }
            },
            onDismiss = { showNewExercise = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
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
            val cardIds = (entries.groupBy { it.exerciseId }.keys + pendingRows.map { it.exerciseId }).distinct()
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
                val pending = pendingRows.firstOrNull { it.exerciseId == exerciseId }
                val collapsed = exerciseId in collapsedIds
                // 有待填输入行的卡片不允许折叠：输入到一半不能被藏起来看不见
                val canCollapse = pending == null
                val arrowAngle by animateFloatAsState(if (collapsed) 180f else 0f)

                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 头部：点整行折叠/展开；展开态显示进度圆点，折叠态显示组数摘要
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canCollapse) {
                                    if (exerciseId in collapsedIds) {
                                        collapsedIds.remove(exerciseId)
                                    } else {
                                        collapsedIds.add(exerciseId)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    exercise.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (collapsed) {
                                    Text(
                                        "（${exercise.muscleGroup}） · ${savedSets.size} 组",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    // 组进度圆点：N 组亮 N 个，待填行是空心描边点
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(savedSets.size + if (pending != null) 1 else 0) { i ->
                                            val filled = i < savedSets.size
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .then(
                                                        if (filled) Modifier.background(MaterialTheme.colorScheme.primary)
                                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (collapsed) "展开" else "收起",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(arrowAngle)
                            )
                        }

                        AnimatedVisibility(visible = !collapsed) {
                            Column {
                                // 已保存的组：组号按当天入库顺序，删除后自动补位
                                savedSets.forEachIndexed { setIndex, entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "第 ${setIndex + 1} 组  ${entry.display}",
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { vm.deleteTrainingEntry(entry.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "删除该组",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                // 待填组行：行内小输入框直接改，✓ 保存该组
                                pending?.let { row ->
                                    PendingRowInline(
                                        vm = vm,
                                        exercise = exercise,
                                        row = row,
                                        setNo = savedSets.size + 1,
                                        // 保存即加入完成：待填行移除变已保存样式，不自动出现
                                        // 下一待填行，练下一组由用户自己点「＋」
                                        onSaved = { pendingRows.remove(row) },
                                        // ✕ 取消：丢弃待填行不写库；当天无已保存组时整卡随之消失
                                        onDiscard = { pendingRows.remove(row) }
                                    )
                                }
                                // 无边框小号「＋」：只负责开下一行，不自动保存当前待填行
                                // （防预填数字被误存成一组）；有待填行时灰显，✓/✕ 处理完才恢复
                                TextButton(
                                    onClick = { addPendingRow(exerciseId) },
                                    enabled = pending == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "＋",
                                        fontSize = 14.sp,
                                        color = if (pending == null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text("＋ 添加训练", fontSize = 15.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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

/** 新建动作：名称 + 部位 + 记录方式；重名提示；创建成功直接选中回到训练页。 */
@Composable
private fun NewExerciseDialog(
    exercises: List<Exercise>,
    onCreate: (String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("腿") }
    var recordType by remember { mutableStateOf(Exercise.TYPE_STRENGTH) }
    val duplicated = exercises.any { it.name == name.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建动作") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Text("部位", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                Text("记录方式", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                listOf(
                    Exercise.TYPE_STRENGTH to "重量 × 次数（器械/负重，如深蹲、卧推）",
                    Exercise.TYPE_REPS to "纯次数（自重，如俯卧撑、引体）",
                    Exercise.TYPE_DURATION to "计时时长（如平板支撑、悬垂）"
                ).forEach { (t, label) ->
                    TextButton(
                        onClick = { recordType = t },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (recordType == t) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (recordType == t) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, group, recordType) },
                enabled = name.isNotBlank() && !duplicated
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 待填组行：页面状态，重量/次数/时长的行内输入草稿。 */
private class PendingRow(val exerciseId: Long) {
    var weight by mutableStateOf("")
    var reps by mutableStateOf("")
    var duration by mutableStateOf("")
}

/** 按 recordType 校验待填行；合法返回 (weight, reps, duration)，否则 null。 */
private fun parsePending(row: PendingRow, recordType: Int): Triple<Double?, Int?, Int?>? {
    val w = row.weight.toDoubleOrNull()
    val r = row.reps.toIntOrNull()
    val d = row.duration.toIntOrNull()
    return when (recordType) {
        Exercise.TYPE_STRENGTH ->
            if (w != null && w > 0 && r != null && r > 0) Triple(w, r, null) else null
        Exercise.TYPE_REPS ->
            if (r != null && r > 0) Triple(null, r, null) else null
        Exercise.TYPE_DURATION ->
            if (d != null && d > 0) Triple(null, null, d) else null
        else -> when {
            w != null && w > 0 && r != null && r > 0 -> Triple(w, r, null)
            row.weight.isBlank() && r != null && r > 0 -> Triple(null, r, null)
            row.weight.isBlank() && row.reps.isBlank() && d != null && d > 0 -> Triple(null, null, d)
            else -> null
        }
    }
}

private fun savePending(vm: GshViewModel, exercise: Exercise, parsed: Triple<Double?, Int?, Int?>) {
    vm.addTrainingEntry(exercise, parsed.first, parsed.second, parsed.third)
}

/** 待填组行：组号徽章 + 行内小输入框（单位印框内、无提示词）+ ✕ 取消 + ✓ 保存该组。 */
@Composable
private fun PendingRowInline(
    vm: GshViewModel,
    exercise: Exercise,
    row: PendingRow,
    setNo: Int,
    onSaved: () -> Unit,
    onDiscard: () -> Unit
) {
    // 行出现时预填上次第 N 组（不足带上一组，无历史留空）
    LaunchedEffect(row) {
        val last = vm.lastSetsOf(exercise.id)
        val from = last.getOrNull(setNo - 1) ?: last.lastOrNull()
        if (from != null) {
            row.weight = from.weightKg?.let { trimNum(it) } ?: ""
            row.reps = from.reps?.toString() ?: ""
            row.duration = from.durationMin?.toString() ?: ""
        }
    }
    val type = exercise.recordType
    val parsed = parsePending(row, type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 组号文字徽章：与已保存行「第 N 组」同语言，统一最小宽度保证左缘对齐
        Text(
            "第 $setNo 组",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        when (type) {
            Exercise.TYPE_STRENGTH -> {
                InlineNumField(row.weight, { row.weight = it }, "kg", KeyboardType.Decimal, Modifier.width(72.dp))
                InlineNumField(row.reps, { row.reps = it }, "次", KeyboardType.Number, Modifier.width(56.dp))
                Spacer(Modifier.weight(1f))
            }
            Exercise.TYPE_REPS -> {
                InlineNumField(row.reps, { row.reps = it }, "次", KeyboardType.Number, Modifier.weight(1f))
            }
            Exercise.TYPE_DURATION -> {
                InlineNumField(row.duration, { row.duration = it }, "分钟", KeyboardType.Number, Modifier.weight(1f))
            }
            else -> {
                // 存量未定类型：三框兼容，首次保存后类型固定
                InlineNumField(row.weight, { row.weight = it }, "kg", KeyboardType.Decimal, Modifier.width(72.dp))
                InlineNumField(row.reps, { row.reps = it }, "次", KeyboardType.Number, Modifier.width(56.dp))
                InlineNumField(row.duration, { row.duration = it }, "分钟", KeyboardType.Number, Modifier.width(64.dp))
                Spacer(Modifier.weight(1f))
            }
        }
        // ✕ 取消 + ✓ 保存：钉在行右缘，与已保存行垃圾桶同一竖线，两按钮间 4dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "取消该组")
            }
            IconButton(
                onClick = {
                    parsed?.let { savePending(vm, exercise, it) }
                    onSaved()
                },
                enabled = parsed != null,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "保存该组")
            }
        }
    }
}

/**
 * 行内数字输入框：无提示词（单位即说明）、48dp 高、8dp 横向内边距、单位印框内
 * 右侧同基线、数字右对齐。M3 的 OutlinedTextField 无 contentPadding 参数，
 * 需求文档的紧凑尺寸/内边距用 BasicTextField 自绘实现（聚焦时描边加亮）。
 */
@Composable
private fun InlineNumField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    keyboardType: KeyboardType,
    modifier: Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        interactionSource = interactionSource,
        decorationBox = { innerField ->
            Row(
                modifier = modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    innerField()
                }
                Text(unit, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
