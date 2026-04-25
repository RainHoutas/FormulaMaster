package com.example.formulamaster.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Task 5.5：学习热力图（GitHub Style 方格日历）。
 *
 * - 固定 [WEEKS] = 53 列（过去 52 周 + 本周）× 7 行（周一 → 周日）
 * - 右下角对齐"今天"，今天之后的格子不绘制（留空白）
 * - 4 档颜色：0 / 1~3 / 4~9 / 10+（颜色由 M3 colorScheme.primary 派生 alpha）
 *
 * 交互：点击任意格子 → [onDayClicked] 回调 (LocalDate, Int)，
 * 外层可用 Snackbar / Tooltip 展示"该日复习 N 次"。
 *
 * 纯无状态 Composable，数据由 ViewModel 预先按日聚合。
 */
@Composable
fun HeatmapCalendar(
    dayCounts: Map<LocalDate, Int>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    cellSize: Dp = 12.dp,
    cellSpacing: Dp = 3.dp,
    onDayClicked: (LocalDate, Int) -> Unit = { _, _ -> }
) {
    val colors = heatmapColors()

    // 今天坐标：最右列、dayOfWeek 对应行（周一=0..周日=6）
    val todayRow = (today.dayOfWeek.value - 1).coerceIn(0, 6)
    val todayCol = WEEKS - 1

    // 左上角那个格子对应的日期（无记录时 count=0）
    val earliestDate = remember(today) {
        today.minusDays((todayCol * 7 + todayRow).toLong())
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            WeekdayLabels(cellSize = cellSize, cellSpacing = cellSpacing)
            Spacer(Modifier.width(4.dp))
            HeatmapGrid(
                dayCounts = dayCounts,
                today = today,
                earliestDate = earliestDate,
                cellSize = cellSize,
                cellSpacing = cellSpacing,
                colors = colors,
                onDayClicked = onDayClicked
            )
        }

        Spacer(Modifier.height(8.dp))

        HeatmapLegend(colors = colors, cellSize = cellSize, cellSpacing = cellSpacing)
    }
}

// ── 常量 ──────────────────────────────────────────────────────────────────────

private const val WEEKS = 53

// ── 方格主体（绘制 + 点击反查叠加在同一 Box） ──────────────────────────────────

@Composable
private fun HeatmapGrid(
    dayCounts: Map<LocalDate, Int>,
    today: LocalDate,
    earliestDate: LocalDate,
    cellSize: Dp,
    cellSpacing: Dp,
    colors: HeatmapColors,
    onDayClicked: (LocalDate, Int) -> Unit
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val spacingPx = with(density) { cellSpacing.toPx() }
    val step = cellPx + spacingPx

    val widthDp = cellSize * WEEKS + cellSpacing * (WEEKS - 1)
    val heightDp = cellSize * 7 + cellSpacing * 6

    Box(
        modifier = Modifier
            .size(width = widthDp, height = heightDp)
            .pointerInput(earliestDate, today, dayCounts) {
                detectTapGestures { offset ->
                    hitTest(offset, step, earliestDate, today)?.let { date ->
                        onDayClicked(date, dayCounts[date] ?: 0)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.size(width = widthDp, height = heightDp)) {
            drawGrid(
                dayCounts = dayCounts,
                today = today,
                earliestDate = earliestDate,
                cellPx = cellPx,
                spacingPx = spacingPx,
                colors = colors
            )
        }
    }
}

/**
 * 把屏幕点击坐标反查回 LocalDate。
 * 落在格子间隙或未来日期返回 null。
 */
private fun hitTest(
    offset: Offset,
    step: Float,
    earliestDate: LocalDate,
    today: LocalDate
): LocalDate? {
    val col = (offset.x / step).toInt()
    val row = (offset.y / step).toInt()
    if (col !in 0 until WEEKS || row !in 0..6) return null
    val date = earliestDate.plusDays((col * 7 + row).toLong())
    return if (date.isAfter(today)) null else date
}

// ── 绘制（纯数学循环，不涉及状态） ─────────────────────────────────────────────

private fun DrawScope.drawGrid(
    dayCounts: Map<LocalDate, Int>,
    today: LocalDate,
    earliestDate: LocalDate,
    cellPx: Float,
    spacingPx: Float,
    colors: HeatmapColors
) {
    val step = cellPx + spacingPx
    for (col in 0 until WEEKS) {
        for (row in 0..6) {
            val date = earliestDate.plusDays((col * 7 + row).toLong())
            if (date.isAfter(today)) continue   // 未来留白
            val count = dayCounts[date] ?: 0
            drawRect(
                color = colors.forCount(count),
                topLeft = Offset(col * step, row * step),
                size = Size(cellPx, cellPx)
            )
        }
    }
}

// ── 周几标签（只显示 Mon / Wed / Fri） ────────────────────────────────────────

@Composable
private fun WeekdayLabels(cellSize: Dp, cellSpacing: Dp) {
    val labels = listOf("Mon", "", "Wed", "", "Fri", "", "")
    Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
        labels.forEach { label ->
            Box(modifier = Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── 图例：Less → More ────────────────────────────────────────────────────────

@Composable
private fun HeatmapLegend(
    colors: HeatmapColors,
    cellSize: Dp,
    cellSpacing: Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        listOf(colors.l0, colors.l1, colors.l2, colors.l3).forEach { c ->
            Box(
                modifier = Modifier
                    .size(cellSize)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Canvas(modifier = Modifier.size(cellSize)) {
                    drawRect(c, size = Size(cellSize.toPx(), cellSize.toPx()))
                }
            }
        }
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ── 颜色映射 ──────────────────────────────────────────────────────────────────

private data class HeatmapColors(
    val l0: Color,   // 0 次
    val l1: Color,   // 1~3 次
    val l2: Color,   // 4~9 次
    val l3: Color    // 10+ 次
) {
    fun forCount(n: Int): Color = when {
        n <= 0 -> l0
        n <= 3 -> l1
        n <= 9 -> l2
        else   -> l3
    }
}

@Composable
private fun heatmapColors(): HeatmapColors {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    return HeatmapColors(
        l0 = empty,
        l1 = primary.copy(alpha = 0.2f),
        l2 = primary.copy(alpha = 0.6f),
        l3 = primary
    )
}
