package com.gsh.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gsh.app.R
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "今日", Icons.Outlined.Today),
    Tab("history", "历史", Icons.Outlined.History),
    Tab("body", "变化", Icons.Outlined.AccessibilityNew),
    Tab("foods", "食物库", Icons.Outlined.Restaurant),
    Tab("me", "我", Icons.Outlined.Person)
)

/** 启动页奶油底色（与插画边缘一致，整页视觉无缝） */
private val SplashBg = Color(0xFFFEF4E7)

@Composable
fun GshApp(vm: GshViewModel = viewModel(factory = GshViewModel.Factory)) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    // 启动插画：冷启动展示约一秒后淡出，点击可跳过；回前台/切页面不会重播
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900)
        showSplash = false
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                composable("body") {
                    BodyScreen(vm = vm)
                }
                composable("foods") {
                    FoodLibraryScreen(vm = vm)
                }
                composable("me") {
                    MeScreen(
                        vm = vm,
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenWorkout = { navController.navigate("workout") }
                    )
                }
                // 训练记录页（逐条添加模式，从「我」页/今日页进入）
                composable("training") {
                    TrainingScreen(vm = vm, onDone = { navController.popBackStack() })
                }
                // 设置改为「我」页进入的二级页（不显示底部导航）
                composable("settings") {
                    SettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
        }
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(tween(300))
        ) {
            SplashOverlay(onSkip = { showSplash = false })
        }
    }
}

/** 启动插画覆盖层：Q 版角色 + GSH 字样，点击跳过。 */
@Composable
private fun SplashOverlay(onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg)
            .clickable { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_illustration),
                contentDescription = "GSH 启动插画",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "GSH · 增肌饮食记录",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF3E3A33)
            )
        }
    }
}
