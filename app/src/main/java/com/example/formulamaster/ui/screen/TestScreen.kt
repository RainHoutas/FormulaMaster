package com.example.formulamaster.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import com.example.formulamaster.data.RecognizerPreference
import com.example.formulamaster.domain.MathOcrRecognizer
import com.example.formulamaster.domain.RecognizerRegistry
import com.example.formulamaster.domain.RecognizerSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.domain.model.FormulaWithState
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.component.SprintSkipDialog
import com.example.formulamaster.ui.component.TestCanvas
import com.example.formulamaster.ui.util.vibrateError
import com.example.formulamaster.ui.viewmodel.TestViewModel
import kotlinx.coroutines.delay

/**
 * Task 5.1 / 5.2 / 5.3：严测模块
 *
 * - 5.1 基础环境：隐藏底部导航 + Close 退出 + Mastered 队列
 * - 5.2 手写输入：TestCanvas + 1.5s OCR + 答题区拼接
 * - 5.3 严厉裁决：[提交核对] AlertDialog + [完全正确]/[出现错误] + 惩罚机制
 *   （错误 → 200ms 强振动 + 屏幕边缘红光闪烁 + FSRS rating=1 + isTestMode=true）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    onExit: () -> Unit,
    viewModel: TestViewModel = viewModel(factory = TestViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Sprint 1 Task 1.7：从用户偏好动态解析 Light/Deep 识别器
    // DataStore Flow → settings 变化 → recognizer 立即重组（无需重启）
    val pref = remember { RecognizerPreference(context.applicationContext) }
    val settings by pref.settings.collectAsState(initial = RecognizerSettings())
    val lightRecognizer: MathOcrRecognizer? =
        remember(settings) { RecognizerRegistry.resolveLight(settings) }
    val deepRecognizer: MathOcrRecognizer? =
        remember(settings) { RecognizerRegistry.resolveDeep(settings) }

    // 错误惩罚：屏幕边缘红光闪烁（外置于 key() 保证切题后仍能完成动画）
    var flashError by remember { mutableStateOf(false) }
    LaunchedEffect(flashError) {
        if (flashError) {
            delay(500L)
            flashError = false
        }
    }

    // Task 5.4：冲刺期 Leech 再次失败时的跳过弹窗。
    // 冻结刚才那道题的 formulaId（submit 已推进到下一题，这里作用于"上一题"）
    var pendingLeechSkipId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阶段严测") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出测试"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    )
                }

                uiState.queue.isEmpty() -> {
                    EmptyMasteredState(modifier = Modifier.fillMaxSize())
                }

                uiState.isSessionComplete -> {
                    SessionCompleteState(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    val item = uiState.currentItem ?: return@Box
                    // 切题时刷新 Canvas 内部笔画 / OCR 候选的本地状态
                    key(item.formula.formulaId) {
                        TestContent(
                            item             = item,
                            progressText     = "${uiState.currentIndex + 1} / ${uiState.queue.size}",
                            answerPieces     = uiState.answerPieces,
                            canSubmit        = uiState.canSubmit,
                            lightRecognizer  = lightRecognizer,
                            deepRecognizer   = deepRecognizer,
                            onAppendPiece    = viewModel::appendPiece,
                            onPopPiece       = viewModel::popLastPiece,
                            onClearAnswer    = viewModel::clearAnswer,
                            onSubmitCorrect  = { costMs ->
                                viewModel.submitJudgment(item, isCorrect = true, costTimeMs = costMs)
                            },
                            onSubmitError    = { costMs ->
                                // Task 5.4：Leech（lapses≥4）惩罚强化——振动时长双倍 400ms
                                val isLeech = item.lapses >= 4
                                vibrateError(context, durationMs = if (isLeech) 400L else 200L)
                                flashError = true
                                viewModel.submitJudgment(item, isCorrect = false, costTimeMs = costMs)
                                // 冲刺期 + Leech：弹窗询问"跳过本周"
                                if (isLeech && SprintModeManager.isActive()) {
                                    pendingLeechSkipId = item.formula.formulaId
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 错误闪烁红框（外层 overlay，不受 key() 重组影响）
            AnimatedVisibility(
                visible = flashError,
                enter = fadeIn(tween(100)),
                exit  = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 8.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                )
            }

            // Task 5.4：冲刺期 Leech 跳过弹窗
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
    }
}

// ── 单题布局 ──────────────────────────────────────────────────────────────────

@Composable
private fun TestContent(
    item: FormulaWithState,
    progressText: String,
    answerPieces: List<String>,
    canSubmit: Boolean,
    lightRecognizer: MathOcrRecognizer?,
    deepRecognizer: MathOcrRecognizer?,
    onAppendPiece: (String) -> Unit,
    onPopPiece: () -> Unit,
    onClearAnswer: () -> Unit,
    onSubmitCorrect: (costMs: Long) -> Unit,
    onSubmitError: (costMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 本题开始时刻，用于统计 costTimeMs
    val questionStartMs = remember { System.currentTimeMillis() }
    var showJudgmentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = item.formula.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请在下方默写该公式",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnswerBar(
            pieces = answerPieces,
            onUndo = onPopPiece,
            onClear = onClearAnswer
        )

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            TestCanvas(
                onCandidateSelected = onAppendPiece,
                lightRecognizer = lightRecognizer,
                deepRecognizer = deepRecognizer,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        FilledTonalButton(
            onClick = { showJudgmentDialog = true },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("提交核对")
        }
    }

    if (showJudgmentDialog) {
        StrictJudgmentDialog(
            standardLatex = item.formula.latexCode,
            onCorrect = {
                showJudgmentDialog = false
                onSubmitCorrect(System.currentTimeMillis() - questionStartMs)
            },
            onError = {
                showJudgmentDialog = false
                onSubmitError(System.currentTimeMillis() - questionStartMs)
            },
            onDismiss = { showJudgmentDialog = false }
        )
    }
}

// ── 严厉裁决对话框 ────────────────────────────────────────────────────────────

@Composable
private fun StrictJudgmentDialog(
    standardLatex: String,
    onCorrect: () -> Unit,
    onError: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标准答案") },
        text = {
            Column {
                MathFormulaView(
                    latex = standardLatex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "请诚实核对：与标准答案相比，你的默写是否完全正确？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCorrect) {
                Text("完全正确")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onError,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("出现错误")
            }
        }
    )
}

// ── 答题区 ────────────────────────────────────────────────────────────────────

@Composable
private fun AnswerBar(
    pieces: List<String>,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            ) {
                if (pieces.isEmpty()) {
                    Text(
                        text = "答题区（采纳预览或选择候选拼接公式）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                } else {
                    // 用 KaTeX 渲染，不再用 Text 显示 raw 源码。
                    // pieces 间用空格连接 —— LaTeX 中空格作为 token 分隔符是安全的。
                    MathFormulaView(
                        latex = pieces.joinToString(" "),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            IconButton(onClick = onUndo, enabled = pieces.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "回退一块"
                )
            }
            IconButton(onClick = onClear, enabled = pieces.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "清空答题区"
                )
            }
        }
    }
}

// ── 空状态 / 会话完成 ────────────────────────────────────────────────────────

@Composable
private fun EmptyMasteredState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "📝",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无可测试的公式",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "只有达到「已掌握」状态的公式才会进入严测流程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SessionCompleteState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
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
                text = "本轮测试完成",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

