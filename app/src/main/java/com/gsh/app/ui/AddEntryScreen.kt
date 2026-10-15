package com.gsh.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsh.app.data.Food
import com.gsh.app.data.MealEntry
import com.gsh.app.data.NutritionSnapshot
import java.util.Calendar

/** 按当前时间给出默认餐别。 */
private fun defaultMealType(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..9 -> MealEntry.MEAL_BREAKFAST
    in 10..14 -> MealEntry.MEAL_LUNCH
    in 15..19 -> MealEntry.MEAL_DINNER
    else -> MealEntry.MEAL_SNACK
}

/** initialMealType 为 -1 时按当前时间自动选择。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(vm: GshViewModel, initialMealType: Int = -1, onDone: () -> Unit) {
    val foods by vm.foods.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val searchHistory by vm.searchHistory.collectAsState()

    // 进入本页时清掉上一次的搜索词
    LaunchedEffect(Unit) { vm.setSearchQuery("") }

    var selectedMeal by remember {
        mutableIntStateOf(
            if (initialMealType in 0 until MealEntry.MEAL_COUNT) initialMealType else defaultMealType()
        )
    }
    var selectedFood by remember { mutableStateOf<Food?>(null) }
    var amountText by remember { mutableStateOf("100") }

    val amount = amountText.toDoubleOrNull()
    val preview = selectedFood?.nutrition(amount ?: 0.0)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("添加记录") },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 餐别选择
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until MealEntry.MEAL_COUNT).forEach { type ->
                    FilterChip(
                        selected = selectedMeal == type,
                        onClick = { selectedMeal = type },
                        label = { Text(MealEntry.mealName(type)) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索食物") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.recordSearch(query) })
            )

            if (query.isBlank() && selectedFood == null && searchHistory.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SearchHistoryBar(
                    history = searchHistory,
                    onTermClick = { vm.setSearchQuery(it) },
                    onClear = vm::clearSearchHistory
                )
            }
        }

        val food = selectedFood
        if (food == null) {
            FoodList(
                foods = foods,
                modifier = Modifier.weight(1f),
                onPick = {
                    // 选中结果前，把当前有效的搜索词记入历史
                    if (query.isNotBlank()) vm.recordSearch(query)
                    selectedFood = it
                    amountText = if (it.unit == Food.UNIT_PIECE) "1" else "100"
                }
            )
        } else {
            SelectedFoodCard(
                food = food,
                amountText = amountText,
                preview = preview,
                onAmountChange = { amountText = it },
                onClear = { selectedFood = null },
                onSave = {
                    val a = amountText.toDoubleOrNull()
                    if (a != null && a > 0) {
                        vm.addEntry(food, a, selectedMeal)
                        onDone()
                    }
                },
                canSave = amount != null && amount > 0
            )
        }
    }
}

@Composable
private fun FoodList(foods: List<Food>, modifier: Modifier = Modifier, onPick: (Food) -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(foods, key = { it.id }) { food ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(food) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = food.name + if (food.isCustom) "（自定义）" else "",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${food.basisLabel}：蛋白 %.1f · 碳水 %.1f · 脂肪 %.1f · %.0f kcal".format(
                            food.proteinPerUnit, food.carbsPerUnit, food.fatPerUnit, food.caloriesPerUnit
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedFoodCard(
    food: Food,
    amountText: String,
    preview: NutritionSnapshot?,
    onAmountChange: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClear) { Text("换一个") }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(food.amountHint) },
            suffix = { Text(food.unitLabel) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (food.unit == Food.UNIT_PIECE) {
                    KeyboardType.Number
                } else {
                    KeyboardType.Decimal
                }
            ),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (preview != null) {
                    Text(
                        "本份营养（按 $amountText${food.unitLabel}）",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("蛋白质 %.1f g".format(preview.protein), color = ProteinColor, fontWeight = FontWeight.Bold)
                    Text("碳水 %.1f g".format(preview.carbs), color = CarbsColor, fontWeight = FontWeight.Bold)
                    Text("脂肪 %.1f g".format(preview.fat), color = FatColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "热量 %.0f kcal".format(preview.calories),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("请输入有效数量", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
    }
}
