package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.ui.component.MathFormulaView
import com.example.formulamaster.ui.component.TracingCanvas
import com.example.formulamaster.ui.viewmodel.MemoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaDetailScreen(
    formulaId: String,
    onBack: () -> Unit,
    viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    val item = if (uiState.isLoading) null
    else uiState.formulas.find { it.formula.formulaId == formulaId }

    if (item == null) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
        return
    }

    val formula = item.formula
    val isActivated = item.isActivated

    Scaffold(
        // 不重复消费外层 Scaffold 已处理的 WindowInsets
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(formula.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Edge-to-Edge：bottomBar 需自带 navigationBars inset padding，
            // 否则在手势导航栏机型上底部按钮会被系统栏遮挡。
            FilledTonalButton(
                onClick = {
                    if (!isActivated) {
                        viewModel.activateFormula(formula.formulaId)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }
                },
                enabled = !isActivated,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(if (isActivated) "复习中" else "标记为开始学习")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Task 5.4：顽固难点（Leech）顶部警示横幅 ──────────────────────
            if (item.lapses >= 4) {
                LeechBanner(
                    lapses = item.lapses,
                    tags = formula.tags,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── 完整公式渲染 ─────────────────────────────────────────────────
            MathFormulaView(
                latex = formula.latexCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── 临摹区域 ─────────────────────────────────────────────────────
            Text(
                text = "临摹练习",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TracingCanvas(
                latexCode = formula.latexCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

// ── Task 5.4：顽固难点警示横幅 ───────────────────────────────────────────────
//
// lapses ≥ 4 的公式在列表（MemoryScreen 已高亮）、详情、复习均需视觉区分。
// 此处展示错误次数 + 应用场景标签（formula.tags），提示用户重点攻关方向。

@Composable
private fun LeechBanner(
    lapses: Int,
    tags: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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
