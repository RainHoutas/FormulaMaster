package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.model.FormulaWithState
import com.example.formulamaster.ui.viewmodel.MemoryViewModel

@Composable
fun MemoryScreen(
    onFormulaClick: (String, Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start  = contentPadding.calculateStartPadding(LayoutDirection.Ltr) + 16.dp,
            end    = contentPadding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp,
            top    = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(uiState.formulas, key = { it.formula.formulaId }) { item ->
            FormulaCard(
                item = item,
                onClick = { onFormulaClick(item.formula.formulaId, item.isActivated) }
            )
        }
    }
}

// ── 公式卡片 ──────────────────────────────────────────────────────────────────

@Composable
private fun FormulaCard(
    item: FormulaWithState,
    onClick: () -> Unit
) {
    val isLeech = item.lapses >= 4

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // 顽固节点覆盖背景色；正常卡片使用 ElevatedCard 自带的 tonal 提亮
        colors = if (isLeech)
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        else
            CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.formula.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectChip(text = item.formula.subject)
                if (item.isActivated) {
                    LearningStateChip(learningState = item.learningState)
                }
            }
        }
    }
}

@Composable
private fun SubjectChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun LearningStateChip(learningState: Int) {
    val (label, color) = when (learningState) {
        1 -> "学习中" to MaterialTheme.colorScheme.primaryContainer
        2 -> "复习中" to MaterialTheme.colorScheme.tertiaryContainer
        3 -> "已掌握" to MaterialTheme.colorScheme.secondaryContainer
        else -> return
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
