package com.example.formulamaster.ui.screen

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// ── 路由定义 ──────────────────────────────────────────────────────────────────

sealed class AppRoute(val route: String) {
    data object Memory : AppRoute("memory")
    data object Review : AppRoute("review")
    data object Test   : AppRoute("test")

    /** 公式详情：memory → formula_detail/{formulaId} */
    data object FormulaDetail : AppRoute("formula_detail/{formulaId}") {
        fun createRoute(formulaId: String) = "formula_detail/$formulaId"
    }
}

// ── Tab 元数据 ─────────────────────────────────────────────────────────────────

// Test 路由进入后需隐藏底部导航栏（营造考试氛围），故不列入此集合
private val topLevelRoutes = setOf(
    AppRoute.Memory.route,
    AppRoute.Review.route
)

private data class TopLevelTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    TopLevelTab(AppRoute.Memory, "记忆", Icons.Filled.Star,      Icons.Outlined.Star),
    TopLevelTab(AppRoute.Review, "复习", Icons.Filled.DateRange,  Icons.Outlined.DateRange),
    TopLevelTab(AppRoute.Test,   "测试", Icons.Filled.Edit,       Icons.Outlined.Edit)
)

// ── MainScreen ────────────────────────────────────────────────────────────────

/**
 * @param navTarget Task 6.1：通知点击后携带的导航目标（"review" 等），
 *   null 表示正常启动。LaunchedEffect 监听变化，App 在后台时 onNewIntent 更新
 *   MainActivity.navTarget → 此处 Compose State 变化 → 触发导航。
 */
@Composable
@Preview
fun MainScreen(navTarget: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Task 6.1：响应通知导航（冷启动 / 热启动均生效）
    LaunchedEffect(navTarget) {
        if (navTarget == "review") {
            navController.navigate(AppRoute.Review.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 详情页、Test 页等不显示底部导航栏
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == tab.route.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Memory.route,
            // Tab 切换无动画，子页面路由各自声明 slide
            enterTransition    = { EnterTransition.None },
            exitTransition     = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition  = { ExitTransition.None }
        ) {
            // ── Tab 主页面 ────────────────────────────────────────────────────
            composable(AppRoute.Memory.route) {
                MemoryScreen(
                    onFormulaClick = { formulaId ->
                        navController.navigate(AppRoute.FormulaDetail.createRoute(formulaId))
                    },
                    contentPadding = innerPadding
                )
            }
            composable(AppRoute.Review.route) {
                ReviewScreen(contentPadding = innerPadding)
            }
            composable(AppRoute.Test.route) {
                TestScreen(
                    onExit = {
                        // 退出测试 → 返回记忆 Tab（底部导航恢复）
                        navController.navigate(AppRoute.Memory.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // ── 公式详情（横向滑入）──────────────────────────────────────────
            composable(
                route = AppRoute.FormulaDetail.route,
                arguments = listOf(navArgument("formulaId") { type = NavType.StringType }),
                enterTransition    = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition     = { slideOutHorizontally(targetOffsetX = { it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition  = { slideOutHorizontally(targetOffsetX = { it }) }
            ) { backStackEntry ->
                val formulaId = backStackEntry.arguments?.getString("formulaId") ?: return@composable
                FormulaDetailScreen(
                    formulaId = formulaId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

