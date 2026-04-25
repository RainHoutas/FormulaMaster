package com.example.formulamaster.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.model.FormulaWithState

// ── 步骤状态枚举 ──────────────────────────────────────────────────────────────

private enum class ReviewStep { BLINDER, CLOZE, RATING }

// ── 评分按钮元数据 ────────────────────────────────────────────────────────────

private data class RatingMeta(val rating: Int, val label: String)

private val ratingMetaList = listOf(
    RatingMeta(1, "完全忘记"),
    RatingMeta(2, "有些困难"),
    RatingMeta(3, "比较顺利"),
    RatingMeta(4, "倒背如流")
)

// ── ReviewCard 主体 ───────────────────────────────────────────────────────────

/**
 * 三步复习卡片
 *
 * Step 1（BLINDER）：标题 + 遮罩，点击遮罩唤醒公式
 * Step 2（CLOZE）  ：带 [?] 占位符的填空公式 + 候选选项按钮
 * Step 3（RATING） ：完整公式 + 4 档 FSRS 自评按钮（滑入）
 *
 * @param onReviewSubmitted 用户点击评分后回调，传出 (rating, costTimeMs)
 */
@Composable
fun ReviewCard(
    item: FormulaWithState,
    onReviewSubmitted: (rating: Int, costTimeMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val formulaId = item.formula.formulaId

    // 以 formulaId 为 key，翻页时自动重置卡片状态
    var step             by remember(formulaId) { mutableStateOf(ReviewStep.BLINDER) }
    var selectedOptionIdx by remember(formulaId) { mutableStateOf<Int?>(null) }
    val revealTimeMs      = remember(formulaId)  { System.currentTimeMillis() }

    // 解析填空数据
    val clozeItems = remember(item.formula.clozeData) {
        ClozeParser.parse(item.formula.clozeData)
    }
    val clozeItem = clozeItems.firstOrNull()
    val hasCloze  = clozeItem != null

    // 生成带 [?] 占位符的 LaTeX（用 \text{[?]} 在 KaTeX 中显示）
    val clozeLatex = remember(item.formula.latexCode, clozeItem?.placeholder) {
        if (clozeItem != null)
            item.formula.latexCode.replace(clozeItem.placeholder, "\\text{[\\,?\\,]}")
        else
            item.formula.latexCode
    }

    // 选项列表（保持固定顺序，不每次重排）
    val options: List<String> = remember(clozeItem) { clozeItem?.options ?: emptyList() }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // ── 公式标题 ──────────────────────────────────────────────────────
        Text(
            text = item.formula.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${item.formula.subject} · ${item.formula.chapter}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // ── Step 1：遮罩（BLINDER）────────────────────────────────────────
        if (step == ReviewStep.BLINDER) {
            BlinderCard(
                onClick = {
                    step = if (hasCloze) ReviewStep.CLOZE else ReviewStep.RATING
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(150.dp)
            )
        }

        // ── Step 2/3：公式渲染区（展开动画）──────────────────────────────
        AnimatedVisibility(
            visible = step != ReviewStep.BLINDER,
            enter = expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            // Step 2 显示填空公式，Step 3 显示完整公式
            val latex = if (step == ReviewStep.RATING) item.formula.latexCode else clozeLatex
            MathFormulaView(
                latex = latex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Step 2：填空候选选项（CLOZE）──────────────────────────────────
        AnimatedVisibility(
            visible = step == ReviewStep.CLOZE || (step == ReviewStep.RATING && selectedOptionIdx != null),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ClozeOptions(
                options = options,
                correctAnswer = clozeItem?.placeholder ?: "",
                selectedIdx = selectedOptionIdx,
                onOptionSelected = { idx ->
                    if (selectedOptionIdx == null) {
                        selectedOptionIdx = idx
                        // 选完立即进入 Step 3，评分按钮滑入
                        step = ReviewStep.RATING
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        // ── Task 5.4：顽固难点（Leech）tags 提示 ──────────────────────────
        // 仅在评分阶段且 lapses ≥ 4 时出现，展示应用场景标签，辅助记忆关联
        AnimatedVisibility(
            visible = step == ReviewStep.RATING && item.lapses >= 4,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LeechTagsFooter(
                tags = item.formula.tags,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Step 3：FSRS 自评按钮（从底部滑入）───────────────────────────
        AnimatedVisibility(
            visible = step == ReviewStep.RATING,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
        ) {
            RatingButtons(
                onRatingSelected = { rating ->
                    val costMs = System.currentTimeMillis() - revealTimeMs
                    onReviewSubmitted(rating, costMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

// ── Step 1：遮罩卡片 ──────────────────────────────────────────────────────────

@Composable
private fun BlinderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "点击唤醒公式",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "先在脑中尝试回忆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── Step 2：填空选项 ───────────────────────────────────────────────────────────

@Composable
private fun ClozeOptions(
    options: List<String>,
    correctAnswer: String,
    selectedIdx: Int?,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { idx, option ->
            val isSelected = idx == selectedIdx
            val isCorrect  = option == correctAnswer

            // 未选择时正常；选择后：选中正确=绿，选中错误=红，正确答案=绿
            val containerColor = when {
                selectedIdx == null          -> MaterialTheme.colorScheme.secondaryContainer
                isSelected && isCorrect      -> MaterialTheme.colorScheme.secondaryContainer
                isSelected && !isCorrect     -> MaterialTheme.colorScheme.errorContainer
                !isSelected && isCorrect     -> MaterialTheme.colorScheme.secondaryContainer
                else                         -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when {
                selectedIdx == null          -> MaterialTheme.colorScheme.onSecondaryContainer
                isSelected && isCorrect      -> MaterialTheme.colorScheme.onSecondaryContainer
                isSelected && !isCorrect     -> MaterialTheme.colorScheme.onErrorContainer
                !isSelected && isCorrect     -> MaterialTheme.colorScheme.onSecondaryContainer
                else                         -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            FilledTonalButton(
                onClick = { onOptionSelected(idx) },
                enabled = selectedIdx == null,   // 选完后禁止再改
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor         = containerColor,
                    contentColor           = contentColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor   = contentColor
                )
            ) {
                // 显示 LaTeX 源码（Sprint 6 WebViewPool 就绪后可改为 MathFormulaView）
                Text(
                    text = option,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Step 3：FSRS 自评按钮 ─────────────────────────────────────────────────────

@Composable
private fun RatingButtons(
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ratingMetaList.forEach { meta ->
            val containerColor = when (meta.rating) {
                1    -> MaterialTheme.colorScheme.errorContainer
                2    -> MaterialTheme.colorScheme.tertiaryContainer
                3    -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val contentColor = when (meta.rating) {
                1    -> MaterialTheme.colorScheme.onErrorContainer
                2    -> MaterialTheme.colorScheme.onTertiaryContainer
                3    -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }

            Surface(
                onClick = { onRatingSelected(meta.rating) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                contentColor = contentColor
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = meta.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${meta.rating}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ── Task 5.4：Leech 公式 tags 提示条 ──────────────────────────────────────────
//
// 仅在评分阶段且 lapses ≥ 4 时展示，把 formula.tags（应用场景）拆成 M3 chip 风格
// 小色块，帮助用户把"容易错的公式"和"实际场景"建立强关联。
// 不占位：非 Leech 情况下通过外层 AnimatedVisibility 完全隐藏。

@Composable
private fun LeechTagsFooter(
    tags: String,
    modifier: Modifier = Modifier
) {
    if (tags.isBlank()) return
    val tagList = remember(tags) {
        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    if (tagList.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "⚠️ 顽固难点 · 应用场景",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = tagList.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
