package com.gsh.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryScreen(vm: GshViewModel) {
    val foods by vm.foods.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val searchHistory by vm.searchHistory.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // 进入本页时清掉上一次的搜索词
    LaunchedEffect(Unit) { vm.setSearchQuery("") }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加食物") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(title = { Text("食物库") })
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索食物") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.recordSearch(query) })
            )
            if (query.isBlank() && searchHistory.isNotEmpty()) {
                SearchHistoryBar(
                    history = searchHistory,
                    onTermClick = { vm.setSearchQuery(it) },
                    onClear = vm::clearSearchHistory,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(foods, key = { it.id }) { food ->
                    FoodRow(food = food, onDelete = { vm.deleteCustomFood(food) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddFoodDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, p, c, f, unit ->
                vm.addCustomFood(name, p, c, f, unit)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FoodRow(food: Food, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            if (food.isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, protein: Double, carbs: Double, fat: Double, unit: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var unit by remember { mutableIntStateOf(Food.UNIT_GRAM) }

    val basisText = when (unit) {
        Food.UNIT_ML -> "营养含量按每 100ml 填写（克）"
        Food.UNIT_PIECE -> "营养含量按每个填写（克）"
        else -> "营养含量按每 100g 填写（克）"
    }

    val p = protein.toDoubleOrNull()
    val c = carbs.toDoubleOrNull()
    val f = fat.toDoubleOrNull()
    val valid = name.isNotBlank() && p != null && c != null && f != null &&
        p >= 0 && c >= 0 && f >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义食物") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 单位选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Food.UNIT_GRAM to "克（g）",
                        Food.UNIT_ML to "毫升（ml）",
                        Food.UNIT_PIECE to "个"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = unit == value,
                            onClick = { unit = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(basisText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("食物名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = protein, onValueChange = { protein = it }, label = { Text("蛋白质") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = carbs, onValueChange = { carbs = it }, label = { Text("碳水") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fat, onValueChange = { fat = it }, label = { Text("脂肪") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(name, p!!, c!!, f!!, unit) },
                enabled = valid
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
