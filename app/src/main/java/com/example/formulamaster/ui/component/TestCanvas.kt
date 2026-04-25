package com.example.formulamaster.ui.component

import android.os.Build
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.formulamaster.domain.MathOcrRecognizer
import com.example.formulamaster.domain.MockMathRecognizer
import com.example.formulamaster.domain.OcrInput
import com.example.formulamaster.domain.RecognitionMode
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
 *   - 停笔 [lightDebounceMs]（默认 300ms）→ [RecognitionMode.Light] → 顶部预览条即时反馈
 *   - 停笔 [deepDebounceMs]（默认 1500ms）→ [RecognitionMode.Deep] → 候选区多候选
 * - **顶部 40dp 预览条**：KaTeX 实时渲染 Light 结果 + "✓采纳"快捷按钮
 * - **画布横向自适应**：占满容器宽度（不嵌套 horizontalScroll，避免父子手势冲突）
 * - **右侧 fade 渐变**：24dp 宽渐变常驻右缘的视觉装饰
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
    recognizer: MathOcrRecognizer = remember { MockMathRecognizer() },
    lightDebounceMs: Long = 300L,
    deepDebounceMs: Long = 1_500L
) {
    // 已完成的笔画：每笔一组有序 Offset
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    // 当前正在绘制的笔画
    var currentStroke by remember { mutableStateOf(emptyList<Offset>()) }
    // Deep 识别候选（≤3）
    var candidates by remember { mutableStateOf(emptyList<String>()) }
    // Light 识别预览（top1）
    var previewLatex by remember { mutableStateOf("") }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    // ── 双档 debounce ────────────────────────────────────────────────────
    // Light：300ms 后刷新预览条
    LaunchedEffect(completedStrokes.size) {
        if (completedStrokes.isEmpty()) {
            previewLatex = ""
            return@LaunchedEffect
        }
        delay(lightDebounceMs)
        val snapshot = completedStrokes.map { stroke -> stroke.map { it.x to it.y } }
        // 必须 try-catch：识别器（尤其 ML Kit）可能抛 GmsUnavailable / 模型下载失败 /
        // 网络超时等异常，未捕获会冒泡到协程作用域导致 Composable 崩溃。
        // 失败时静默清空预览，不影响主流程；Task 1.8 接友好降级 UI 时再做用户提示。
        previewLatex = try {
            val result = recognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Light)
            result.firstOrNull().orEmpty()
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Light recognize failed", e)
            ""
        }
    }

    // Deep：1500ms 后填候选区
    LaunchedEffect(completedStrokes.size) {
        if (completedStrokes.isEmpty()) {
            candidates = emptyList()
            return@LaunchedEffect
        }
        delay(deepDebounceMs)
        val snapshot = completedStrokes.map { stroke -> stroke.map { it.x to it.y } }
        candidates = try {
            recognizer.recognize(OcrInput.StrokeInput(snapshot), RecognitionMode.Deep).take(3)
        } catch (e: Exception) {
            android.util.Log.w("TestCanvas", "Deep recognize failed", e)
            emptyList()
        }
    }

    fun clearAll() {
        completedStrokes.clear()
        currentStroke = emptyList()
        candidates = emptyList()
        previewLatex = ""
    }

    fun selectCandidate(latex: String) {
        if (latex.isBlank()) return
        onCandidateSelected(latex)
        clearAll()
    }

    Column(modifier = modifier) {
        // ── 顶部 40dp 实时预览条 ───────────────────────────────────────────
        PreviewBar(
            latex = previewLatex,
            onAdopt = { selectCandidate(previewLatex) }
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

            // 候选区（Deep 识别结果，1500ms 后以 slide + fade 出现）
            // 抽成私有 Composable 函数以隔离外层 Column / BoxWithConstraints scope，
            // 让 AnimatedVisibility 无歧义匹配顶层无 receiver 版本
            CandidatesOverlay(
                candidates = candidates,
                onSelect = { latex -> selectCandidate(latex) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                            candidates = emptyList()
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
 * 顶部 40dp 实时预览条
 *
 * - 左侧：KaTeX 渲染 Light 识别的 top1 候选（[latex] 为空时显示占位提示）
 * - 右侧：✓采纳按钮，等价于点击候选区该条目
 */
@Composable
private fun PreviewBar(
    latex: String,
    onAdopt: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (latex.isBlank()) {
                Text(
                    text = "停笔 300ms 显示预览…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MathFormulaView(
                    latex = latex,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
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

/**
 * 候选区覆盖层：AnimatedVisibility 包裹的 Deep 候选按钮排。
 *
 * 抽离为私有 Composable 的目的是隔离调用点的外层 scope
 * （调用处嵌套在 `Column { BoxWithConstraints { ... } }` 中，
 * 编译器会试图选择 `ColumnScope.AnimatedVisibility` 的扩展，
 * 导致 "cannot be called with implicit receiver" 编译错误）。
 * 在本函数内只有默认的 Composable scope，AnimatedVisibility 无歧义。
 */
@Composable
private fun CandidatesOverlay(
    candidates: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = candidates.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            candidates.forEach { latex ->
                FilledTonalButton(
                    onClick = { onSelect(latex) },
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = latex,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
