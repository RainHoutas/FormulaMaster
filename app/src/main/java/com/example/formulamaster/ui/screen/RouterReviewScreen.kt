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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.C6Grading
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ClozeGrading
import com.example.formulamaster.domain.ClozeSkeletonBuilder
import com.example.formulamaster.domain.DerivationSelfAssessment
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.model.ClozeItem
import com.example.formulamaster.domain.model.DerivationStep
import com.example.formulamaster.ui.component.LatexChipsView
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.viewmodel.C6Option
import com.example.formulamaster.ui.viewmodel.RouterReviewViewModel
import kotlinx.coroutines.delay

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
                    val title = uiState.currentFormula?.title.orEmpty()
                    val latex = uiState.currentFormula?.latexCode.orEmpty()
                    when (action.cardType) {
                        CardType.C1_Recognition -> C1RecognitionPane(
                            action = action,
                            formulaTitle = title,
                            formulaLatex = latex,
                            preconditions = uiState.currentPreconditions,
                            purpose = uiState.currentFormula?.purpose.orEmpty(),
                            mnemonic = uiState.currentFormula?.mnemonic,
                            isReinforced = isReinforced,
                            onRate = viewModel::rate,
                            modifier = Modifier.fillMaxSize()
                        )

                        // C2 无可挖空（数据缺失）时回落通用骨架，避免空卡卡住
                        CardType.C2_Cloze -> if (uiState.currentClozeBlanks.isNotEmpty()) {
                            C2ClozePane(
                                action = action,
                                formulaTitle = title,
                                formulaLatex = latex,
                                blanks = uiState.currentClozeBlanks,
                                isReinforced = isReinforced,
                                onRate = viewModel::rate,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ShowCardPane(action, title, latex, isReinforced, viewModel::rate, Modifier.fillMaxSize())
                        }

                        CardType.C3_Precondition -> C3PreconditionPane(
                            action = action,
                            formulaTitle = title,
                            formulaLatex = latex,
                            preconditions = uiState.currentPreconditions,
                            purpose = uiState.currentFormula?.purpose.orEmpty(),
                            isReinforced = isReinforced,
                            onRate = viewModel::rate,
                            modifier = Modifier.fillMaxSize()
                        )

                        // C4 无推导链（数据缺失）时回落通用骨架，避免空卡卡住
                        CardType.C4_Derivation -> if (uiState.currentDerivationSteps.isNotEmpty()) {
                            C4DerivationPane(
                                action = action,
                                formulaTitle = title,
                                formulaLatex = latex,
                                steps = uiState.currentDerivationSteps,
                                isReinforced = isReinforced,
                                onRate = viewModel::rate,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ShowCardPane(action, title, latex, isReinforced, viewModel::rate, Modifier.fillMaxSize())
                        }

                        // C6 题面 / 候选池数据不足时回落通用骨架
                        CardType.C6_TypicalProblem -> if (
                            uiState.currentC6Problem.isNotBlank() && uiState.currentC6Options.size >= 2
                        ) {
                            C6TypicalProblemPane(
                                action = action,
                                formulaTitle = title,
                                problem = uiState.currentC6Problem,
                                options = uiState.currentC6Options,
                                correctIds = uiState.currentC6CorrectIds,
                                isReinforced = isReinforced,
                                onRate = viewModel::rate,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ShowCardPane(action, title, latex, isReinforced, viewModel::rate, Modifier.fillMaxSize())
                        }

                        else -> ShowCardPane(action, title, latex, isReinforced, viewModel::rate, Modifier.fillMaxSize())
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

// ── C2 加权 cloze 卡专属面板（Task 2.3） ───────────────────────────────────────

/**
 * C2 加权 cloze 卡：每个挖空一组 chip 单选 → 提交 → 系统自动判对错 → 映射评分。
 *
 * 评分由 [ClozeGrading] 决定（全对→4 / 有错→1），用户不自评。提交后展示逐空 ✓/✗ +
 * 错空的正确答案，再点「继续」把 [ClozeGrading.Result.rating] 交给路由器。
 *
 * 选项顺序按卡片 key 稳定乱序，避免正确项总在固定位置。
 */
@Composable
private fun C2ClozePane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    formulaLatex: String,
    blanks: List<ClozeItem>,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shuffledOptions = remember(action.formulaId, action.cardType) {
        blanks.associate { it.index to it.options.shuffled() }
    }
    val selections = remember(action.formulaId, action.cardType) { mutableStateMapOf<Int, String>() }
    var result by remember(action.formulaId, action.cardType) { mutableStateOf<ClozeGrading.Result?>(null) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CardHeaderChips(action.cardType, isReinforced, action.isReinforcementRetest)
        Spacer(Modifier.height(12.dp))
        Text(formulaTitle.ifEmpty { "（公式标题缺失）" }, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "为每个挖空选择正确的部件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // ── 公式骨架（带编号方框的洞，选中 chip 实时填入；提交后整框按对错着色）────────
        // 未提交：读 selections 快照实时填入预览；提交后：buildGraded 填正确答案 + colorbox 上色
        if (formulaLatex.isNotBlank()) {
            val gradedResult = result
            val skeleton = if (gradedResult != null) {
                ClozeSkeletonBuilder.buildGraded(formulaLatex, blanks, gradedResult.perBlankCorrect)
            } else {
                ClozeSkeletonBuilder.build(formulaLatex, blanks, selections.toMap())
            }
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                MathFormulaView(
                    latex = skeleton,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        val submitted = result != null
        blanks.forEachIndexed { i, blank ->
            val opts = shuffledOptions[blank.index].orEmpty()
            val correctThis = result?.perBlankCorrect?.get(blank.index)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("空 ${i + 1}", style = MaterialTheme.typography.labelLarge)
                if (submitted && correctThis != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (correctThis) "✓ 正确" else "✗ 错误",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (correctThis) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (!submitted) {
                LatexChipsView(
                    items = opts,
                    selectable = true,
                    singleSelect = true,
                    onSelectionChanged = { set ->
                        val idx = set.firstOrNull()
                        if (idx != null && idx in opts.indices) selections[blank.index] = opts[idx]
                        else selections.remove(blank.index)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (correctThis == false) {
                // 正确答案已在顶部骨架红框露出；这里显示用户选错的部件，方便上下对照
                Text(
                    text = "你选的（错）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                LatexChipsView(
                    items = listOf(selections[blank.index].orEmpty()),
                    selectable = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!submitted) {
            val allFilled = selections.size == blanks.size
            Button(
                onClick = { result = ClozeGrading.grade(blanks, selections.toMap()) },
                enabled = allFilled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (allFilled) "提交" else "请填完所有空（${selections.size}/${blanks.size}）")
            }
        } else {
            val r = result!!
            Surface(
                color = if (r.allCorrect) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (r.allCorrect) "全部正确 · 系统评定 4（秒出）"
                    else "有错 · 系统评定 1（不会）",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onRate(r.rating) }, modifier = Modifier.fillMaxWidth()) {
                Text("继续")
            }
        }
    }
}

// ── C3 条件先行卡专属面板（Task 2.4） ──────────────────────────────────────────

/**
 * C3 条件先行卡：先 [C3_COUNTDOWN_SECONDS] 秒强制展示「条件 + 用途」（不可作答），
 * 解锁后用户回想公式 → 「看答案」露出公式 → 1/2/3/4 自评。
 *
 * 与 C1 对称，只是以条件先行 + 加了倒计时强制阅读门。
 */
@Composable
private fun C3PreconditionPane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    formulaLatex: String,
    preconditions: List<String>,
    purpose: String,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var remaining by remember(action.formulaId, action.cardType) { mutableIntStateOf(C3_COUNTDOWN_SECONDS) }
    var revealed by rememberSaveable(action.formulaId, action.cardType) { mutableStateOf(false) }

    LaunchedEffect(action.formulaId, action.cardType) {
        remaining = C3_COUNTDOWN_SECONDS
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    val unlocked = remaining <= 0

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CardHeaderChips(action.cardType, isReinforced, action.isReinforcementRetest)
        Spacer(Modifier.height(12.dp))
        Text(formulaTitle.ifEmpty { "（公式标题缺失）" }, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                SectionLabel("适用条件")
                if (preconditions.isEmpty()) {
                    Text(
                        "（暂未标注）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    preconditions.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                RevealSectionDivider()
                SectionLabel("用途")
                Text(
                    text = purpose.ifBlank { "（暂未标注）" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (purpose.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface
                )
                if (revealed) {
                    RevealSectionDivider()
                    SectionLabel("公式")
                    MathFormulaView(
                        latex = formulaLatex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            !unlocked -> Text(
                text = "请先看条件与用途 · $remaining 秒后可作答",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            !revealed -> {
                Text(
                    "回想这个公式…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("看答案")
                }
            }

            else -> RatingRow(onRate = onRate)
        }
    }
}

private const val C3_COUNTDOWN_SECONDS = 3

// ── C4 推导卡专属面板（Task 3.1） ──────────────────────────────────────────────

/**
 * C4 推导卡：题面给「公式结论」→ 用户心里推一遍 → [C4_COUNTDOWN_SECONDS] 秒倒计时门 →
 * 解锁后「看推导」一次性露出完整推导链（每步 latex + note）→ 三档自评。
 *
 * 三档自评（[DerivationSelfAssessment]）映射 FSRS 评分 1/2/4（用户 2026-06-05 拍板）：
 * 不会→1 / 查看了→2 / 推出来了→4。映射逻辑在纯枚举中，便于单测。
 *
 * 与 C3 对称（倒计时门 + 解锁后展开），区别在于：C3 先看条件回想公式，C4 先看结论回想推导过程。
 */
@Composable
private fun C4DerivationPane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    formulaLatex: String,
    steps: List<DerivationStep>,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var remaining by remember(action.formulaId, action.cardType) { mutableIntStateOf(C4_COUNTDOWN_SECONDS) }
    var revealed by rememberSaveable(action.formulaId, action.cardType) { mutableStateOf(false) }

    LaunchedEffect(action.formulaId, action.cardType) {
        remaining = C4_COUNTDOWN_SECONDS
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    val unlocked = remaining <= 0

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CardHeaderChips(action.cardType, isReinforced, action.isReinforcementRetest)
        Spacer(Modifier.height(12.dp))
        Text(formulaTitle.ifEmpty { "（公式标题缺失）" }, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))

        // ── 题面：目标结论（始终可见，让用户知道往哪推）────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                SectionLabel("目标结论")
                MathFormulaView(
                    latex = formulaLatex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                Text(
                    text = "在心里把这条公式推一遍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ── 推导链（解锁并点开后一次全露）────────────────────────────────────
        if (revealed) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("推导过程")
                    steps.forEachIndexed { i, step ->
                        if (i > 0) RevealSectionDivider()
                        Text(
                            text = "${i + 1}. ${step.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        if (step.latex.isNotBlank()) {
                            MathFormulaView(
                                latex = step.latex,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            !unlocked -> Text(
                text = "先自己推 · $remaining 秒后看推导",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            !revealed -> Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                Text("看推导")
            }

            else -> DerivationRatingRow(onRate = onRate)
        }
    }
}

/**
 * C4 三档自评行：不会 / 查看了 / 推出来了 → 取 [DerivationSelfAssessment.rating] 交路由器。
 */
@Composable
private fun DerivationRatingRow(onRate: (Int) -> Unit) {
    Column {
        Text(
            text = "自评",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DerivationSelfAssessment.entries.forEach { choice ->
                RatingButton(
                    label = choice.label,
                    onClick = { onRate(choice.rating) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private const val C4_COUNTDOWN_SECONDS = 5

// ── C6 题型反查卡专属面板（Task 3.2） ──────────────────────────────────────────

/**
 * C6 题型反查卡：展示一道题面 → 从**同章节公式池多选**该题该用哪条公式 → 系统判对错 → 映射评分。
 *
 * 评分由 [C6Grading] 决定（选中集恰好等于正确集→4 / 否则→1），用户不自评。提交后展示对错横幅，
 * 选错时露出正确公式（标题 + KaTeX）。选项以公式 KaTeX 渲染（用户 2026-06-05 拍板）。
 *
 * 题面是教辅改编纯文本（部分含数值答案，不透露解法），用 [Text] 渲染；选项才走 [LatexChipsView]。
 */
@Composable
private fun C6TypicalProblemPane(
    action: ReviewRouter.NextAction.ShowCard,
    formulaTitle: String,
    problem: String,
    options: List<C6Option>,
    correctIds: Set<String>,
    isReinforced: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndices = remember(action.formulaId, action.cardType) { mutableStateListOf<Int>() }
    var result by remember(action.formulaId, action.cardType) { mutableStateOf<C6Grading.Result?>(null) }
    val submitted = result != null

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CardHeaderChips(action.cardType, isReinforced, action.isReinforcementRetest)
        Spacer(Modifier.height(12.dp))
        Text(formulaTitle.ifEmpty { "（公式标题缺失）" }, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // ── 题面 ────────────────────────────────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                SectionLabel("题面")
                Text(problem, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = "这道题该用哪条公式？（可多选）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (!submitted) {
            LatexChipsView(
                items = options.map { it.latex },
                selectable = true,
                onSelectionChanged = { set ->
                    selectedIndices.clear()
                    selectedIndices.addAll(set)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            val anySelected = selectedIndices.isNotEmpty()
            Button(
                onClick = {
                    val selectedIds = selectedIndices.mapNotNull { options.getOrNull(it)?.formulaId }.toSet()
                    result = C6Grading.grade(selectedIds, correctIds)
                },
                enabled = anySelected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (anySelected) "提交" else "请至少选一条")
            }
        } else {
            val r = result!!
            Surface(
                color = if (r.allCorrect) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (r.allCorrect) "正确 · 系统评定 4" else "不对 · 系统评定 1",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            // 选错时露出正确公式
            if (!r.allCorrect) {
                Spacer(Modifier.height(12.dp))
                Text("正确公式：", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                options.filter { it.formulaId in correctIds }.forEach { opt ->
                    Text(opt.title, style = MaterialTheme.typography.bodyMedium)
                    MathFormulaView(
                        latex = opt.latex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { onRate(r.rating) }, modifier = Modifier.fillMaxWidth()) {
                Text("继续")
            }
        }
    }
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
            RatingButton(label = "4 秒出", onClick = { onRate(4) }, modifier = Modifier.weight(1f))
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
