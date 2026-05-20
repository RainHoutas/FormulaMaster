package com.example.formulamaster.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.component.TracingCanvas
import com.example.formulamaster.ui.viewmodel.FormulaLearnRitualViewModel
import com.example.formulamaster.ui.viewmodel.Step7State
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sprint 2 Task 2.5：七步学习仪式 Screen。
 *
 * 七步：
 *  1. 条件 + 用途先行（2s 倒计时）
 *  2. 拆块讲解（横滑或纵列）
 *  3. 推导链静态展示（DerivationStep）
 *  4. 临摹手写（TracingCanvas + 手动确认）
 *  5. Worked Example × 2（Sprint 2 占位）
 *  6. 最小填空预热（ClozeParser.minimalSample）
 *  7. 巩固迷你卡序列（C1+C2+C3 mini-card + 重做队列）
 *
 * UI 结构：顶部 StepIndicator（自由前后）+ HorizontalPager 一屏一步。
 * 用户在 Step 7 全 3 张 mini-card 通过后，底栏出现「结业」按钮 → 触发 [completeRitual]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaLearnRitualScreen(
    formulaId: String,
    onBack: () -> Unit,
    onFinishToMemory: () -> Unit,
    viewModel: FormulaLearnRitualViewModel = viewModel(
        factory = FormulaLearnRitualViewModel.factory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(formulaId) {
        viewModel.load(formulaId)
    }

    // 仪式完成 → 自动跳回 Memory（避免用户在结业页继续翻动）
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(800L)
            onFinishToMemory()
        }
    }

    if (uiState.isLoading || uiState.formula == null) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
        return
    }

    val formula = uiState.formula!!
    val pagerState = rememberPagerState(pageCount = { 7 })
    val scope = rememberCoroutineScope()

    // 维护"哪些步骤已访问过" → StepIndicator 用于自由前后；当前 step 和已访问 step 可点
    var maxVisitedStep by remember { mutableStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > maxVisitedStep) {
            maxVisitedStep = pagerState.currentPage
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(formula.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
                StepIndicator(
                    currentStep = pagerState.currentPage,
                    maxVisitedStep = maxVisitedStep,
                    onStepClick = { step ->
                        scope.launch { pagerState.animateScrollToPage(step) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        bottomBar = {
            RitualBottomBar(
                currentStep = pagerState.currentPage,
                step7Finished = uiState.step7.isFinished,
                onPrev = {
                    scope.launch {
                        if (pagerState.currentPage > 0) {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                onNext = {
                    scope.launch {
                        if (pagerState.currentPage < 6) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                onComplete = { viewModel.completeRitual() }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
            // 自由前后：不锁定 Pager 滑动
        ) { page ->
            when (page) {
                0 -> Step1Precondition(
                    purpose = uiState.purpose,
                    preconditions = uiState.preconditions
                )
                1 -> Step2Chunks(latex = formula.latexCode)
                2 -> Step3Derivation(steps = uiState.derivationSteps)
                3 -> Step4Tracing(latex = formula.latexCode)
                4 -> Step5WorkedExamplePlaceholder()
                5 -> Step6MinimalCloze(
                    minimalItem = uiState.minimalClozeItem,
                    fullLatex = formula.latexCode
                )
                6 -> Step7ConsolidationMini(
                    state = uiState.step7,
                    purpose = uiState.purpose,
                    preconditions = uiState.preconditions,
                    latex = formula.latexCode,
                    clozeItems = uiState.clozeItems,
                    onPass = { viewModel.step7Pass() },
                    onFail = { viewModel.step7Fail() }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 顶部 StepIndicator + 底栏 BottomBar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepIndicator(
    currentStep: Int,
    maxVisitedStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val stepLabels = listOf("条件", "拆块", "推导", "临摹", "例题", "填空", "巩固")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stepLabels.forEachIndexed { idx, label ->
            val isCurrent = idx == currentStep
            val isReachable = idx <= maxVisitedStep
            val color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isReachable -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = isReachable) { onStepClick(idx) }
                    .padding(horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 28.dp else 22.dp)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RitualBottomBar(
    currentStep: Int,
    step7Finished: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrev,
            enabled = currentStep > 0,
            modifier = Modifier.weight(1f)
        ) { Text("上一步") }

        if (currentStep == 6 && step7Finished) {
            Button(
                onClick = onComplete,
                modifier = Modifier.weight(1f)
            ) { Text("结业 · 初始化复习") }
        } else {
            FilledTonalButton(
                onClick = onNext,
                enabled = currentStep < 6,
                modifier = Modifier.weight(1f)
            ) { Text(if (currentStep == 6) "完成第 7 步后结业" else "下一步") }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 1: 条件 + 用途（2s 倒计时）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step1Precondition(
    purpose: String,
    preconditions: List<String>
) {
    var remainingSeconds by remember { mutableStateOf(2) }
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    StepScaffold(title = "Step 1 · 用途 + 条件") {
        if (remainingSeconds > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "看清条件再继续 · ${remainingSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (purpose.isNotBlank()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("用途", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(purpose, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (preconditions.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("条件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    preconditions.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 2: 拆块讲解
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step2Chunks(latex: String) {
    StepScaffold(title = "Step 2 · 拆块阅读") {
        Text(
            text = "把公式拆成几块，分别理解每一块的含义。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        MathFormulaView(
            latex = latex,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "（拆块 chunk 数据 Sprint 2 占位，待数据补完后展示分块讲解）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 3: 推导链
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step3Derivation(steps: List<com.example.formulamaster.domain.model.DerivationStep>) {
    StepScaffold(title = "Step 3 · 推导链") {
        if (steps.isEmpty()) {
            Text(
                text = "本公式暂无推导链数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 外层 StepScaffold 已是 verticalScroll，不能再嵌 LazyColumn
            // （会触发 IllegalStateException: infinity maximum height）。
            steps.forEach { step ->
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (step.latex.isNotBlank()) {
                            MathFormulaView(
                                latex = step.latex,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (step.note.isNotBlank()) {
                            Text(step.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 4: 临摹（沿用 TracingCanvas + 用户手动确认）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step4Tracing(latex: String) {
    var confirmed by remember { mutableStateOf(false) }
    StepScaffold(title = "Step 4 · 临摹手写") {
        Text(
            text = "在下方画布上跟着公式临摹一遍。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TracingCanvas(
            latexCode = latex,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { confirmed = true },
            enabled = !confirmed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (confirmed) "已确认临摹完成 ✓" else "我临摹完了")
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 5: Worked Example（Sprint 2 占位）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step5WorkedExamplePlaceholder() {
    StepScaffold(title = "Step 5 · 例题示范") {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📘", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Worked Example 模块开发中",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sprint 2 内为占位；后续补完 workedExamples 字段后会显示 2 道带完整步骤的例题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 6: 最小填空预热
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step6MinimalCloze(
    minimalItem: com.example.formulamaster.domain.model.ClozeItem?,
    fullLatex: String
) {
    var revealed by remember { mutableStateOf(false) }
    StepScaffold(title = "Step 6 · 最小填空预热") {
        if (minimalItem == null) {
            Text(
                text = "本公式暂无 clozeData 数据，跳过预热。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@StepScaffold
        }
        Text(
            text = "回忆这一处该填什么。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("挖空位置（占位符）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                MathFormulaView(
                    latex = if (revealed) minimalItem.placeholder else "\\;\\fbox{\\;?\\;}\\;",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (revealed) "隐藏答案" else "我想好了 · 看答案")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("完整公式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        MathFormulaView(
            latex = fullLatex,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Step 7: 巩固迷你卡序列（mini C1 + C2 + C3，错答入重做队列）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun Step7ConsolidationMini(
    state: Step7State,
    purpose: String,
    preconditions: List<String>,
    latex: String,
    clozeItems: List<com.example.formulamaster.domain.model.ClozeItem>,
    onPass: () -> Unit,
    onFail: () -> Unit
) {
    StepScaffold(title = "Step 7 · 巩固迷你卡") {
        if (state.isFinished) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("3 张迷你卡全部通过", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "点击底栏「结业 · 初始化复习」即可进入跨日复习。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@StepScaffold
        }

        val current = state.currentCard ?: return@StepScaffold
        Text(
            text = "本轮第 ${state.currentIndex + 1} / ${state.pendingDeck.size} 张" +
                if (state.retryDeck.isNotEmpty()) " · 待重做 ${state.retryDeck.size} 张" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        // Bug 修复：用 attemptCount 作为 key 强制每次评分后重新组装 mini-card，
        // 否则重做轮次进入同一卡型时，旧 remember 残留（C2 submitted=true 后按钮卡死）。
        key(state.attemptCount) {
            when (current) {
                CardType.C1_Recognition -> MiniC1Card(latex = latex, onPass = onPass, onFail = onFail)
                CardType.C2_Cloze -> MiniC2Card(
                    items = clozeItems,
                    preconditions = preconditions,
                    fullLatex = latex,
                    onPass = onPass,
                    onFail = onFail
                )
                CardType.C3_Precondition -> MiniC3Card(
                    purpose = purpose,
                    preconditions = preconditions,
                    latex = latex,
                    onPass = onPass,
                    onFail = onFail
                )
                else -> {
                    // C4/C5/C6 在 Sprint 2 暂不参与 mini-card 序列
                    Text("此卡型 mini 形态暂未实装，自动通过。", style = MaterialTheme.typography.bodySmall)
                    LaunchedEffect(current) { onPass() }
                }
            }
        }
    }
}

@Composable
private fun MiniC1Card(latex: String, onPass: () -> Unit, onFail: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mini · 识别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("在心里默默写出这条公式", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            if (revealed) {
                MathFormulaView(
                    latex = latex,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFail, modifier = Modifier.weight(1f)) { Text("忘了") }
                    Button(onClick = onPass, modifier = Modifier.weight(1f)) { Text("记得") }
                }
            } else {
                FilledTonalButton(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("看答案") }
            }
        }
    }
}

@Composable
private fun MiniC2Card(
    items: List<com.example.formulamaster.domain.model.ClozeItem>,
    preconditions: List<String>,
    fullLatex: String,
    onPass: () -> Unit,
    onFail: () -> Unit
) {
    val item = remember(items) {
        com.example.formulamaster.domain.ClozeParser.minimalSample(items, preconditions)
    }
    var selected by remember(item) { mutableStateOf<String?>(null) }
    var submitted by remember(item) { mutableStateOf(false) }

    if (item == null) {
        Text("本公式无 cloze 数据，自动通过。", style = MaterialTheme.typography.bodySmall)
        LaunchedEffect(Unit) { onPass() }
        return
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mini · 填空", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            MathFormulaView(
                latex = if (submitted) fullLatex else "\\;\\fbox{\\;?\\;}\\;",
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            Spacer(Modifier.height(12.dp))
            val options = if (item.options.isEmpty()) listOf(item.placeholder) else item.options
            options.forEach { opt ->
                val isSel = selected == opt
                OutlinedButton(
                    onClick = { if (!submitted) selected = opt },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(opt, modifier = Modifier.padding(vertical = 2.dp))
                    if (isSel) Text(" ✓")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    submitted = true
                    if (selected == item.placeholder) onPass() else onFail()
                },
                enabled = selected != null && !submitted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (submitted) "已提交" else "提交")
            }
        }
    }
}

@Composable
private fun MiniC3Card(
    purpose: String,
    preconditions: List<String>,
    latex: String,
    onPass: () -> Unit,
    onFail: () -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(2) }
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L); remainingSeconds--
        }
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mini · 条件先行", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (purpose.isNotBlank()) {
                Text("用途：$purpose", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (preconditions.isNotEmpty()) {
                Text("条件：", style = MaterialTheme.typography.labelMedium)
                preconditions.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            MathFormulaView(
                latex = latex,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (remainingSeconds > 0) {
                Text(
                    text = "请先认真看条件 · ${remainingSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFail, modifier = Modifier.weight(1f)) { Text("再看看") }
                    Button(onClick = onPass, modifier = Modifier.weight(1f)) { Text("理解了") }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 步骤通用 Scaffold（顶部标题 + 可滚动内容区）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepScaffold(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        content()
        Spacer(Modifier.height(48.dp))
    }
}
