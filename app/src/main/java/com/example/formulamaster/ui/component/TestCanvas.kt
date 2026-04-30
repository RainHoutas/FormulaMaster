package com.example.formulamaster.ui.component

import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.example.formulamaster.domain.LatexNormalizer
import com.example.formulamaster.domain.MathOcrRecognizer
import com.example.formulamaster.domain.MockMathRecognizer
import com.example.formulamaster.domain.OcrInput
import com.example.formulamaster.domain.RecognitionMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

/**
 * [MotionEvent.TOOL_TYPE_PALM] 于 API 33 引入，值为 5。
 * 即便 compileSdk >= 33 亦可能被 Kotlin stub 解析失败，
 * 硬编码为字面常量最稳妥，且 Android 对 public constant 的值承诺不变。
 */
private const val TOOL_TYPE_PALM_VALUE = 5

/**
 * 严测默写画布
 *
 * ## Sprint 1 Task 1.1 升级
 * - **Palm Rejection**：`pointerInteropFilter` 过滤 [MotionEvent.TOOL_TYPE_PALM]
 * - **双档 debounce**：Light（停笔 [lightDebounceMs]）/ Deep（手动「强识别」按钮触发）
 * - **顶部 80dp 预览条**：KaTeX 实时渲染 + "✓采纳" + 「强识别」+ 「👎都不对」按钮
 *
 * ## Sprint 1 Task 1.7 升级
 * - Light/Deep 两档分别接收独立识别器（来自 `RecognizerPreference` + `RecognizerRegistry.resolveLight/Deep`）
 *
 * ## Sprint 1 Task 1.8 升级（友好降级）
 * - 移除组件内的"红字提示"层（曾用于 Deep 失败），统一通过 [onDeepFailure] 回调上抛给宿主页面
 *   显示分类 Snackbar；本组件不再持有错误显示状态，符合 UDF
 * - Light 档失败遵守"完全静默"原则（Q3 决策）：只 Logcat，不冒泡，不打扰用户
 * - 两档都未绑定时用户写第一笔 → 触发 [onWritingButNoRecognizer]，宿主页面应弹引导 Snackbar
 *
 * ## Sprint 1 Task 1.9 升级（识别失败反馈）
 * - PreviewBar 加 👎 按钮，点击通过 [onReportFeedback] 回调把 (strokes, candidates, mode) 上抛，
 *   宿主页面负责弹反馈 Dialog 并入库
 *
 * ## 架构说明
 * 本组件依然只依赖 [MathOcrRecognizer] 接口，UI 层零感知具体实现。
 * 错误展示状态（Snackbar）抬升到宿主 [com.example.formulamaster.ui.screen.TestScreen]，
 * 本组件保持纯输入设备职责。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TestCanvas(
    onCandidateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    lightRecognizer: MathOcrRecognizer? = remember { MockMathRecognizer() },
    deepRecognizer: MathOcrRecognizer? = remember { MockMathRecognizer() },
    onDeepFailure: (Throwable) -> Unit = {},
    onWritingButNoRecognizer: () -> Unit = {},
    onReportFeedback: (FeedbackPayload) -> Unit = {},
    lightDebounceMs: Long = 300L,
    @Suppress("UNUSED_PARAMETER")  // 历史接口字段，Deep 改为手动触发后未使用，保留兼容外部调用方
    deepDebounceMs: Long = 1_500L
) {
    // 已完成的笔画：每笔一组有序 Offset
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    // 当前正在绘制的笔画
    var currentStroke by remember { mutableStateOf(emptyList<Offset>()) }
    // 当前预览的 LaTeX（Light 自动写入；强识别成功后覆盖；采纳后清空）
    var previewLatex by remember { mutableStateOf("") }
    // Light 请求是否在飞行中
    var lightLoading by remember { mutableStateOf(false) }
    // Deep 手动触发计数器：++ 一次触发一次 Deep 识别
    var deepTriggerNonce by remember { mutableIntStateOf(0) }
    // Deep 请求是否在飞行中
    var deepLoading by remember { mutableStateOf(false) }
    // 最近一次识别返回的完整候选列表（用于反馈样本携带）
    val lastCandidates = remember { mutableStateListOf<String>() }
    // 最近一次识别使用的模式（用于反馈样本携带）
    var lastMode by remember { mutableStateOf(RecognitionMode.Light) }
    // 是否已对"两档都没绑定"提示过一次（避免每笔重复 Snackbar）
    var unboundNotifiedThisSession by remember { mutableStateOf(false) }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 两档都未绑定时，用户写完第一笔触发一次提示
    LaunchedEffect(completedStrokes.size, lightRecognizer, deepRecognizer) {
        if (completedStrokes.isNotEmpty() &&
            lightRecognizer == null &&
            deepRecognizer == null &&
            !unboundNotifiedThisSession
        ) {
            unboundNotifiedThisSession = true
            onWritingButNoRecognizer()
        }
    }

    // ── Light：自动防抖（cheap，turbo 端点 2000/天烧得起） ───────────────
    LaunchedEffect(completedStrokes.size, lightRecognizer) {
        if (completedStrokes.isEmpty() || lightRecognizer == null) {
            previewLatex = ""
            lightLoading = false
            return@LaunchedEffect
        }
        delay(lightDebounceMs)
        val snapshot = completedStrokes.map { stroke -> stroke.map { it.x to it.y } }
        lightLoading = true
        // Light 失败完全静默（Sprint 1 Task 1.8 Q3 决策）：自动防抖每次失败 Snackbar 太烦扰
        try {
            val raw = lightRecognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Light)
            val cleaned = LatexNormalizer.normalizeAndFilter(raw)
            lastCandidates.clear()
            lastCandidates.addAll(cleaned)
            lastMode = RecognitionMode.Light
            previewLatex = cleaned.firstOrNull().orEmpty()
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Light recognize failed", e)
            previewLatex = ""
        } finally {
            lightLoading = false
        }
    }

    // ── Deep：手动触发（避免浪费 standard 端点 500/天的额度） ────────────
    // 设计：单结果直接覆盖 previewLatex（去除弹窗确认环节），失败抛给宿主显示 Snackbar
    LaunchedEffect(deepTriggerNonce) {
        if (deepTriggerNonce == 0) return@LaunchedEffect  // 初始值，不触发
        if (completedStrokes.isEmpty() || deepRecognizer == null) return@LaunchedEffect

        val snapshot = completedStrokes.map { stroke -> stroke.map { it.x to it.y } }
        deepLoading = true
        try {
            val raw = deepRecognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Deep)
            val cleaned = LatexNormalizer.normalizeAndFilter(raw)
            lastCandidates.clear()
            lastCandidates.addAll(cleaned)
            lastMode = RecognitionMode.Deep
            val first = cleaned.firstOrNull()
            if (first != null) {
                previewLatex = first
            } else {
                // 服务端 200 但无候选（如 Mathpix 顶层 error 或 SimpleTex status=false），
                // 等价于"识别失败"语义，统一上抛让宿主决定 UI 反馈
                onDeepFailure(IllegalStateException("识别未返回结果"))
            }
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Deep recognize failed", e)
            onDeepFailure(e)
        } finally {
            deepLoading = false
        }
    }

    fun clearAll() {
        completedStrokes.clear()
        currentStroke = emptyList()
        previewLatex = ""
        lastCandidates.clear()
        unboundNotifiedThisSession = false
    }

    fun selectCandidate(latex: String) {
        if (latex.isBlank()) return
        onCandidateSelected(latex)
        clearAll()
    }

    val canReportFeedback by remember {
        derivedStateOf { completedStrokes.isNotEmpty() }
    }

    Column(modifier = modifier) {
        // ── 顶部 80dp 实时预览条 ───────────────────────────────────────────
        PreviewBar(
            latex = previewLatex,
            isLoading = lightLoading,
            hasStrokes = completedStrokes.isNotEmpty(),
            onAdopt = { selectCandidate(previewLatex) },
            onRecognizeDeep = { deepTriggerNonce++ },
            canRecognizeDeep = completedStrokes.isNotEmpty() && deepRecognizer != null && !deepLoading,
            isDeepLoading = deepLoading,
            canReportFeedback = canReportFeedback,
            onReportFeedback = {
                val strokeSnapshot = completedStrokes.map { s -> s.map { it.x to it.y } }
                onReportFeedback(
                    FeedbackPayload(
                        strokes = strokeSnapshot,
                        candidates = lastCandidates.toList(),
                        mode = lastMode
                    )
                )
            }
        )

        // ── 主画布区（剩余空间） ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { motionEvent ->
                        // Palm Rejection：活动指针为 TOOL_TYPE_PALM 时丢弃
                        val actionIndex = motionEvent.actionIndex
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            motionEvent.getToolType(actionIndex) == TOOL_TYPE_PALM_VALUE
                        ) {
                            return@pointerInteropFilter true
                        }
                        val x = motionEvent.x
                        val y = motionEvent.y
                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                currentStroke = listOf(Offset(x, y))
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (currentStroke.isNotEmpty()) {
                                    currentStroke = currentStroke + Offset(x, y)
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                if (currentStroke.isNotEmpty()) {
                                    completedStrokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                currentStroke = emptyList()
                            }
                        }
                        true
                    }
            ) {
                val style = Stroke(
                    width = 8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )

                fun List<Offset>.toPath(): Path {
                    val path = Path()
                    if (isEmpty()) return path
                    path.moveTo(first().x, first().y)
                    drop(1).forEach { path.lineTo(it.x, it.y) }
                    return path
                }

                completedStrokes.forEach { stroke ->
                    drawPath(stroke.toPath(), color = strokeColor, style = style)
                }
                if (currentStroke.isNotEmpty()) {
                    drawPath(currentStroke.toPath(), color = strokeColor, style = style)
                }
            }

            // 右侧 24dp fade 渐变（常驻，暗示可向右延伸）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(24.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, surfaceColor)
                        )
                    )
            )

            // 右上角工具栏：撤销 / 清空
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (completedStrokes.isNotEmpty()) {
                            completedStrokes.removeAt(completedStrokes.lastIndex)
                        }
                    },
                    enabled = completedStrokes.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "撤销上一笔"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { clearAll() },
                    enabled = completedStrokes.isNotEmpty() || currentStroke.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空画布"
                    )
                }
            }
        }
    }

}

/**
 * Sprint 1 Task 1.9 — 反馈样本载荷
 *
 * 由 [TestCanvas] 收集，[com.example.formulamaster.ui.screen.TestScreen] 弹反馈 Dialog
 * 让用户填正确 LaTeX 后入库。
 *
 * 注意：本类不持有 formulaId / recognizerType（这些在宿主页面已知，
 * 由宿主在落库时合成），保持 TestCanvas 对场景无感知。
 */
