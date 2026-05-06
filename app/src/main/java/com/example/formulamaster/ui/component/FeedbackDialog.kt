package com.example.formulamaster.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Sprint 1 Task 1.9 / Sprint 3 Task 3.4 — 识别失败反馈对话框
 *
 * 用户在 TestCanvas 点击「都不对」按钮后弹出本对话框，让用户：
 * 1. 看到自己刚才写的笔画（小型预览）
 * 2. 看到识别器返回的所有候选（横向滚动 chip 列表）
 * 3. 看到当前公式的标准渲染（[MathFormulaView]，对照排错）
 * 4. 从公式 placeholder 列表中**多选**错误部件（[LatexChipsView]，KaTeX 渲染）
 * 5. 提交 → 由 [onSubmit] 回调上抛 wrongPlaceholders 列表
 *
 * ## 候选 / 多选区共用单 WebView 渲染
 * 候选区（只读）和多选区（可选中）都用 [LatexChipsView]——一份 HTML 模板 +
 * `selectable: Boolean` 切换交互。两者视觉/性能特征一致，仅交互能力有别。
 *
 * ## 布局演进
 * Sprint 3 Task 3.4 初版用 [androidx.compose.material3.AlertDialog]，但：
 * - AlertDialog 默认 confirmButton/dismissButton 被强制塞同一行；放 3 按钮（取消/都不对/
 *   标记错误部件）会被自动折成 2 行，浪费大量垂直空间
 * - AlertDialog 高度被平台限制（约 ~60% 屏高），chip 多选区被压扁看不全
 *
 * 改用 [Dialog] + [Surface] 自定义：
 * - 95% 屏宽 + 88% 屏高，chip 区有充足展示空间
 * - 底部 2 按钮（取消 + 主按钮）单行紧凑
 * - 候选区改 [LazyRow] 横向滚动，节省垂直空间
 *
 * ## 「都不对」按钮去除（用户反馈）
 * 原 spec 的「都不对」按钮（全选所有 placeholder）实际使用频率极低——
 * 用户在 chip 区里 6 个 placeholder 全选只比 1 个多 5 次点击，不值得占用底部按钮槽位。
 *
 * 兜底语义保留在主按钮上：
 * - hasPlaceholders=false（clozeData 空数组）→ 主按钮文案"提交反馈"，永远可点，提交空列表
 * - hasPlaceholders=true && selected 空 → 主按钮文案"标记错误部件"，禁用
 * - hasPlaceholders=true && selected 非空 → 主按钮文案"标记错误部件"，可点
 */
@Composable
fun FeedbackDialog(
    strokes: List<List<Pair<Float, Float>>>,
    candidates: List<String>,
    formulaLatex: String,
    placeholders: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (wrongPlaceholders: List<String>) -> Unit
) {
    // 用户多选的 placeholder 索引集合（基于位置 index，避免重复 placeholder 导致的歧义）
    // selection 由 [PlaceholderChipsView] 内部 JS 维护，通过回调上抛
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val hasPlaceholders = placeholders.isNotEmpty()
    // 主按钮可用条件：
    // - 无 placeholder 场景：永远可用（提交空列表表示整体失败）
    // - 有 placeholder 场景：必须至少选中 1 个
    val canSubmit = !hasPlaceholders || selected.isNotEmpty()

    // 高度上限 = 88% 屏高。用 heightIn(max=...) 而非 fillMaxHeight(0.88f)，
    // 这样内容少时 dialog 自然收缩，不会出现下方大片空白
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.88).dp

    Dialog(
        onDismissRequest = onDismiss,
        // 解除平台默认宽度限制，允许我们用 fillMaxWidth(0.95f) 控制实际宽度
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = maxDialogHeight)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // ── 标题 ──────────────────────────────────────────────────
                Text(
                    text = "反馈识别错误",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))

                // ── 滚动内容区 ────────────────────────────────────────────
                // weight(1f, fill = false)：内容比上限矮时按 wrap content 排版（dialog 收缩），
                // 内容超上限时被 weight 收紧到剩余空间，触发内部滚动。
                // fill=true（默认）会强制撑满 weight 分配的高度 → 内容少时下方空白
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 你写的内容（笔画预览）
                    SectionLabel("你写的内容：")
                    StrokesPreview(
                        strokes = strokes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(STROKES_HEIGHT_DP.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(12.dp))

                    // 2. 识别返回的候选（KaTeX 渲染，只读模式 chip）
                    SectionLabel("识别返回（共 ${candidates.size} 个候选）：")
                    if (candidates.isEmpty()) {
                        Text(
                            text = "（识别器无返回）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LatexChipsView(
                            items = candidates.take(MAX_CANDIDATES_SHOWN),
                            selectable = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 3. 当前公式渲染（对照排错）
                    SectionLabel("当前公式：")
                    MathFormulaView(
                        latex = formulaLatex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FORMULA_HEIGHT_DP.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    // 4. 错误部件多选区（KaTeX 渲染的可点击 chip）
                    SectionLabel("标记识别错误的子部件（可多选）：")
                    if (!hasPlaceholders) {
                        // 空数组兜底：提示文字，主按钮文案变"提交反馈"
                        Text(
                            text = "（本公式无可选部件，可直接提交整体反馈）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LatexChipsView(
                            items = placeholders,
                            selectable = true,
                            onSelectionChanged = { ids -> selected = ids },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "你的反馈样本会在本机存储，可在设置页一键导出 JSON。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── 底部按钮：紧凑单行，靠右对齐 ───────────────────────────
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val list = if (hasPlaceholders) {
                                selected.sorted().map { placeholders[it] }
                            } else {
                                emptyList()  // 空 placeholder 场景：整体失败
                            }
                            onSubmit(list)
                        },
                        enabled = canSubmit
                    ) {
                        Text(
                            text = if (hasPlaceholders) "标记错误部件"
                                   else "提交反馈"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
}

private const val STROKES_HEIGHT_DP = 120
private const val FORMULA_HEIGHT_DP = 100
private const val MAX_CANDIDATES_SHOWN = 5

/**
 * 笔画预览：等比缩放笔画到容器内，留 8dp 内边距。
 *
 * 实现细节：
 * - 计算所有笔画 bounding box；按 min(scaleX, scaleY) 等比缩放避免变形
 * - 笔画粗细用 4f（比真画布的 8f 细一半，对应到缩放后的视觉密度刚好）
 * - 容器尺寸为 0 时退化为 no-op（避免除以 0）
 */
@Composable
private fun StrokesPreview(
    strokes: List<List<Pair<Float, Float>>>,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return@Canvas
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        strokes.forEach { stroke ->
            stroke.forEach { (x, y) ->
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        val rawW = (maxX - minX).coerceAtLeast(1f)
        val rawH = (maxY - minY).coerceAtLeast(1f)
        val padding = 16f
        val drawableW = (size.width - 2f * padding).coerceAtLeast(1f)
        val drawableH = (size.height - 2f * padding).coerceAtLeast(1f)
        val scale = minOf(drawableW / rawW, drawableH / rawH)
        val offsetX = padding + (drawableW - rawW * scale) / 2f
        val offsetY = padding + (drawableH - rawH * scale) / 2f

        val style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val path = Path()
            val (sx, sy) = stroke.first()
            path.moveTo(offsetX + (sx - minX) * scale, offsetY + (sy - minY) * scale)
            stroke.drop(1).forEach { (x, y) ->
                path.lineTo(offsetX + (x - minX) * scale, offsetY + (y - minY) * scale)
            }
            drawPath(path, color = color, style = style)
        }
    }
}
