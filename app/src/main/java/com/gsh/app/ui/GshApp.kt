package com.gsh.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "今日", Icons.Outlined.Today),
    Tab("history", "历史", Icons.Outlined.History),
    Tab("foods", "食物库", Icons.Outlined.Restaurant),
    Tab("settings", "设置", Icons.Outlined.Settings)
)

@Composable
fun GshApp(vm: GshViewModel = viewModel(factory = GshViewModel.Factory)) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 20 }
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutVertically(tween(220)) { it / 20 }
            }
        ) {
            composable("today") {
                TodayScreen(
                    vm = vm,
                    onAddClick = { mealType ->
                        navController.navigate("add?mealType=$mealType")
                    },
                    onCopyClick = { navController.navigate("copy") }
                )
            }
            composable("copy") {
                CopyMealScreen(vm = vm, onDone = { navController.popBackStack() })
            }
            composable(
                route = "add?mealType={mealType}",
                arguments = listOf(navArgument("mealType") {
                    type = NavType.IntType
                    defaultValue = -1
                })
            ) { entry ->
                AddEntryScreen(
                    vm = vm,
                    initialMealType = entry.arguments?.getInt("mealType") ?: -1,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("history") {
                HistoryScreen(
                    vm = vm,
                    onDayClick = { date ->
                        vm.selectDate(date)
                        navigateToTab("today")
                    }
                )
            }
            composable("foods") {
                FoodLibraryScreen(vm = vm)
            }
            composable("settings") {
                SettingsScreen(vm = vm)
            }
        }
    }
}
