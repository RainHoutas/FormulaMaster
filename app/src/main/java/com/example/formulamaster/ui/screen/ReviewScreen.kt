package com.example.formulamaster.ui.screen

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.ui.component.HeatmapCalendar
import com.example.formulamaster.ui.component.ReviewCard
import com.example.formulamaster.ui.component.SprintSkipDialog
import com.example.formulamaster.ui.util.vibrateError
import com.example.formulamaster.ui.viewmodel.HeatmapState
import com.example.formulamaster.ui.viewmodel.ReviewViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val heatmap by viewModel.heatmap.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Task 5.4：冲刺期 Leech 再次遗忘时的"跳过本周"弹窗。
    // pending 锁定**那道题的** formulaId（submit 和翻页是异步的）
    var pendingLeechSkipId by remember { mutableStateOf<String?>(null) }

    // Task 5.5：热力图点击 → Snackbar 展示当天复习次数
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val onDayClicked: (LocalDate, Int) -> Unit = { date, count ->
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "${date.format(dateFormatter)} · 复习 $count 次"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    when {
        uiState.isLoading -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            )
        }

        uiState.queue.isEmpty() || uiState.isSessionComplete -> {
            EmptyReviewState(
                heatmap = heatmap,
                onDayClicked = onDayClicked,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        }

        else -> {
            val pagerState = rememberPagerState(pageCount = { uiState.queue.size })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                // ── 进度条 ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日复习",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${uiState.completedCount} / ${uiState.queue.size} 已完成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── 复习卡片 Pager ────────────────────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1
                ) { page ->
                    val item = uiState.queue[page]
                    ReviewCard(
                        item = item,
                        onReviewSubmitted = { rating, costTimeMs ->
                            // Task 5.4：Leech 再次遗忘 → 强化振动 + 冲刺期弹窗
                            if (rating == 1 && item.lapses >= 4) {
                                vibrateError(context, durationMs = 400L)
                                if (SprintModeManager.isActive()) {
                                    pendingLeechSkipId = item.formula.formulaId
                                }
                            }
                            // 先提交 DB（异步）
                            viewModel.submitReview(item, rating, costTimeMs)
                            // 再滚动到下一页（有动画）
                            scope.launch {
                                val next = page + 1
                                if (next < pagerState.pageCount) {
                                    pagerState.animateScrollToPage(next)
                                }
                                // 最后一页：completedCount 更新后 isSessionComplete=true
                                // → Composable 重组自动切换到 EmptyReviewState
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ── 页码指示点 ────────────────────────────────────────────────
                PagerDots(
                    count = uiState.queue.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }

        // Task 5.5：Snackbar 宿主叠在内容底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = 8.dp)
        )
    }

    // Task 5.4：冲刺期 Leech 跳过弹窗
    // AlertDialog 会独立成 Dialog window，不受上面 when 分支的当前内容影响
    pendingLeechSkipId?.let { id ->
        SprintSkipDialog(
            onSkip = {
                viewModel.postponeByWeek(id)
                pendingLeechSkipId = null
            },
            onContinue = { pendingLeechSkipId = null }
        )
    }
}

// ── 空状态 / 会话结束（含 Task 5.5 热力图） ──────────────────────────────────

@Composable
private fun EmptyReviewState(
    heatmap: HeatmapState,
    onDayClicked: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "今日复习已完成",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "保持节奏，明天继续",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Task 5.5：学习热力图（365 天） ───────────────────────────────────
        Text(
            text = "学习热力图",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            HeatmapCalendar(
                dayCounts = heatmap.dayCounts,
                today = heatmap.today,
                onDayClicked = onDayClicked
            )
        }
    }
}

// ── 页码指示点 ────────────────────────────────────────────────────────────────

@Composable
private fun PagerDots(
    count: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == currentPage
            Text(
                text = "●",
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 10.dp else 7.dp),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                fontSize = if (isSelected)
                    MaterialTheme.typography.labelMedium.fontSize
                else
                    MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}
