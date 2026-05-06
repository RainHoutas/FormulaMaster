package com.example.formulamaster.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.data.AppSettings
import com.example.formulamaster.domain.InputMode
import com.example.formulamaster.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sprint 2 Task 2.5 — 首次启动引导。
 *
 * 5 页全屏 HorizontalPager：
 *  1. 欢迎页：项目自我介绍
 *  2. 考试目标日期：DatePicker，可保留默认
 *  3. 复习刷新时间：TimePicker，可保留默认
 *  4. 配置识别器：SimpleTex token 单输入（可跳过）
 *  5. 完成：致谢 + 「开始使用」
 *
 * 顶部："跳过引导" TextButton（直接落库 firstLaunchCompletedAt 并退出）。
 * 底部：上一步 / 下一步（最后一页变"开始使用"）。
 *
 * @param onCompleted 引导结束（完成或跳过）后的回调，由 AppRoot 切换到 MainScreen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(LocalContext.current)
    )
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()

    // ── 用户在前面几步输入的本地态（提交时统一落库）────────────────────────
    val zone = remember { ZoneId.systemDefault() }
    val defaultExamDate = remember { AppSettings.defaultTargetExamDate(zone) }
    var examDateMs by remember { mutableLongStateOf(defaultExamDate) }
    var refreshHour by remember { mutableIntStateOf(8) }
    var refreshMinute by remember { mutableIntStateOf(0) }
    var simpleTexToken by remember { mutableStateOf("") }
    // Sprint 3 Task 3.3：用户在"输入方式"页选择的模式（默认手写识别）
    var selectedInputMode by remember { mutableStateOf(InputMode.Handwriting) }

    fun finishAndPersist() {
        val timeChanged = refreshHour != 8 || refreshMinute != 0
        viewModel.completeAndPersist(
            targetExamDate = if (examDateMs != defaultExamDate) examDateMs else null,
            dailyRefreshHour = if (timeChanged) refreshHour else null,
            dailyRefreshMinute = if (timeChanged) refreshMinute else null,
            simpleTexToken = simpleTexToken.takeIf { it.isNotBlank() },
            inputMode = selectedInputMode
        )
        onCompleted()
    }

    fun skipAll() {
        viewModel.skip()
        onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    if (pagerState.currentPage < 5) {
                        TextButton(onClick = { skipAll() }) {
                            Text("跳过引导")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                // Sprint 3 Task 3.3：6 页；第 1 页（索引 1）为新增的"输入方式"页
                // 原 1-4 后移到 2-5；选纸笔时"下一步"从 page 3 直接跳到 page 5，跳过 page 4
                when (page) {
                    0 -> WelcomePage()
                    1 -> InputModePage(
                        selectedMode = selectedInputMode,
                        onModeSelected = { selectedInputMode = it }
                    )
                    2 -> ExamDatePage(
                        currentMs = examDateMs,
                        defaultMs = defaultExamDate,
                        zone = zone,
                        onPick = { examDateMs = it }
                    )
                    3 -> RefreshHourPage(
                        currentHour = refreshHour,
                        currentMinute = refreshMinute,
                        onPick = { h, m -> refreshHour = h; refreshMinute = m }
                    )
                    4 -> RecognizerPage(
                        token = simpleTexToken,
                        onTokenChange = { simpleTexToken = it }
                    )
                    5 -> CompletionPage()
                }
            }

            // ── 页面指示器 ─────────────────────────────────────────────────
            PageIndicator(
                count = 6,
                current = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // ── 底部按钮 ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                // Sprint 3 Task 3.3：纸笔模式下从完成页（5）回退跳过识别器页（4）
                                val prev = when {
                                    pagerState.currentPage == 5 &&
                                        selectedInputMode == InputMode.PaperPen -> 3
                                    else -> pagerState.currentPage - 1
                                }
                                pagerState.animateScrollToPage(prev)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("上一步") }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 5) {
                            coroutineScope.launch {
                                // Sprint 3 Task 3.3：纸笔模式下从刷新时间页（3）前进时跳过识别器页（4）
                                val next = when {
                                    pagerState.currentPage == 3 &&
                                        selectedInputMode == InputMode.PaperPen -> 5
                                    else -> pagerState.currentPage + 1
                                }
                                pagerState.animateScrollToPage(next)
                            }
                        } else {
                            finishAndPersist()
                        }
                    },
                    modifier = Modifier.weight(if (pagerState.currentPage > 0) 1.5f else 1f)
                ) {
                    Text(
                        text = when (pagerState.currentPage) {
                            0    -> "出发"
                            5    -> "开始使用"
                            else -> "下一步"
                        }
                    )
                }
            }
        }
    }
}

