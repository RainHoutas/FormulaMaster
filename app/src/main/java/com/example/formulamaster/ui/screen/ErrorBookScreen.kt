package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import com.example.formulamaster.domain.FormulaIndex
import com.example.formulamaster.ui.viewmodel.ErrorBookUiState
import com.example.formulamaster.ui.viewmodel.ErrorBookViewModel
import com.example.formulamaster.ui.viewmodel.ErrorFormState
import com.example.formulamaster.ui.viewmodel.ErrorReportRow

/**
 * 学习流程重构 Sprint 3 Task 3.3：错题本二级页（Memory FAB → 此页）。
 *
 * 两态：历史列表（[ErrorBookUiState.Mode.List]）↔ 新增表单（[ErrorBookUiState.Mode.Form]）。
 * 全程 chip / 数字键盘，零自由文字（RFC §3.6）。
 *
 * @param onNavigateToLearn 公式池点未学公式 → 跳七步学习仪式（表单草稿随 VM 存活）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorBookScreen(
    onBack: () -> Unit,
    onNavigateToLearn: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ErrorBookViewModel = viewModel(factory = ErrorBookViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val isForm = uiState.mode == ErrorBookUiState.Mode.Form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isForm) "新增错题" else "错题本") },
                navigationIcon = {
                    IconButton(onClick = { if (isForm) viewModel.closeForm() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isForm) {
                FloatingActionButton(onClick = viewModel::openForm) {
                    Icon(Icons.Filled.Add, contentDescription = "新增错题")
                }
            }
        }
    ) { innerPadding ->
        val merged = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        )
        if (isForm) {
            ErrorReportFormContent(
                form = uiState.form,
                index = uiState.formulaIndex,
                sourceTypeOptions = uiState.sourceTypeOptions,
                onSubject = viewModel::setSubject,
                onChapter = viewModel::setChapter,
                onSourceType = viewModel::setSourceType,
                onSourceTag = viewModel::setSourceTag,
                onToggleFormula = viewModel::toggleFormula,
                onToggleShowAll = viewModel::toggleShowAllSubjects,
                onNavigateToLearn = onNavigateToLearn,
                onSubmit = viewModel::submit,
                contentPadding = merged
            )
        } else {
            ErrorBookListContent(
                rows = uiState.rows,
                onDelete = viewModel::requestDelete,
                contentPadding = merged
            )
        }
    }

    // 删除确认对话框（策略 = 每次询问 时弹出）
    uiState.pendingDelete?.let { report ->
        DeleteReportDialog(
            report = report,
            onConfirm = { restore, remember -> viewModel.confirmDelete(report, restore, remember) },
            onDismiss = viewModel::cancelDelete
        )
    }
}

// ── 列表态 ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBookListContent(
    rows: List<ErrorReportRow>,
    onDelete: (ErrorReportEntity) -> Unit,
    contentPadding: PaddingValues
) {
    if (rows.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("还没有错题记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "点右下角 + 记录一道错题，选出用错的公式，系统会把它们提前排进复习。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 16.dp, end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows, key = { it.report.id }) { row ->
            ErrorReportCard(row = row, onDelete = { onDelete(row.report) })
        }
    }
}

@Composable
private fun ErrorReportCard(row: ErrorReportRow, onDelete: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${row.report.sourceType} ${row.report.sourceTag}".trim(),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${row.report.subject} · ${row.report.chapter} · ${row.formulaCount} 条公式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.formulaTitles.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        row.formulaTitles.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── 表单态 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorReportFormContent(
    form: ErrorFormState,
    index: FormulaIndex,
    sourceTypeOptions: List<String>,
    onSubject: (String) -> Unit,
    onChapter: (String) -> Unit,
    onSourceType: (String) -> Unit,
    onSourceTag: (String) -> Unit,
    onToggleFormula: (String) -> Unit,
    onToggleShowAll: () -> Unit,
    onNavigateToLearn: (String) -> Unit,
    onSubmit: () -> Unit,
    contentPadding: PaddingValues
) {
    // 点未学公式 → 确认「去学」对话框
    var pendingLearn by remember { mutableStateOf<FormulaIndex.Entry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ① subject
        FormSection("科目") {
            ChipRow(
                options = index.subjectNames,
                selected = form.subject,
                onSelect = onSubject
            )
        }
        // ② chapter（subject 联动）
        if (form.subject != null) {
            FormSection("章节") {
                ChipRow(
                    options = index.chaptersOf(form.subject),
                    selected = form.chapter,
                    onSelect = onChapter
                )
            }
        }
        // ③ sourceType
        FormSection("来源") {
            ChipRow(
                options = sourceTypeOptions,
                selected = form.sourceType,
                onSelect = onSourceType
            )
        }
        // ④ sourceTag 数字键盘
        FormSection("编号（如 2024-18）") {
            OutlinedTextField(
                value = form.sourceTag,
                onValueChange = onSourceTag,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("数字 / 连字符") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        // ⑤ 公式多选池
        FormSection("用错的公式（可多选，未学的灰显）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = form.showAllSubjects,
                    onClick = onToggleShowAll,
                    label = { Text("显示全部科目") }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "已选 ${form.selectedFormulaIds.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            FormulaPool(
                index = index,
                selectedSubject = form.subject,
                showAll = form.showAllSubjects,
                selectedIds = form.selectedFormulaIds,
                onToggleLearned = onToggleFormula,
                onTapUnlearned = { pendingLearn = it }
            )
        }
        // 提交
        FilledTonalButton(
            onClick = onSubmit,
            enabled = form.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("记录错题并排进复习")
        }
    }

    pendingLearn?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingLearn = null },
            title = { Text("这条还没学") },
            text = { Text("「${entry.formula.title}」还没学过，先去学一遍？回来后你的填写会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = entry.formula.formulaId
                    pendingLearn = null
                    onNavigateToLearn(id)
                }) { Text("去学") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLearn = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 公式多选池：按 (subject→)chapter 分组渲染，已学可选、未学灰显。
 *
 * 过滤规则：[showAll] → 全部科目；否则 [selectedSubject] 非空 → 该科目；
 * 都不满足（没选科目又没开全部）→ 提示先选科目，让「显示全部科目」开关始终有明确意义。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaPool(
    index: FormulaIndex,
    selectedSubject: String?,
    showAll: Boolean,
    selectedIds: Set<String>,
    onToggleLearned: (String) -> Unit,
    onTapUnlearned: (FormulaIndex.Entry) -> Unit
) {
    val subjects = when {
        showAll -> index.subjects
        selectedSubject != null -> index.subjects.filter { it.subject == selectedSubject }
        else -> emptyList()
    }
    if (subjects.isEmpty() || subjects.all { it.chapters.isEmpty() }) {
        Text(
            if (selectedSubject == null && !showAll)
                "先选上面的科目，或打开「显示全部科目」。"
            else "这个科目下暂时没有可选公式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        subjects.forEach { subjectGroup ->
            subjectGroup.chapters.forEach { chapterGroup ->
                val label = if (showAll) {
                    "${subjectGroup.subject} · ${chapterGroup.chapter}"
                } else {
                    chapterGroup.chapter
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chapterGroup.entries.forEach { entry ->
                        val id = entry.formula.formulaId
                        // 未学也保持 enabled（否则 disabled chip 收不到点击）；
                        // 靠 label 灰化 + 「未学」后缀区分，点击路由到「去学」确认。
                        FilterChip(
                            selected = entry.isLearned && id in selectedIds,
                            onClick = {
                                if (entry.isLearned) onToggleLearned(id) else onTapUnlearned(entry)
                            },
                            label = {
                                Text(
                                    if (entry.isLearned) entry.formula.title
                                    else "${entry.formula.title}（未学）",
                                    color = if (entry.isLearned) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── 删除对话框 ─────────────────────────────────────────────────────────────────

@Composable
private fun DeleteReportDialog(
    report: ErrorReportEntity,
    onConfirm: (restore: Boolean, remember: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条错题？") },
        text = {
            Column {
                Text("同时恢复这些公式的复习计划吗？「恢复计划」会把录入后没复习过的部分还原到录入前。")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text("以后都这样，不再询问", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true, rememberChoice) }) { Text("恢复计划") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false, rememberChoice) }) { Text("仅删记录") }
        }
    )
}

// ── 小组件 ─────────────────────────────────────────────────────────────────────

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}
