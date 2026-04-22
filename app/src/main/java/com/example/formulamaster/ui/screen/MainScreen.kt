package com.example.formulamaster.ui.screen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// ── 路由定义 ──────────────────────────────────────────────────────────────────

sealed class AppRoute(val route: String) {
    data object Memory : AppRoute("memory")
    data object Review : AppRoute("review")
    data object Test   : AppRoute("test")
}

// ── Tab 元数据 ─────────────────────────────────────────────────────────────────

private data class TopLevelTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    TopLevelTab(AppRoute.Memory, "记忆", Icons.Filled.Star,     Icons.Outlined.Star),
    TopLevelTab(AppRoute.Review, "复习", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    TopLevelTab(AppRoute.Test,   "测试", Icons.Filled.Edit,      Icons.Outlined.Edit)
)

// ── 动画常量 ──────────────────────────────────────────────────────────────────

private const val NAV_ANIM_DURATION = 300

// ── MainScreen ────────────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route.route) {
                                // 弹回起始目标，避免堆栈积压
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Memory.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(NAV_ANIM_DURATION)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(NAV_ANIM_DURATION)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(NAV_ANIM_DURATION)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(NAV_ANIM_DURATION)
                )
            }
        ) {
            composable(AppRoute.Memory.route) { MemoryPlaceholder() }
            composable(AppRoute.Review.route) { ReviewPlaceholder() }
            composable(AppRoute.Test.route)   { TestPlaceholder() }
        }
    }
}

// ── 占位页（Sprint 3~5 替换为真实实现） ──────────────────────────────────────

@Composable
private fun MemoryPlaceholder() {
    PlaceholderScreen(label = "TODO: Memory（记忆模块）")
}

@Composable
private fun ReviewPlaceholder() {
    PlaceholderScreen(label = "TODO: Review（复习模块）")
}

@Composable
private fun TestPlaceholder() {
    PlaceholderScreen(label = "TODO: Test（测试模块）")
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label)
    }
}