data class FeedbackPayload(
    val strokes: List<List<Pair<Float, Float>>>,
    val candidates: List<String>,
    val mode: RecognitionMode
)

/**
 * 顶部 80dp 实时预览条
 *
 * 三档状态显示：
 * - Loading：CircularProgressIndicator + "识别中…"
 * - 有结果：KaTeX 渲染 latex
 * - 空结果：占位文字
 *
 * 右侧按钮：
 * - 「👎」（IconButton）：上抛反馈 Payload，由宿主弹 Dialog（Task 1.9）
 * - 「强识别」（FilledTonalButton）：手动触发 Deep 识别
 * - 「采纳」（IconButton ✓）：把预览的 latex 提交为答案
 */
@Composable
private fun PreviewBar(
    latex: String,
    isLoading: Boolean,
    hasStrokes: Boolean,
    onAdopt: () -> Unit,
    onRecognizeDeep: () -> Unit,
    canRecognizeDeep: Boolean,
    isDeepLoading: Boolean,
    canReportFeedback: Boolean,
    onReportFeedback: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            when {
                isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "识别中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                latex.isNotBlank() -> MathFormulaView(
                    latex = latex,
                    modifier = Modifier.fillMaxSize()
                )
                hasStrokes -> Text(
                    text = "未识别到公式（点「强识别」试试更精确的）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    text = "写公式后停笔 300ms 自动预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(
            onClick = onReportFeedback,
            enabled = canReportFeedback,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp, vertical = 4.dp
            )
        ) {
            // material-icons-core 不含 ThumbDown，用文字标签替代（含义更直白）
            Text(
                text = "都不对",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        FilledTonalButton(
            onClick = onRecognizeDeep,
            enabled = canRecognizeDeep,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp, vertical = 4.dp
            )
        ) {
            if (isDeepLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (isDeepLoading) "强识别中" else "强识别",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onAdopt,
            enabled = latex.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "采纳预览结果"
            )
        }
    }
}
