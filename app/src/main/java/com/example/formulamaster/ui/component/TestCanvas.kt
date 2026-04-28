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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * - **Palm Rejection**：`pointerInteropFilter` 过滤 [MotionEvent.TOOL_TYPE_PALM]，
 *   避免写字时手掌误触造成多余笔画
 * - **双档 debounce**：
 *   - 停笔 [lightDebounceMs]（默认 300ms）→ [lightRecognizer] → 顶部预览条即时反馈
 *   - 停笔 [deepDebounceMs]（默认 1500ms）→ [deepRecognizer] → 候选区多候选
 * - **顶部 40dp 预览条**：KaTeX 实时渲染 Light 结果 + "✓采纳"快捷按钮
 * - **画布横向自适应**：占满容器宽度（不嵌套 horizontalScroll，避免父子手势冲突）
 * - **右侧 fade 渐变**：24dp 宽渐变常驻右缘的视觉装饰
 *
 * ## Sprint 1 Task 1.7 升级
 * - Light/Deep 两档分别接受独立的识别器（来自 `RecognizerPreference` + `RecognizerRegistry.resolveLight/Deep`），
 *   用户可在设置页绑定不同识别器到两档
 * - null 表示该档未绑定（不触发识别），避免静默失败
 *
 * ## 架构说明
 * OCR 通过 [MathOcrRecognizer] 接口注入（依赖倒置），UI 层零感知具体实现。
 * 输入使用 [OcrInput.StrokeInput] 传递坐标列表，规避 Bitmap 截图成本。
 * `pointerInteropFilter` 标记为 Experimental，但系目前在 Compose 中
 * 访问原生 `MotionEvent.getToolType()` 的唯一稳定途径（Compose `PointerType`
 * 不包含 PALM 分类），属合法使用。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TestCanvas(
    onCandidateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    lightRecognizer: MathOcrRecognizer? = remember { MockMathRecognizer() },
    deepRecognizer: MathOcrRecognizer? = remember { MockMathRecognizer() },
    lightDebounceMs: Long = 300L,
    deepDebounceMs: Long = 1_500L
) {
    // 已完成的笔画：每笔一组有序 Offset
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    // 当前正在绘制的笔画
    var currentStroke by remember { mutableStateOf(emptyList<Offset>()) }
    // 当前预览的 LaTeX（Light 自动写入；强识别成功后覆盖；采纳后清空）
    var previewLatex by remember { mutableStateOf("") }
    // Light 请求是否在飞行中（用于 PreviewBar 显示"识别中…"）
    var lightLoading by remember { mutableStateOf(false) }
    // Deep 手动触发计数器：++ 一次触发一次 Deep 识别（避免 Boolean 边沿同步问题）
    var deepTriggerNonce by remember { mutableIntStateOf(0) }
    // Deep 请求是否在飞行中
    var deepLoading by remember { mutableStateOf(false) }
    // Deep 请求结果反馈：失败时显示 2s 提示（成功默默覆盖 previewLatex 即可）
    var deepFailureMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepFailureMessage) {
        if (deepFailureMessage != null) {
            delay(2500L)
            deepFailureMessage = null
        }
    }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

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
        // 必须 try-catch：识别器可能抛网络超时 / Key 失效 / 服务异常等，
        // 未捕获会冒泡到协程作用域导致 Composable 崩溃。
        previewLatex = try {
            val result = lightRecognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Light)
            // 应用 LatexNormalizer 把 Unicode 符号、裸函数名等清理为 KaTeX 兼容形式
            result.firstOrNull()?.let { LatexNormalizer.normalize(it) }.orEmpty()
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Light recognize failed", e)
            ""
        } finally {
            lightLoading = false
        }
    }

    // ── Deep：手动触发（避免浪费 standard 端点 500/天的额度） ────────────
    // 设计：单结果直接覆盖 previewLatex（去除弹窗确认环节），失败时 2.5s 提示
    LaunchedEffect(deepTriggerNonce) {
        if (deepTriggerNonce == 0) return@LaunchedEffect  // 初始值，不触发
        if (completedStrokes.isEmpty() || deepRecognizer == null) return@LaunchedEffect

        val snapshot = completedStrokes.map { stroke -> stroke.map { it.x to it.y } }
        deepLoading = true
        try {
            val raw = deepRecognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Deep)
            val cleaned = LatexNormalizer.normalizeAndFilter(raw).firstOrNull()
            if (cleaned != null) {
                previewLatex = cleaned   // 直接覆盖 Light 的结果
                deepFailureMessage = null
            } else {
                deepFailureMessage = "强识别未识别到公式"
            }
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Deep recognize failed", e)
            deepFailureMessage = "强识别失败：${e.message?.take(40) ?: e.javaClass.simpleName}"
        } finally {
            deepLoading = false
        }
    }

    fun clearAll() {
        completedStrokes.clear()
        currentStroke = emptyList()
        previewLatex = ""
        deepFailureMessage = null
    }

    fun selectCandidate(latex: String) {
        if (latex.isBlank()) return
        onCandidateSelected(latex)
        clearAll()
    }

    Column(modifier = modifier) {
        // ── 顶部 80dp 实时预览条 ───────────────────────────────────────────
        PreviewBar(
            latex = previewLatex,
            isLoading = lightLoading,
            hasStrokes = completedStrokes.isNotEmpty(),
            deepFailureMessage = deepFailureMessage,
            onAdopt = { selectCandidate(previewLatex) },
            onRecognizeDeep = { deepTriggerNonce++ },
            canRecognizeDeep = completedStrokes.isNotEmpty() && deepRecognizer != null && !deepLoading,
            isDeepLoading = deepLoading
        )

        // ── 主画布区（剩余空间） ───────────────────────────────────────────
        // 注意：画布占满容器宽度，不嵌套 horizontalScroll。
        // 历史教训：之前用 horizontalScroll + 动态画布宽度想做"无限横向延伸"，
        // 结果父级 horizontalScroll 的 scrollable modifier 在水平滑动超过 touch slop
        // 时 intercept 事件，子级 pointerInteropFilter 只收到 ACTION_DOWN 然后 CANCEL，
        // 表现为"按下立即断笔 + 画板抖动"。默写场景一次只写一个短公式就采纳/清空，
        // 不需要无限滚动；"横向自适应"= 充满容器宽度即可。
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
                        // MotionEvent.TOOL_TYPE_PALM 于 API 33（Android 13）引入；
                        // 低版本上 palm 事件会作为普通 touch，回退到原行为。
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
                            // previewLatex 会在下一轮 Light debounce 自动刷新（或清空）
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
 * 顶部 80dp 实时预览条
 *
 * 三档状态显示：
 * - Loading：CircularProgressIndicator + "识别中…"
 * - 有结果：KaTeX 渲染 latex（MathFormulaView 占满高度，复杂上下标也能完整显示）
 * - 空结果：占位文字（区分"还没写"和"写了但识别为空"）
 *
 * 右侧两个按钮：
 * - 「强识别」（FilledTonalButton）：手动触发 Deep 识别（更准确但耗 standard 额度）
 * - 「采纳」（IconButton ✓）：把预览的 latex 提交为答案
 */
@Composable
private fun PreviewBar(
    latex: String,
    isLoading: Boolean,
    hasStrokes: Boolean,
    deepFailureMessage: String?,
    onAdopt: () -> Unit,
    onRecognizeDeep: () -> Unit,
    canRecognizeDeep: Boolean,
    isDeepLoading: Boolean
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
                // 强识别失败提示优先级最高（2.5s 内覆盖任何其他状态）
                deepFailureMessage != null -> Text(
                    text = deepFailureMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
        Spacer(modifier = Modifier.width(8.dp))
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