// ── 页面：输入方式（Sprint 3 Task 3.3）──────────────────────────────────────

/**
 * 引导第 2 页（页索引 1）：让用户在"手写识别"和"纸笔自评"之间二选一。
 *
 * - 默认高亮"手写识别"（[InputMode.Handwriting]）
 * - 选纸笔后显示提示"下一步会跳过识别器配置"
 * - 选择结果通过 [onModeSelected] 上抛，由 [OnboardingScreen] 持有并在完成时落库
 */
@Composable
private fun InputModePage(
    selectedMode: InputMode,
    onModeSelected: (InputMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle("你打算怎么练公式？")
        PageSubtitle("两种方式各有优势，随时可以在「设置」里切换。")
        Spacer(Modifier.height(24.dp))

        InputModeCard(
            icon = "✍️",
            title = InputMode.Handwriting.displayName,
            description = "在屏幕上手写公式，App 自动识别成 LaTeX。\n" +
                "体验流畅，无需动笔。需配置识别器（下一步引导，推荐 SimpleTex 免费）。",
            isSelected = selectedMode == InputMode.Handwriting,
            onClick = { onModeSelected(InputMode.Handwriting) }
        )

        Spacer(Modifier.height(12.dp))

        InputModeCard(
            icon = "📄",
            title = InputMode.PaperPen.displayName,
            description = "屏幕只展示公式名，你在纸上写完后对照标准答案自评对错。\n" +
                "不依赖识别器，不消耗 API 额度。需要自己判断对错。",
            isSelected = selectedMode == InputMode.PaperPen,
            onClick = { onModeSelected(InputMode.PaperPen) }
        )

        if (selectedMode == InputMode.PaperPen) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "已选纸笔自评，下一步将跳过识别器配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 单个输入方式选项卡片。
 *
 * 选中时：主色 2dp 描边 + 右侧勾号图标。
 * 未选中时：无描边，视觉退到背景。
 */
@Composable
private fun InputModeCard(
    icon: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── 页面：欢迎 ────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Formula Master",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "公式不该靠死磕，节奏才能记得牢",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "为考研学子量身打造",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                BulletLine("用 FSRS 间隔重复算法接管你的复习节奏")
                Spacer(Modifier.height(8.dp))
                BulletLine("默写 + 手写识别一气呵成，不用打字凑公式")
                Spacer(Modifier.height(8.dp))
                BulletLine("距考越近，冲刺模式越凶；离考越远，按部就班")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "接下来花 30 秒做几个简单设置，然后就能开始练了。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── 页面：考试目标日期 ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDatePage(
    currentMs: Long,
    defaultMs: Long,
    zone: ZoneId,
    onPick: (Long) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val examLocalDate = remember(currentMs) {
        Instant.ofEpochMilli(currentMs).atZone(zone).toLocalDate()
    }
    val isUsingDefault = currentMs == defaultMs
    val remainingDays = remember(currentMs) {
        (currentMs - System.currentTimeMillis()) / 86_400_000L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle("先说重头戏")
        PageSubtitle("你打算什么时候上考场？")
        Spacer(Modifier.height(20.dp))

        // ── 为什么要设置考试日期 ─────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "为什么要设置考试日期？",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                BulletLine("App 用它判断你到底在「日常学习」还是「最后冲刺」")
                Spacer(Modifier.height(6.dp))
                BulletLine("距考 ≤ 30 天会自动切到冲刺模式：高稳定公式间隔减半，已掌握公式回炉重练")
                Spacer(Modifier.height(6.dp))
                BulletLine("距考多少天会一直显示在面板上，时间感比“哎都快了”准多了")
                Spacer(Modifier.height(6.dp))
                BulletLine("没设置就只能猜，App 猜不准你只会觉得我们不靠谱")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 当前选中的日期 ────────────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = examLocalDate.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isUsingDefault) "推测日期（12 月倒数第二个周六，按近年规律），强烈建议改成自己的真实考试日期"
                           else "已自定义",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUsingDefault) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        remainingDays < 0 -> "这个日期已经过去了，记得调整。"
                        remainingDays == 0L -> "就是今天！"
                        remainingDays <= 30 -> "还剩 $remainingDays 天 — 已经进入冲刺范围。"
                        else -> "还剩 $remainingDays 天 — 慢慢来，节奏不能乱。"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isUsingDefault) "改成我自己的日期" else "再调一下") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "随时可以在「设置」里改，没什么是定死的。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (showDatePicker) {
        val todayUtcStart = remember {
            LocalDate.now(zone).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val initialUtcMs = remember(currentMs) {
            examLocalDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMs,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= todayUtcStart
                override fun isSelectableYear(year: Int): Boolean =
                    year >= LocalDate.now(zone).year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utcMs = state.selectedDateMillis
                    if (utcMs != null) {
                        val pickedLocal = Instant.ofEpochMilli(utcMs)
                            .atZone(ZoneId.of("UTC")).toLocalDate()
                        val localMidnight = pickedLocal.atStartOfDay(zone)
                            .toInstant().toEpochMilli()
                        onPick(localMidnight)
                    }
                    showDatePicker = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

// ── 页面：复习刷新时间 ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshHourPage(
    currentHour: Int,
    currentMinute: Int,
    onPick: (hour: Int, minute: Int) -> Unit
) {
    var pickerGen by remember { mutableIntStateOf(0) }
    val timeState = remember(pickerGen) {
        TimePickerState(
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true
        )
    }
    var showDialog by remember { mutableStateOf(false) }

    val displayTime = "%02d:%02d".format(currentHour, currentMinute)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle("一天从几点开始？")
        PageSubtitle("App 会按这个时间点切换“今日复习”队列。")
        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = displayTime,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (currentHour) {
                        in 5..8   -> "早起型选手 — 一觉醒来就开练。"
                        in 9..11  -> "上午稳扎稳打型。"
                        in 12..17 -> "午后开局，踩着节奏来。"
                        in 18..22 -> "夜猫子节奏。"
                        else      -> "深夜模式。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        pickerGen++
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("调整时间") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "默认 08:00，绝大多数人不用改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("设置每日刷新时间") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPick(timeState.hour, timeState.minute)
                    showDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

// ── 页面：识别器配置（SimpleTex token 简化版）─────────────────────────────

@Composable
private fun RecognizerPage(
    token: String,
    onTokenChange: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle("配一个识别器")
        PageSubtitle("默写时把你的笔迹翻译成 LaTeX，少了它就只能看公式不能写。")
        Spacer(Modifier.height(20.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SimpleTex（推荐）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "国内直连免代理，免费 2000 次/天（Turbo）+ 500 次/天（标准）。注册一个账号就有。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("UAT Token") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(if (visible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "去 simpletex.cn 注册账号 → 控制台 → 获取 UAT Token 粘贴到这。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "也可以稍后在「设置」里配置。\n如果暂时不想动，下一步直接跳过即可。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── 页面：完成 ────────────────────────────────────────────────────────────

@Composable
private fun CompletionPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "可以出发了",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "记得：",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                BulletLine("「记忆」选你今天要练的公式，长按可以激活进队列")
                Spacer(Modifier.height(10.dp))
                BulletLine("「复习」按算法节奏来，按钮选自己真实的记忆程度，别给自己注水")
                Spacer(Modifier.height(10.dp))
                BulletLine("「测试」是默写练兵场，识别失败别慌 — 每条都能反馈给开发者")
                Spacer(Modifier.height(10.dp))
                BulletLine("不爽现在的设置？任何时候去「设置」里调")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "祝你考场上顺手得像写自己的名字。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── 通用片段 ──────────────────────────────────────────────────────────────

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PageSubtitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BulletLine(text: String) {
    Row {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            val isActive = index == current
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 10.dp else 8.dp),
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
            ) { Box(Modifier.fillMaxSize()) }
        }
    }
}
