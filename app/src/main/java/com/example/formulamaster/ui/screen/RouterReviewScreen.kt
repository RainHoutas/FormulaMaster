package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.viewmodel.RouterReviewViewModel

/**
 * Sprint 2 Task 2.1c / 2.2：路由驱动的复习屏。
 *
 * 卡型 UI 分化：
 * - **C1 识别卡**（Task 2.2）：专属 [C1RecognitionPane]，看答案后露出「公式 + 适用条件 + 用途 + 口诀」
 *   同卡内分段（小标题 + 分隔线）。
 * - C2/C3/C4/C5/C6：暂用通用 [ShowCardPane]"卡片骨架"（reveal latex），各自专属交互见
 *   Task 2.3 / 2.4 / Sprint 3。
 *
 * 三种 [ReviewRouter.NextAction] 渲染：
 * - [ReviewRouter.NextAction.ShowCard]：按 cardType 选 C1 专属 / 通用骨架
 * - [ReviewRouter.NextAction.StartDictation]：wasPreviouslyBlocked 红条 + 完整公式 + hint 标签 + 通过/没通过
 * - [ReviewRouter.NextAction.SessionEnd]：完成页
 */
@Composable
fun RouterReviewScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: RouterReviewViewModel = viewModel(
        factory = RouterReviewViewModel.factory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    // 启动会话（每次首次组装时触发一次）
    LaunchedEffect(Unit) {
        viewModel.startSession()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            }

            uiState.isSessionEnd -> {
                RouterSessionEnd(modifier = Modifier.fillMaxSize())
            }

            else -> when (val action = uiState.pendingAction) {
                is ReviewRouter.NextAction.ShowCard -> {
                    val isReinforced = uiState.currentSubCard?.isReinforced == true
                    if (action.cardType == CardType.C1_Recognition) {
                        C1RecognitionPane(
                            action = action,
                            formulaTitle = uiState.currentFormula?.title.orEmpty(),
                            formulaLatex = uiState.currentFormula?.latexCode.orEmpty(),
                            preconditions = uiState.currentPreconditions,
                            purpose = uiState.currentFormula?.purpose.orEmpty(),
                            mnemonic = uiState.currentFormula?.mnemonic,
                            isReinforced = isReinforced,
                            onRate = viewModel::rate,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ShowCardPane(
                            action = action,
                            formulaTitle = uiState.currentFormula?.title.orEmpty(),
                            formulaLatex = uiState.currentFormula?.latexCode.orEmpty(),
                            isReinforced = isReinforced,
                            onRate = viewModel::rate,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                is ReviewRouter.NextAction.StartDictation -> {
                    DictationPane(
                        action = action,
                        formulaTitle = uiState.currentFormula?.title.orEmpty(),
                        formulaLatex = uiState.currentFormula?.latexCode.orEmpty(),
                        onResult = viewModel::submitDictation,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ReviewRouter.NextAction.SessionEnd -> {
                    RouterSessionEnd(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── C1 识别卡专属面板（Task 2.2） ───────────────────────────────────────────────

/**
 * C1 识别卡：公式名 → 看答案 → 露出「公式 + 适用条件 + 用途 + 口诀」。
 *
 * 露出区为同一张 [ElevatedCard] 内分段（小标题 + [HorizontalDivider]）；口诀仅当
 * [mnemonic] 非空非空白时渲染。整段可滚动，避免长条件 / 长用途撑爆。
 */
@Composable
private fun C1RecognitionPane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    formulaLatex: String,
    preconditions: List<String>,
    purpose: String,
    mnemonic: String?,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 路由器推下一张卡（formulaId/cardType 变）时 reveal 重置回 false
    var revealed by rememberSaveable(action.formulaId, action.cardType) { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        CardHeaderChips(
            cardType = action.cardType,
            isReinforced = isReinforced,
            isReinforcementRetest = action.isReinforcementRetest
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = formulaTitle.ifEmpty { "（公式标题缺失）" },
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(20.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "回想这个公式的完整形式、适用条件、用途",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!revealed) {
                    Text(
                        text = "（点击下方「看答案」露出）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // ① 公式本体
                    MathFormulaView(
                        latex = formulaLatex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    // ② 适用条件
                    RevealSectionDivider()
                    SectionLabel("适用条件")
                    if (preconditions.isEmpty()) {
                        Text(
                            text = "（暂未标注）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        preconditions.forEach { cond ->
                            Text(
                                text = "• $cond",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // ③ 用途
                    RevealSectionDivider()
                    SectionLabel("用途")
                    Text(
                        text = purpose.ifBlank { "（暂未标注）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (purpose.isBlank()) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    // ④ 口诀（仅有值时）
                    if (!mnemonic.isNullOrBlank()) {
                        RevealSectionDivider()
                        SectionLabel("💡 口诀")
                        Text(
                            text = mnemonic,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!revealed) {
            Button(
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("看答案")
            }
        } else {
            RatingRow(onRate = { rating -> onRate(rating); revealed = false })
        }
    }
}

@Composable
private fun RevealSectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

// ── ShowCard 通用骨架（C2-C6 暂用） ─────────────────────────────────────────────

@Composable
private fun ShowCardPane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    formulaLatex: String,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 用 action 标识做 key：路由器推下一张卡时 reveal 状态重置回 false
    var revealed by rememberSaveable(action.formulaId, action.cardType) { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        CardHeaderChips(
            cardType = action.cardType,
            isReinforced = isReinforced,
            isReinforcementRetest = action.isReinforcementRetest
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = formulaTitle.ifEmpty { "（公式标题缺失）" },
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(20.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = cardPrompt(action.cardType),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (revealed) {
                    MathFormulaView(
                        latex = formulaLatex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                } else {
                    Text(
                        text = "（点击下方「看答案」露出）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!revealed) {
            FilledTonalButton(
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("看答案")
            }
        } else {
            RatingRow(onRate = { rating -> onRate(rating); revealed = false })
        }
    }
}

// ── 共用：顶部 chips 行 + 评分行 ────────────────────────────────────────────────

@Composable
private fun CardHeaderChips(
    cardType: CardType,
    isReinforced: Boolean,
    isReinforcementRetest: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardTypeChip(cardType)
        if (isReinforced) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "⚠ 强标记",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        if (isReinforcementRetest) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "⚡ 加强卡回考",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun RatingRow(onRate: (Int) -> Unit) {
    Column {
        Text(
            text = "评分",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingButton(label = "1 不会", onClick = { onRate(1) }, modifier = Modifier.weight(1f))
            RatingButton(label = "2 模糊", onClick = { onRate(2) }, modifier = Modifier.weight(1f))
            RatingButton(label = "3 想起", onClick = { onRate(3) }, modifier = Modifier.weight(1f))
            RatingButton(label = "4 一眼出", onClick = { onRate(4) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RatingButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CardTypeChip(cardType: CardType) {
    val text = when (cardType) {
        CardType.C1_Recognition     -> "C1 识别"
        CardType.C2_Cloze           -> "C2 填空"
        CardType.C3_Precondition    -> "C3 条件"
        CardType.C4_Derivation      -> "C4 推导"
        CardType.C5_Discrimination  -> "C5 易混"
        CardType.C6_TypicalProblem  -> "C6 题型"
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun cardPrompt(cardType: CardType): String = when (cardType) {
    CardType.C1_Recognition     -> "回想公式名对应的完整公式 + 适用条件 + 用途"
    CardType.C2_Cloze           -> "回想被挖空的公式部件"
    CardType.C3_Precondition    -> "先看条件，再回想公式"
    CardType.C4_Derivation      -> "回想推导步骤"
    CardType.C5_Discrimination  -> "辨析易混公式"
    CardType.C6_TypicalProblem  -> "回想此题型应使用的公式"
}

// ── 默写子面板 ─────────────────────────────────────────────────────────────────

@Composable
private fun DictationPane(
    action: ReviewRouter.NextAction.StartDictation,
    formulaTitle: String,
    formulaLatex: String,
    onResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // 上次被阻断的强提醒红条
        if (action.wasPreviouslyBlocked) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠ 上次默写被阻断 · 本次格外仔细",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "默写：${formulaTitle.ifEmpty { "公式" }}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = hintText(action.hintLevel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 显示完整公式作为参考答案（MVP：用户对照自评通过/没通过；后续 Task 接 TracingCanvas/PaperPen）
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "目标公式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                MathFormulaView(
                    latex = formulaLatex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onResult(false) },
                modifier = Modifier.weight(1f)
            ) {
                Text("没默出")
            }
            Button(
                onClick = { onResult(true) },
                modifier = Modifier.weight(1f)
            ) {
                Text("默对了")
            }
        }
    }
}

private fun hintText(hintLevel: Int): String = when (hintLevel) {
    0 -> "首发：不看公式回想"
    1 -> "Hint 1：露公式第一块"
    2 -> "Hint 2：露推导前两步"
    else -> "Hint $hintLevel"
}

// ── 会话结束页 ─────────────────────────────────────────────────────────────────

@Composable
private fun RouterSessionEnd(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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
            text = "今日复习已完成",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "保持节奏，明天继续",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
