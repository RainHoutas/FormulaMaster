package com.example.formulamaster.ui.screen

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import android.util.Log
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
    data object Memory   : AppRoute("memory")
    data object Review   : AppRoute("review")
    data object Test     : AppRoute("test")
    data object Settings : AppRoute("settings")

    /** 公式详情：memory → formula_detail/{formulaId} */
    data object FormulaDetail : AppRoute("formula_detail/{formulaId}") {
        fun createRoute(formulaId: String) = "formula_detail/$formulaId"
    }

    /** Sprint 2 Task 2.5：七步学习仪式（formula_detail → 开始学习按钮进入） */
    data object FormulaLearnRitual : AppRoute("formula_learn_ritual/{formulaId}") {
        fun createRoute(formulaId: String) = "formula_learn_ritual/$formulaId"
    }
}

// ── Tab 元数据 ─────────────────────────────────────────────────────────────────

// Test 路由进入后需隐藏底部导航栏（营造考试氛围），故不列入此集合
private val topLevelRoutes = setOf(
    AppRoute.Memory.route,
    AppRoute.Review.route,
    AppRoute.Settings.route
)

private data class TopLevelTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    TopLevelTab(AppRoute.Memory,   "记忆", Icons.Filled.Star,      Icons.Outlined.Star),
    TopLevelTab(AppRoute.Review,   "复习", Icons.Filled.DateRange,  Icons.Outlined.DateRange),
    TopLevelTab(AppRoute.Test,     "测试", Icons.Filled.Edit,       Icons.Outlined.Edit),
    TopLevelTab(AppRoute.Settings, "设置", Icons.Filled.Settings,  Icons.Outlined.Settings)
)

// ── MainScreen ────────────────────────────────────────────────────────────────

/**
 * @param navTarget Task 6.1：通知点击后携带的导航目标（"review" 等），
 *   null 表示正常启动。LaunchedEffect 监听变化，App 在后台时 onNewIntent 更新
 *   MainActivity.navTarget → 此处 Compose State 变化 → 触发导航。
 */
// [PerfDiag] Sprint 2 Task 2.1 诊断（已收尾，2026-04-30）。开关复位 false，将来排性能问题改 true 即可。
private const val DIAG_ENABLED = false
private const val DIAG_TAG = "PerfDiag.Nav"

// Sprint 2 Task 2.1 修复 B：NavHost 默认淡入淡出时长（毫秒）
private const val NAV_FADE_MS = 120

@Composable
@Preview
fun MainScreen(navTarget: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // [PerfDiag] Sprint 2 Task 2.1：路由变化时序日志，用于诊断 Tab 切换卡顿/残留
    LaunchedEffect(currentRoute) {
        if (DIAG_ENABLED) {
            Log.d(DIAG_TAG, "route → $currentRoute @ ${System.currentTimeMillis()}")
        }
    }

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
            // Sprint 2 Task 2.1 修复 B：120ms 短淡入淡出。
            // 此前用 EnterTransition.None / ExitTransition.None 切换瞬间没有遮罩
            // → 旧页面 dispose 但屏幕缓冲未刷新 → 用户看到上一屏元素残留 + "闪一下"。
            // 120ms 是经验值：足够掩盖新页面 layout/measure/draw 的几帧延迟，
            // 又不会让用户感觉切换"拖沓"。详情页 composable 自带 slideInHorizontally，
            // 会覆盖 NavHost 默认，互不影响。
            enterTransition    = { fadeIn(tween(NAV_FADE_MS)) },
            exitTransition     = { fadeOut(tween(NAV_FADE_MS)) },
            popEnterTransition = { fadeIn(tween(NAV_FADE_MS)) },
            popExitTransition  = { fadeOut(tween(NAV_FADE_MS)) }
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
                        // Sprint 2 Task 2.1 修复 E：用 navigateUp 退到上一个 destination，
                        // 而不是写死跳 Memory。这样从详情页 / 其他 push 路径进 Test 时，
                        // 退出能回到用户实际的"来源页"。
                        // 注：从 NavBar 直接点 Test Tab 进入时，由于 NavBar 切换的
                        // popUpTo + saveState 行为，上一栈帧仍是 Memory（startDestination），
                        // 所以这种情况退出仍回 Memory（行为不变）。
                        // 完全消除该限制需把 Test 移出 NavBar，已加入改进点池。
                        if (!navController.navigateUp()) {
                            navController.navigate(AppRoute.Memory.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    // Sprint 1 Task 1.8：Snackbar「去设置」action 直跳设置 Tab
                    onNavigateToSettings = {
                        navController.navigate(AppRoute.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(AppRoute.Settings.route) {
                SettingsScreen(contentPadding = innerPadding)
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
                    onBack = { navController.popBackStack() },
                    onStartRitual = {
                        navController.navigate(AppRoute.FormulaLearnRitual.createRoute(formulaId))
                    }
                )
            }

            // ── Sprint 2 Task 2.5：七步学习仪式 ──────────────────────────────
            composable(
                route = AppRoute.FormulaLearnRitual.route,
                arguments = listOf(navArgument("formulaId") { type = NavType.StringType }),
                enterTransition    = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition     = { slideOutHorizontally(targetOffsetX = { it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition  = { slideOutHorizontally(targetOffsetX = { it }) }
            ) { backStackEntry ->
                val formulaId = backStackEntry.arguments?.getString("formulaId") ?: return@composable
                FormulaLearnRitualScreen(
                    formulaId = formulaId,
                    onBack = { navController.popBackStack() },
                    onFinishToMemory = {
                        // 仪式完成后回 Memory Tab（清除 FormulaDetail / Ritual 两层栈）
                        navController.popBackStack(AppRoute.Memory.route, inclusive = false)
                    }
                )
            }
        }
    }
}

