package com.example.formulamaster.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 公式临摹画布：
 * - 底层以 30% 透明度显示正确公式（KaTeX）作为描红参考
 * - 手指可在上方绘制笔迹
 * - 右上角清空按钮
 */
@Composable
fun TracingCanvas(
    latexCode: String,
    modifier: Modifier = Modifier
) {
    // 已完成的笔画（每笔一组点）
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    // 当前正在绘制的笔画
    var currentStroke by remember { mutableStateOf(emptyList<Offset>()) }

    val strokeColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier) {
        // ── 参考公式（30% 透明度）────────────────────────────────────────────
        MathFormulaView(
            latex = latexCode,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .alpha(0.3f)
                .align(Alignment.Center)
        )

        // ── 绘制层 ──────────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            currentStroke = currentStroke + change.position
                        },
                        onDragEnd = {
                            if (currentStroke.isNotEmpty()) {
                                completedStrokes.add(currentStroke)
                            }
                            currentStroke = emptyList()
                        },
                        onDragCancel = {
                            currentStroke = emptyList()
                        }
                    )
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

        // ── 清空按钮 ─────────────────────────────────────────────────────────
        IconButton(
            onClick = {
                completedStrokes.clear()
                currentStroke = emptyList()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "清空画布"
            )
        }
    }
}
