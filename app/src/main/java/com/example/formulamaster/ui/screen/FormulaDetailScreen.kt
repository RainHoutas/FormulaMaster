package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.DerivationStepParser
import com.example.formulamaster.domain.model.DerivationStep
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.viewmodel.MemoryViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sprint 2 Task 2.5：FormulaDetail 改造为"信息展示页"。
 *
 * 此屏仅对**已激活**公式可达（Memory Tab 点击时按 isActivated 路由）；
 * 未激活公式直接进 [FormulaLearnRitualScreen]，不再经过本屏。
 *
 * 内容（只读 + 快速查询）：
 *  - 顶部完整公式渲染
 *  - 用途 / 条件 / 推导链（可滚动）
 *  - 6 子卡 stability / lapses / 下次复习时间
 *  - 底部「重新学习仪式」次要入口（万一忘干净了）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaDetailScreen(
    formulaId: String,
    onBack: () -> Unit,
    onStartRitual: () -> Unit = {},
    viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val item = if (uiState.isLoading) null
    else uiState.formulas.find { it.formula.formulaId == formulaId }

    if (item == null) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
        )
        return
    }

    val formula = item.formula

    // 直接订阅子卡 Flow（不进 ViewModel，因为只在本屏读，且 MemoryVM 暂不暴露子卡）
    val subCardDao = remember(context) { AppContainer.appDatabase(context).subCardStateDao() }
    val subCards by produceState<List<SubCardStateEntity>>(
        initialValue = emptyList(),
        key1 = formulaId
    ) {
        subCardDao.observeByFormulaId(formulaId).collect { value = it }
    }

    // 解析 JSON 字段（每次重组只做一次）
    val purpose = formula.purpose
    val preconditions = remember(formula.preconditions) {
        runCatching {
            Gson().fromJson<List<String>>(
                formula.preconditions,
                object : TypeToken<List<String>>() {}.type
            ) ?: emptyList()
        }.getOrDefault(emptyList())
    }
    val derivationSteps = remember(formula.derivationSteps) {
        DerivationStepParser.parse(formula.derivationSteps)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(formula.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            OutlinedButton(
                onClick = onStartRitual,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("重新学习仪式（重做七步）")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── 顽固难点提示 ─────────────────────────────────────────────────
            if (item.lapses >= 4) {
                LeechBanner(item.lapses, formula.tags)
                Spacer(Modifier.height(12.dp))
            }

            // ── 公式渲染 ─────────────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                MathFormulaView(
                    latex = formula.latexCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── 用途 + 条件 ──────────────────────────────────────────────────
            if (purpose.isNotBlank()) {
                SectionCard(title = "用途") {
                    Text(purpose, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (preconditions.isNotEmpty()) {
                SectionCard(title = "适用条件") {
                    preconditions.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── 推导链 ───────────────────────────────────────────────────────
            if (derivationSteps.isNotEmpty()) {
                SectionCard(title = "推导链") {
                    derivationSteps.forEachIndexed { idx, step ->
                        DerivationStepBlock(idx + 1, step)
                        if (idx < derivationSteps.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── 6 子卡复习状态 ───────────────────────────────────────────────
            SectionCard(title = "六维记忆状态") {
                if (subCards.isEmpty()) {
                    Text(
                        text = "暂无子卡记录（首次学习仪式结业后自动初始化）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SubCardStatusList(subCards)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── 小组件 ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun DerivationStepBlock(index: Int, step: DerivationStep) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "第 $index 步",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        if (step.latex.isNotBlank()) {
            MathFormulaView(
                latex = step.latex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(vertical = 4.dp)
            )
        }
        if (step.note.isNotBlank()) {
            Text(step.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SubCardStatusList(subCards: List<SubCardStateEntity>) {
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    val byType = subCards.associateBy { it.cardType }

    CardType.entries.forEach { ct ->
        val sub = byType[ct.code]
        SubCardStatusRow(
            label = cardTypeLabel(ct),
            sub = sub,
            zone = zone,
            fmt = fmt
        )
    }
}

@Composable
private fun SubCardStatusRow(
    label: String,
    sub: SubCardStateEntity?,
    zone: ZoneId,
    fmt: DateTimeFormatter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (sub == null) {
                Text("未初始化", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                val next = Instant.ofEpochMilli(sub.nextReviewTime).atZone(zone).format(fmt)
                Text(
                    text = "下次复习 $next",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (sub != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("S", "%.1f".format(sub.stability))
                StatChip("D", "%.1f".format(sub.difficulty))
                if (sub.lapses > 0) StatChip("错", sub.lapses.toString(), warn = true)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, warn: Boolean = false) {
    Surface(
        color = if (warn) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = if (warn) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun cardTypeLabel(ct: CardType): String = when (ct) {
    CardType.C1_Recognition    -> "C1 · 识别"
    CardType.C2_Cloze          -> "C2 · 填空"
    CardType.C3_Precondition   -> "C3 · 条件先行"
    CardType.C4_Derivation     -> "C4 · 推导"
    CardType.C5_Discrimination -> "C5 · 易混辨析"
    CardType.C6_TypicalProblem -> "C6 · 题型反查"
}

@Composable
private fun LeechBanner(lapses: Int, tags: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "⚠️ 顽固难点 · 已错 $lapses 次",
                style = MaterialTheme.typography.titleSmall
            )
            if (tags.isNotBlank()) {
                Text(
                    text = "重点关注：${tags.split(",").joinToString(" · ") { it.trim() }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
