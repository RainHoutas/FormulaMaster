package com.example.formulamaster.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.RecognizerRegistry
import com.example.formulamaster.domain.RecognizerSettings
import com.example.formulamaster.domain.RecognizerType
import com.example.formulamaster.ui.viewmodel.ExportResult
import com.example.formulamaster.ui.viewmodel.SettingsViewModel
import com.example.formulamaster.ui.viewmodel.TestConnectionStatus
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

/**
 * Sprint 1 Task 1.7 — 设置页
 *
 * ## 三大区块
 * 1. **识别器配置**：A1 Mathpix（appId+appKey）+ A2 SimpleTex（token），各带「测试连接」
 * 2. **识别档位绑定**：Light（300ms 防抖）/ Deep（1.5s 防抖）下拉绑定，仅列出已可用识别器
 * 3. **重置区**：清空所有配置（危险操作，二次确认）
 *
 * ## 设计要点
 * - Key 输入框默认密码模式（密文显示），右侧眼睛图标切换可见
 * - 配置完整的识别器卡片右上角显示 ✓ 角标，缺配置显示「未配置」灰色徽章
 * - 「测试连接」按钮发起最小请求验证 Key 鉴权，结果实时反馈
 * - 切换 Light/Deep 绑定即时生效（DataStore Flow 推送 → ViewModel → UI）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(LocalContext.current)
    )
) {
    val settings by viewModel.settings.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val lastCompletionAtMs by viewModel.lastCompletionAtMs.collectAsState()
    val feedbackCount by viewModel.feedbackCount.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 测试连接冷却倒计时：以"最近完成时间戳"为 key，每次新完成触发一轮完整的 5s ticker
    // 关键：状态条 4 秒自动消失（testStatus 变化）不会影响这个 LaunchedEffect，
    // 因为 lastCompletionAtMs 只在新测试完成时变化，不会因 dismiss 抖动
    var tickMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastCompletionAtMs) {
        if (lastCompletionAtMs == 0L) return@LaunchedEffect
        // 冷却期 5s，500ms 一个 tick → 12 次（6s）覆盖到完整冷却结束 + 一点缓冲
        repeat(12) {
            tickMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(500L)
        }
        tickMs = System.currentTimeMillis()  // 最终一次 tick 强制冷却归零的 UI 重组
    }

    // 派生：每个识别器当前的冷却剩余秒数（tickMs 变化触发 remember 重算）
    val mathpixCooldown = remember(tickMs) {
        viewModel.cooldownSecondsRemaining(RecognizerType.A1_Mathpix)
    }
    val simpleTexCooldown = remember(tickMs) {
        viewModel.cooldownSecondsRemaining(RecognizerType.A2_SimpleTex_Standard)
    }

    // Sprint 1 Task 1.9：SAF 导出 JSON Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportFeedbackJson(uri)
    }

    // 监听导出结果，弹 Snackbar
    LaunchedEffect(exportResult) {
        when (val r = exportResult) {
            null -> {}
            is ExportResult.NoSamples -> {
                snackbarHostState.showSnackbar("还没有反馈样本")
                viewModel.clearExportResult()
            }
            is ExportResult.Success -> {
                snackbarHostState.showSnackbar("导出成功（${r.count} 条）")
                viewModel.clearExportResult()
            }
            is ExportResult.Failed -> {
                snackbarHostState.showSnackbar("导出失败：${r.reason}")
                viewModel.clearExportResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SectionHeader("识别器配置")

            // SimpleTex 放在前面：推荐路径，国内直连真免费
            SimpleTexCard(
                settings = settings,
                // 测试连接以 Standard 端点为准（Standard 和 Turbo 共享 token，
                // Standard 通过即代表整套 SimpleTex 可用）
                testStatus = testStatus[RecognizerType.A2_SimpleTex_Standard],
                cooldownSeconds = simpleTexCooldown,
                onSave = viewModel::setSimpleTexToken,
                onTest = { viewModel.testConnection(RecognizerType.A2_SimpleTex_Standard) },
                onDismissStatus = { viewModel.clearTestStatus(RecognizerType.A2_SimpleTex_Standard) }
            )

            Spacer(Modifier.height(12.dp))

            // Mathpix 放在后面：付费 API（$19.99 激活费 + 信用卡）
            MathpixCard(
                settings = settings,
                testStatus = testStatus[RecognizerType.A1_Mathpix],
                cooldownSeconds = mathpixCooldown,
                onSave = viewModel::setMathpixCredentials,
                onTest = { viewModel.testConnection(RecognizerType.A1_Mathpix) },
                onDismissStatus = { viewModel.clearTestStatus(RecognizerType.A1_Mathpix) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionHeader("识别档位绑定")

            BindingDropdown(
                label = "实时预览（300ms 防抖）",
                description = "停笔后立即显示候选 LaTeX，要求识别速度",
                current = settings.lightRecognizerId,
                available = RecognizerRegistry.availableTypes(settings),
                onSelect = viewModel::setLightRecognizer
            )

            Spacer(Modifier.height(12.dp))

            BindingDropdown(
                label = "精确识别（1.5s 防抖）",
                description = "用于最终候选，要求识别准确",
                current = settings.deepRecognizerId,
                available = RecognizerRegistry.availableTypes(settings),
                onSelect = viewModel::setDeepRecognizer
            )

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Sprint 1 Task 1.9：识别反馈样本管理
            FeedbackSection(
                count = feedbackCount,
                onExport = {
                    val ts = java.text.SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    exportLauncher.launch("formulamaster_ocr_feedback_$ts.json")
                },
                onClear = viewModel::clearFeedback
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ResetSection(onReset = viewModel::clearAll)

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 反馈样本管理区（Sprint 1 Task 1.9） ─────────────────────────────────────

@Composable
private fun FeedbackSection(
    count: Int,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    SectionHeader("识别反馈")
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "已收集 $count 条反馈样本",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "在严测页点「都不对」可记录识别失败的笔画 + 候选 + 你写下的正确 LaTeX。" +
                       "导出 JSON 后可作为未来端侧训练 / 误识别复盘的数据源。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onExport,
                    enabled = count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 JSON")
                }
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    enabled = count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空反馈样本？") },
            text = {
                Text("将删除所有已收集的 $count 条反馈样本。建议先导出 JSON 备份。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClearConfirm = false
                    }
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ── 识别器卡片 ────────────────────────────────────────────────────────────

@Composable
private fun MathpixCard(
    settings: RecognizerSettings,
    testStatus: TestConnectionStatus?,
    cooldownSeconds: Int,
    onSave: (appId: String, appKey: String) -> Unit,
    onTest: () -> Unit,
    onDismissStatus: () -> Unit
) {
    var appId by remember(settings.mathpixAppId) { mutableStateOf(settings.mathpixAppId) }
    var appKey by remember(settings.mathpixAppKey) { mutableStateOf(settings.mathpixAppKey) }
    var appKeyVisible by remember { mutableStateOf(false) }

    val isConfigured = RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, settings)
    val isDirty = appId != settings.mathpixAppId || appKey != settings.mathpixAppKey

    RecognizerConfigCard(
        title = "Mathpix Snip",
        subtitle = "海外 API · 准确率最强 · ⚠️ 付费（$19.99 激活费 + 信用卡）",
        isConfigured = isConfigured,
        testStatus = testStatus,
        cooldownSeconds = cooldownSeconds,
        canTest = isConfigured && !isDirty,
        onTest = onTest,
        onDismissStatus = onDismissStatus
    ) {
        OutlinedTextField(
            value = appId,
            onValueChange = { appId = it },
            label = { Text("App ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = appKey,
            onValueChange = { appKey = it },
            label = { Text("App Key") },
            singleLine = true,
            visualTransformation = if (appKeyVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { appKeyVisible = !appKeyVisible }) {
                    Text(if (appKeyVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { onSave(appId.trim(), appKey.trim()) },
            enabled = isDirty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConfigured && !isDirty) "已保存" else "保存")
        }
    }
}

@Composable
private fun SimpleTexCard(
    settings: RecognizerSettings,
    testStatus: TestConnectionStatus?,
    cooldownSeconds: Int,
    onSave: (token: String) -> Unit,
    onTest: () -> Unit,
    onDismissStatus: () -> Unit
) {
    var token by remember(settings.simpleTexToken) { mutableStateOf(settings.simpleTexToken) }
    var tokenVisible by remember { mutableStateOf(false) }

    // Standard 和 Turbo 共享 token，配置即代表两个都可用
    val isConfigured = RecognizerRegistry.isAvailable(RecognizerType.A2_SimpleTex_Standard, settings)
    val isDirty = token != settings.simpleTexToken

    RecognizerConfigCard(
        title = "SimpleTex",
        subtitle = "国内 API · 直连免代理 · 配置一次同时启用 Turbo（2000/天）和 Standard（500/天）",
        isConfigured = isConfigured,
        testStatus = testStatus,
        cooldownSeconds = cooldownSeconds,
        canTest = isConfigured && !isDirty,
        onTest = onTest,
        onDismissStatus = onDismissStatus
    ) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("UAT Token") },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { tokenVisible = !tokenVisible }) {
                    Text(if (tokenVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { onSave(token.trim()) },
            enabled = isDirty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConfigured && !isDirty) "已保存" else "保存")
        }
    }
}

/**
 * 通用识别器配置卡片骨架。
 * 子类（MathpixCard / SimpleTexCard）通过 [content] 注入具体输入字段。
 */
