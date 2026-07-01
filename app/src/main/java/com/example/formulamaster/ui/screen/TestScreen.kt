package com.example.formulamaster.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.runtime.DisposableEffect
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.InputMode
import com.example.formulamaster.domain.MathOcrRecognizer
import com.example.formulamaster.domain.RecognitionMode
import com.example.formulamaster.domain.RecognizerErrorClassifier
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.RecognizerType
import com.example.formulamaster.domain.model.FormulaWithState
import com.example.formulamaster.ui.component.FeedbackDialog
import com.example.formulamaster.ui.component.FeedbackPayload
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.component.PaperPenInputArea
import com.example.formulamaster.ui.component.SprintSkipDialog
import com.example.formulamaster.ui.component.TestCanvas
import com.example.formulamaster.ui.util.vibrateError
import com.example.formulamaster.ui.viewmodel.TestViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Task 5.1 / 5.2 / 5.3：严测模块
 *
 * - 5.1 基础环境：隐藏底部导航 + Close 退出 + Mastered 队列
 * - 5.2 手写输入：TestCanvas + 1.5s OCR + 答题区拼接
 * - 5.3 严厉裁决：[提交核对] AlertDialog + [完全正确]/[出现错误] + 惩罚机制
 *
 * ## Sprint 1 Task 1.8 升级（友好降级 Snackbar）
 * - 接 SnackbarHost，把 TestCanvas 的两类失败信号转为分类提示：
 *   - 两档都未绑定 / 用户写第一笔 → "尚未绑定识别器" + 「去设置」action
 *   - Deep 识别失败 → 用 [RecognizerErrorClassifier] 分类的简短文案
 * - 「去设置」通过 [onNavigateToSettings] 回调上抛到 MainScreen，由 NavController 切换 Tab
 *
 * ## Sprint 1 Task 1.9 升级（识别失败反馈）
 * - TestCanvas 「都不对」按钮 → [FeedbackDialog] → [TestViewModel.submitOcrFeedback] 入库
 * - 入库后 Snackbar 提示"已记录，可在设置页导出"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    onExit: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TestViewModel = viewModel(factory = TestViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSprintActive by viewModel.isSprintActive.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Sprint 1 Task 1.7：从用户偏好动态解析 Light/Deep 识别器
    // Sprint 2 Task 2.1 修复 D + H：
    // - D: AppContainer 单例 → Tink AEAD 一次性初始化
    // - H: preference.settings 已是 process 级 hot StateFlow → collectAsState 立即拿到当前值，
    //   不再为每个 ViewModel 重新订阅 cold flow（消除"进 Test 不跟手"感）
    val pref = remember { AppContainer.recognizerPreference(context) }
    val settings by pref.settings.collectAsState()
    val lightRecognizer: MathOcrRecognizer? =
        remember(settings) { RecognizerRegistry.resolveLight(settings) }
    val deepRecognizer: MathOcrRecognizer? =
        remember(settings) { RecognizerRegistry.resolveDeep(settings) }

    // Sprint 3 Task 3.2：读取输入方式偏好，决定手写路径还是纸笔路径
    val appPref = remember { AppContainer.appPreference(context) }
    val appSettings by appPref.settings.collectAsState()

    // Sprint 3 Task 3.2：纸笔模式强制横屏
    // - PaperPen → SCREEN_ORIENTATION_SENSOR_LANDSCAPE（进入即锁横屏）
    // - 切回 Handwriting 或离开 TestScreen → onDispose 恢复 UNSPECIFIED
    // DisposableEffect key = inputMode：模式切换时 effect 重跑，onDispose 先复原
    val activity = context as? Activity
    DisposableEffect(appSettings.inputMode) {
        if (appSettings.inputMode == InputMode.PaperPen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 错误惩罚：屏幕边缘红光闪烁
    var flashError by remember { mutableStateOf(false) }
    LaunchedEffect(flashError) {
        if (flashError) {
            delay(500L)
            flashError = false
        }
    }

    // Task 5.4：冲刺期 Leech 再次失败时的跳过弹窗
    var pendingLeechSkipId by remember { mutableStateOf<String?>(null) }

    // Sprint 1 Task 1.9：反馈对话框待显示载荷（null = 关闭）
    var pendingFeedback by remember { mutableStateOf<FeedbackPayload?>(null) }

    // ── Snackbar 触发函数（提取出来便于复用） ─────────────────────────────────
    fun showUnboundSnackbar() {
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "尚未绑定识别器，写下来也不会识别",
                actionLabel = "去设置",
                duration = SnackbarDuration.Long,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToSettings()
            }
        }
    }

    fun showDeepFailureSnackbar(throwable: Throwable) {
        val msg = RecognizerErrorClassifier.classify(throwable)
        coroutineScope.launch {
            // Key 失效类错误带"去设置"action；其他网络错误只显示文案
            val needsSettings = msg.contains("Key")
            val result = snackbarHostState.showSnackbar(
                message = "强识别失败：$msg",
                actionLabel = if (needsSettings) "去设置" else null,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            if (needsSettings && result == SnackbarResult.ActionPerformed) {
                onNavigateToSettings()
            }
        }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    key(item.formula.formulaId) {
                        // Sprint 3 Task 3.2：按 inputMode 路由
                        // - PaperPen  → 纸笔自评（不调识别器，不消耗额度，强制横屏由外层 DisposableEffect 管理）
                        // - Handwriting → 现有手写识别路径
                        when (appSettings.inputMode) {
                            InputMode.PaperPen -> PaperPenInputArea(
                                item         = item,
                                progressText = "${uiState.currentIndex + 1} / ${uiState.queue.size}",
                                onSubmitCorrect = { costMs ->
                                    viewModel.submitJudgment(item, isCorrect = true, costTimeMs = costMs)
                                },
                                onSubmitError = { costMs ->
                                    val isLeech = item.isLeech
                                    vibrateError(context, durationMs = if (isLeech) 400L else 200L)
                                    flashError = true
                                    viewModel.submitJudgment(item, isCorrect = false, costTimeMs = costMs)
                                    if (isLeech && isSprintActive) {
                                        pendingLeechSkipId = item.formula.formulaId
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            InputMode.Handwriting -> TestContent(
                                item             = item,
                                progressText     = "${uiState.currentIndex + 1} / ${uiState.queue.size}",
                                answerPieces     = uiState.answerPieces,
                                canSubmit        = uiState.canSubmit,
                                lightRecognizer  = lightRecognizer,
                                deepRecognizer   = deepRecognizer,
                                onAppendPiece    = viewModel::appendPiece,
                                onPopPiece       = viewModel::popLastPiece,
                                onClearAnswer    = viewModel::clearAnswer,
                                onWritingButNoRecognizer = ::showUnboundSnackbar,
                                onDeepFailure    = ::showDeepFailureSnackbar,
                                onReportFeedback = { payload -> pendingFeedback = payload },
                                onSubmitCorrect  = { costMs ->
                                    viewModel.submitJudgment(item, isCorrect = true, costTimeMs = costMs)
                                },
                                onSubmitError    = { costMs ->
                                    val isLeech = item.isLeech
                                    vibrateError(context, durationMs = if (isLeech) 400L else 200L)
                                    flashError = true
                                    viewModel.submitJudgment(item, isCorrect = false, costTimeMs = costMs)
                                    if (isLeech && isSprintActive) {
                                        pendingLeechSkipId = item.formula.formulaId
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 错误闪烁红框
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

            // Sprint 1 Task 1.9 / Sprint 3 Task 3.4：反馈对话框
            // Sprint 3 Task 3.4：复用 ClozeParser 解析 clozeData → placeholder 列表，
            // 替代手输 LaTeX；clozeData 空数组场景下 placeholders 为空，FeedbackDialog 内部退化处理
            pendingFeedback?.let { payload ->
                val currentItem = uiState.currentItem
                val placeholders = remember(currentItem?.formula?.formulaId) {
                    currentItem?.formula?.clozeData
                        ?.let { ClozeParser.parse(it).map { ci -> ci.placeholder } }
                        ?: emptyList()
                }
                FeedbackDialog(
                    strokes = payload.strokes,
                    candidates = payload.candidates,
                    formulaLatex = currentItem?.formula?.latexCode.orEmpty(),
                    placeholders = placeholders,
                    onDismiss = { pendingFeedback = null },
                    onSubmit = { wrongPlaceholders ->
                        // 解析当时使用的识别器类型（按 mode 取 Light 或 Deep 绑定）
                        val recType: RecognizerType? = when (payload.mode) {
                            RecognitionMode.Light -> settings.lightRecognizerId
                            RecognitionMode.Deep  -> settings.deepRecognizerId
                        }
                        viewModel.submitOcrFeedback(
                            formulaId         = currentItem?.formula?.formulaId,
                            recognizerType    = recType?.name ?: "none",
                            mode              = payload.mode,
                            strokes           = payload.strokes,
                            candidates        = payload.candidates,
                            wrongPlaceholders = wrongPlaceholders
                        )
                        pendingFeedback = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "已记录反馈，可在设置页导出 JSON",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
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
    onWritingButNoRecognizer: () -> Unit,
    onDeepFailure: (Throwable) -> Unit,
    onReportFeedback: (FeedbackPayload) -> Unit,
    onSubmitCorrect: (costMs: Long) -> Unit,
    onSubmitError: (costMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
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
                onWritingButNoRecognizer = onWritingButNoRecognizer,
                onDeepFailure = onDeepFailure,
                onReportFeedback = onReportFeedback,
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
