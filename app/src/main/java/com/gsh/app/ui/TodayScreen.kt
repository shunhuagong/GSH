package com.gsh.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gsh.app.ai.AiUiState
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Exercise
import com.gsh.app.data.Food
import com.gsh.app.data.MealEntry
import com.gsh.app.data.NutritionTargets
import com.gsh.app.data.Photo
import com.gsh.app.data.TrainingEntry
import java.io.File

/** 今日页训练入口行：训练日显示；无记录时引导添加，有记录时显示动作×条数。 */
@Composable
private fun TrainingTodayRow(
    entries: List<TrainingEntry>,
    exercises: List<Exercise>,
    onAdd: () -> Unit
) {
    val summary = entries.groupBy { it.exerciseId }.entries
        .mapNotNull { (id, list) -> exercises.find { it.id == id }?.let { it.name to list.size } }
        .joinToString(" · ") { "${it.first} ×${it.second}" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary.ifEmpty { "记一下今天的训练" },
                fontSize = 14.sp,
                fontWeight = if (summary.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加训练",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** onAddClick 参数为餐别（0~3），-1 表示由添加页按时间自动选择。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: GshViewModel, onAddClick: (Int) -> Unit, onCopyClick: () -> Unit) {
    val date by vm.selectedDate.collectAsState()
    val entries by vm.entriesForDate.collectAsState()
    val targets by vm.targets.collectAsState()
    val isTrainingDay by vm.isTrainingDay.collectAsState()
    val dailyAnalysis by vm.dailyAnalysis.collectAsState()
    val streak by vm.streak.collectAsState()
    val mealPhotos by vm.mealPhotos.collectAsState()
    // 月历标记：有记录的日子按训练/休息显示 💪/😴
    val dayTypes by vm.dayTypes.collectAsState()
    val totals by vm.dailyTotals.collectAsState()
    // 训练记录（今日入口）
    val trainingToday by vm.trainingToday.collectAsState()
    val exercises by vm.exercises.collectAsState()
    var showTrainingAdd by remember { mutableStateOf(false) }
    if (showTrainingAdd) {
        AddTrainingDialog(vm = vm, onDismiss = { showTrainingAdd = false })
    }

    var editingEntry by remember { mutableStateOf<MealEntry?>(null) }
    var celebrationTick by remember { mutableStateOf(0) }
    var celebrated by remember { mutableStateOf(false) }
    var pendingPhotoMeal by remember { mutableStateOf<Int?>(null) }
    var viewingPhoto by remember { mutableStateOf<Photo?>(null) }

    // 拍照/选图回调：从上下文取出待打卡的餐次，交给仓库导入
    val photoLaunchers = rememberPhotoLaunchers { uri, tempFile ->
        val meal = pendingPhotoMeal
        pendingPhotoMeal = null
        if (uri != null && meal != null) {
            vm.addMealPhoto(uri, meal, tempFile)
        }
    }

    // 三大营养素全部达标时，触发一次庆祝彩带（删掉记录后重置，可再次触发）
    val allMet = entries.isNotEmpty() &&
        entries.sumOf { it.protein } >= targets.protein &&
        entries.sumOf { it.carbs } >= targets.carbs &&
        entries.sumOf { it.fat } >= targets.fat
    LaunchedEffect(allMet) {
        if (allMet && !celebrated) {
            celebrated = true
            celebrationTick++
        } else if (!allMet) {
            celebrated = false
        }
    }

    if (dailyAnalysis != AiUiState.Idle) {
        AiAnalysisSheet(
            title = "AI 饮食分析 · ${DateUtils.display(date)}",
            state = dailyAnalysis,
            onDismiss = vm::dismissDailyAnalysis
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { amount ->
                vm.updateEntryAmount(entry, amount)
                editingEntry = null
            }
        )
    }

    pendingPhotoMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingPhotoMeal = null },
            title = { Text("添加${MealEntry.mealName(meal)}照片") },
            text = { Text("拍照或从相册选一张，打卡这一餐") },
            confirmButton = {
                TextButton(onClick = { photoLaunchers.takePhoto() }) {
                    Text("📷 拍照")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoLaunchers.pickFromGallery() }) {
                    Text("🖼 从相册选")
                }
            }
        )
    }

    // 查看器：同餐次照片左右滑动切换；删除后自动停在下一张，删光自动关闭
    viewingPhoto?.let { entry ->
        val group = mealPhotos.filter { it.mealType == entry.mealType }
        if (group.isNotEmpty()) {
            PhotoViewerDialog(
                photos = group,
                initialId = entry.id,
                fileOf = vm::photoFile,
                captionOf = { "${DateUtils.display(it.date)} · ${MealEntry.mealName(it.mealType)}" },
                onDelete = { photo -> vm.deletePhoto(photo) },
                onDismiss = { viewingPhoto = null }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            val loggedDates = remember(totals) { totals.map { it.date }.toSet() }
            DateHeader(
                date = date,
                onChange = vm::selectDate,
                streak = streak,
                markOf = { d ->
                    if (d in loggedDates) {
                        if (dayTypes[d] ?: true) "💪" else "😴"
                    } else null
                }
            )
        }
        item {
            DayTypeToggle(
                isTraining = isTrainingDay,
                onChange = { vm.setDayType(date, it) }
            )
        }
        if (isTrainingDay) {
            item {
                TrainingTodayRow(
                    entries = trainingToday,
                    exercises = exercises,
                    onAdd = { showTrainingAdd = true }
                )
            }
        }
        item { NutritionSummaryCard(entries = entries, targets = targets) }
        item {
            OutlinedButton(
                onClick = vm::requestDailyAnalysis,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🤖 AI 分析饮食")
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日饮食",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (date == DateUtils.today()) {
                    TextButton(onClick = onCopyClick) {
                        Text("复制饮食", fontSize = 13.sp)
                    }
                }
            }
        }
        (0 until MealEntry.MEAL_COUNT).forEach { mealType ->
            val mealEntries = entries.filter { it.mealType == mealType }
            item(key = "meal_$mealType") {
                MealSection(
                    mealType = mealType,
                    entries = mealEntries,
                    photos = mealPhotos.filter { it.mealType == mealType },
                    onAdd = { onAddClick(mealType) },
                    onPhoto = { pendingPhotoMeal = mealType },
                    onViewPhoto = { viewingPhoto = it },
                    photoFile = vm::photoFile,
                    onDelete = vm::deleteEntry,
                    onEdit = { editingEntry = it }
                )
            }
        }
        }
        ConfettiOverlay(
            trigger = celebrationTick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DateHeader(
    date: String,
    onChange: (String) -> Unit,
    streak: Int,
    markOf: (String) -> String?
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        // 两段式：第一下仅选中，再点同一天才确认跳转；关闭弹窗后选中自动重置
        var pickedDate by remember(showPicker) { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(8.dp)
            ) {
                MonthCalendar(
                    selectedDate = pickedDate ?: date,
                    onSelectDate = { clicked ->
                        if (pickedDate == clicked) {
                            showPicker = false
                            onChange(clicked)
                        } else {
                            pickedDate = clicked
                        }
                    },
                    allowFuture = true,
                    markOf = markOf
                )
            }
        }
    }

    val localDate = DateUtils.parse(date)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onChange(DateUtils.format(localDate.minusDays(1))) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Text(
            text = DateUtils.display(date),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showPicker = true }
        )
        if (streak > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "🔥 $streak 天",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        IconButton(onClick = { onChange(DateUtils.format(localDate.plusDays(1))) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

@Composable
private fun DayTypeToggle(isTraining: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            selected = isTraining,
            onClick = { onChange(true) },
            label = { Text(if (isTraining) "💪 训练日" else "训练日") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = !isTraining,
            onClick = { onChange(false) },
            label = { Text(if (isTraining) "休息日" else "😴 休息日") }
        )
    }
}

@Composable
private fun NutritionSummaryCard(entries: List<MealEntry>, targets: NutritionTargets) {
    val protein = entries.sumOf { it.protein }
    val carbs = entries.sumOf { it.carbs }
    val fat = entries.sumOf { it.fat }
    val calories = entries.sumOf { it.calories }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroRing("蛋白质", protein, targets.protein, ProteinColor)
                MacroRing("碳水", carbs, targets.carbs, CarbsColor)
                MacroRing("脂肪", fat, targets.fat, FatColor)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "热量",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "%.0f".format(calories),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (calories >= targets.calories) {
                        MaterialTheme.colorScheme.error
                    } else {
                        CarbsColor
                    }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "/ %.0f kcal".format(targets.calories),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun MacroRing(
    name: String,
    value: Double,
    target: Double,
    color: androidx.compose.ui.graphics.Color
) {
    val raw = if (target > 0) (value / target).toFloat() else 0f
    val progress by animateFloatAsState(targetValue = raw, animationSpec = tween(600), label = name)
    val over = raw > 1f
    val ringColor = if (over) MaterialTheme.colorScheme.error else color

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                color = ringColor,
                trackColor = color.copy(alpha = 0.15f),
                strokeWidth = 7.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "%.0f%%".format(raw * 100),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ringColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "%.0f / %.0f g".format(value, target),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MealSection(
    mealType: Int,
    entries: List<MealEntry>,
    photos: List<Photo>,
    onAdd: () -> Unit,
    onPhoto: () -> Unit,
    onViewPhoto: (Photo) -> Unit,
    photoFile: (Photo) -> File,
    onDelete: (MealEntry) -> Unit,
    onEdit: (MealEntry) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(mealEmoji(mealType), fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = MealEntry.mealName(mealType),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = "拍摄${MealEntry.mealName(mealType)}照片",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onPhoto() }
                        .padding(6.dp)
                        .size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加${MealEntry.mealName(mealType)}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumb(
                            file = photoFile(photo),
                            size = 56.dp,
                            onClick = { onViewPhoto(photo) }
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = "＋ 点击添加${MealEntry.mealName(mealType)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(top = 6.dp)
                )
            } else {
                entries.forEach { entry ->
                    MealEntryRow(entry = entry, onEdit = { onEdit(entry) }, onDelete = onDelete)
                }
            }
        }
    }
}

private fun mealEmoji(type: Int): String = when (type) {
    MealEntry.MEAL_BREAKFAST -> "🌅"
    MealEntry.MEAL_LUNCH -> "🍚"
    MealEntry.MEAL_DINNER -> "🌙"
    else -> "🥛"
}

@Composable
private fun MealEntryRow(entry: MealEntry, onEdit: () -> Unit, onDelete: (MealEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.foodName, fontWeight = FontWeight.Bold)
            Text(
                text = "%.0f %s · 蛋白 %.1f · 碳水 %.1f · 脂肪 %.1f · %.0f kcal"
                    .format(entry.amount, entry.amountLabel, entry.protein, entry.carbs, entry.fat, entry.calories),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onDelete(entry) }) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 点击记录行修改数量的对话框；营养按记录快照比例折算。 */
@Composable
private fun EditEntryDialog(entry: MealEntry, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amountText by remember(entry.id) { mutableStateOf(trimAmount(entry.amount)) }
    val amount = amountText.toDoubleOrNull()
    val canSave = amount != null && amount > 0
    val scale = if (canSave && entry.amount > 0) amount!! / entry.amount else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodName) },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(entry.amountHint) },
                    suffix = { Text(entry.amountLabel) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (entry.unit == Food.UNIT_PIECE) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Decimal
                        }
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                if (canSave) {
                    Text(
                        "本份营养（按 $amountText${entry.amountLabel}）",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "蛋白质 %.1f g".format(entry.protein * scale),
                        color = ProteinColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "碳水 %.1f g".format(entry.carbs * scale),
                        color = CarbsColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "脂肪 %.1f g".format(entry.fat * scale),
                        color = FatColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "热量 %.0f kcal".format(
                            (entry.protein + entry.carbs) * scale * 4 + entry.fat * scale * 9
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("请输入有效数量", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onSave) }, enabled = canSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun trimAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