@Composable
private fun RecognizerConfigCard(
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    testStatus: TestConnectionStatus?,
    cooldownSeconds: Int,
    canTest: Boolean,
    onTest: () -> Unit,
    onDismissStatus: () -> Unit,
    content: @Composable () -> Unit
) {
    // 状态变化后短暂高亮，再淡出（实际淡出由 AssistChip 自身样式承担）
    LaunchedEffect(testStatus) {
        if (testStatus is TestConnectionStatus.Success ||
            testStatus is TestConnectionStatus.Failed) {
            // 必须 ≥ TEST_COOLDOWN_MS（5s），否则状态先于冷却消失会让外层 tick LaunchedEffect
            // 提前重启后停掉 tickMs，导致按钮卡在剩余秒数（曾踩坑）
            kotlinx.coroutines.delay(SettingsViewModel.TEST_COOLDOWN_MS)
            onDismissStatus()
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfiguredBadge(isConfigured)
            }

            Spacer(Modifier.height(12.dp))
            content()

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TestStatusChip(testStatus)
                Spacer(Modifier.weight(1f))
                val isTesting = testStatus is TestConnectionStatus.Testing
                val inCooldown = cooldownSeconds > 0
                val buttonText = when {
                    isTesting -> "测试中…"
                    inCooldown -> "${cooldownSeconds}s 后可重试"
                    else -> "测试连接"
                }
                OutlinedButton(
                    onClick = onTest,
                    enabled = canTest && !isTesting && !inCooldown
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun ConfiguredBadge(isConfigured: Boolean) {
    if (isConfigured) {
        AssistChip(
            onClick = {},
            label = { Text("已配置") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            enabled = false,
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        )
    } else {
        AssistChip(
            onClick = {},
            label = { Text("未配置") },
            enabled = false,
        )
    }
}

@Composable
private fun TestStatusChip(status: TestConnectionStatus?) {
    when (status) {
        null -> {}
        is TestConnectionStatus.NotConfigured -> Text(
            "请先填入 Key",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is TestConnectionStatus.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text("测试中…", style = MaterialTheme.typography.bodySmall)
        }
        is TestConnectionStatus.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "连接正常",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        is TestConnectionStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                status.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ── 档位绑定下拉 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BindingDropdown(
    label: String,
    description: String,
    current: RecognizerType?,
    available: List<RecognizerType>,
    onSelect: (RecognizerType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = current?.displayName ?: "未绑定"
    val noOptions = available.isEmpty()

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (!noOptions) expanded = it }
        ) {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                enabled = !noOptions,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("未绑定") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                available.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(type.displayName)
                                Text(
                                    type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelect(type)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (noOptions) "请先在上方配置至少一个识别器" else description,
            style = MaterialTheme.typography.bodySmall,
            color = if (noOptions) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 重置区 ────────────────────────────────────────────────────────────────

@Composable
private fun ResetSection(onReset: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showConfirm = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("重置所有配置", color = MaterialTheme.colorScheme.error)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("重置所有识别器配置？") },
            text = {
                Text("将清除所有 API Key 和 Light/Deep 绑定。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showConfirm = false
                    }
                ) {
                    Text("确认重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ── 通用 ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Box(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
