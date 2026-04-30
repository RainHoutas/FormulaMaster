package com.example.formulamaster.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Sprint 1 Task 1.9 — 识别失败反馈对话框
 *
 * 用户在 TestCanvas 点击「都不对」按钮后弹出本对话框，让用户：
 * 1. 看到自己刚才写的笔画（小型预览）
 * 2. 看到识别器返回的所有候选（chip 列表）
 * 3. 手输正确的 LaTeX
 * 4. 提交 → 由 [onSubmit] 回调入库
 *
 * ## 设计权衡
 * - 笔画预览：自动等比缩放至填满 180dp 区域；若用户笔画跨度极小亦能看清
 * - 候选区为空时显示"识别器无返回"；不阻塞用户提交（这本身就是有价值的失败样本）
 * - 正确 LaTeX 输入框初始为空，不预填候选第一项（避免诱导用户偷懒接受错误结果）
 */
@Composable
fun FeedbackDialog(
    strokes: List<List<Pair<Float, Float>>>,
    candidates: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (correctLatex: String) -> Unit
) {
    var correctLatex by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("反馈识别错误") },
        text = {
            Column {
                Text(
                    text = "你写的内容：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                StrokesPreview(
                    strokes = strokes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "识别返回（共 ${candidates.size} 个候选）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (candidates.isEmpty()) {
                    Text(
                        text = "（识别器无返回）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        candidates.take(5).forEach { c ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        text = c,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = correctLatex,
                    onValueChange = { correctLatex = it },
                    label = { Text("正确的 LaTeX") },
                    placeholder = { Text("\\frac{a}{b}") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "你的反馈样本会在本机加密存储，可在设置页一键导出 JSON。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(correctLatex.trim()) },
                enabled = correctLatex.trim().isNotBlank()
            ) {
                Text("提交反馈")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

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
