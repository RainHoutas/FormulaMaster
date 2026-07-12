package com.example.formulamaster.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.formulamaster.domain.EntryRelationType
import com.example.formulamaster.domain.graph.BubbleLayout
import com.example.formulamaster.domain.graph.ChapterKey
import com.example.formulamaster.domain.graph.DerivationEdge
import com.example.formulamaster.domain.graph.GraphModel
import com.example.formulamaster.domain.graph.GraphNode
import com.example.formulamaster.domain.graph.GraphPoint
import com.example.formulamaster.domain.graph.NodeState
import com.example.formulamaster.domain.graph.OverviewLayout
import com.example.formulamaster.domain.graph.WithinChapterLayout
import com.example.formulamaster.ui.viewmodel.GraphViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow

// 学科分类色（母层气泡填充）
private val SubjectColors = mapOf(
    "高数" to Color(0xFF3B82C4),
    "线代" to Color(0xFFC07F2C),
    "概率论" to Color(0xFF3F9B6B)
)
private val EdgeColor = Color(0x804A5AD0)          // 跨章关联边（半透明靛）
private val MasterColor = Color(0xFF3F9B6B)        // 掌握度环 / 已掌握
private val LearningColor = Color(0xFFDD9A2F)      // 学习中
private val NewColor = Color(0xFF8A93A5)           // 未学
private val LeechColor = Color(0xFFD9584F)         // 顽固
// 子层块内边配色（推导/易混/同族）
private val DerivEdge = Color(0xB34A5AD0)
private val ConfEdge = Color(0xB3D67A26)
private val SibEdge = Color(0x66AAB0BE)

/**
 * 公式族图谱 · 母层（Sprint 4 Task 4.2 增量①，RFC §9.4 D17）。
 *
 * 章节聚类分区气泡地图，可拖动、松手吸附最近气泡居中，底部进度条随当前气泡走。
 * 跨章关联用 Canvas 画气泡间连线。相机变换（graphicsLayer 平移+缩放）下 Canvas 边与
 * Composable 气泡共用同一世界坐标系，保持对齐。
 *
 * ⚠ 增量①：点气泡=居中+选中（进度条更新）。钻取子层 / 开合动画在增量②接入。
 */
@Composable
fun GraphScreen(
    onFormulaClick: (String, Boolean) -> Unit,
    onOpenErrorBook: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: GraphViewModel = viewModel(factory = GraphViewModel.factory(LocalContext.current))
) {
    val ui by viewModel.uiState.collectAsState()
    val model = ui.model
    val overview = ui.overview

    if (ui.isLoading || model == null || overview == null) {
        CircularProgressIndicator(Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        return
    }

    // rememberSaveable：跳学习/详情页后返回，能恢复到当前钻入的章节子层（不退回母层）
    var openKeyStr by rememberSaveable { mutableStateOf<String?>(null) }
    val openKey = remember(openKeyStr) {
        openKeyStr?.let { s ->
            val i = s.indexOf("::")
            if (i < 0) null else ChapterKey(s.substring(0, i), s.substring(i + 2))
        }
    }
    var openOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    // 退出动画期间保留最后打开的章节，供内容渲染
    val shownKey = remember { mutableStateOf<ChapterKey?>(null) }
    openKey?.let { shownKey.value = it }
    // 跨章跳转时聚焦的目标公式（子层据此居中 + 脉冲）；普通钻入为 null
    var focusFormula by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = openKey != null) { openKeyStr = null }

    Box(Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        GraphOverviewCanvas(
            model = model,
            overview = overview,
            contentPadding = contentPadding,
            onBubbleTap = { key, pos ->
                openOrigin = if (screenSize.width == 0) TransformOrigin.Center
                else TransformOrigin(pos.x / screenSize.width, pos.y / screenSize.height)
                focusFormula = null
                openKeyStr = key.subject + "::" + key.chapter
            },
            modifier = Modifier.fillMaxSize()
        )

        if (openKey == null) {
            FloatingActionButton(
                onClick = onOpenErrorBook,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = contentPadding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp
                    )
            ) { Icon(Icons.Filled.Warning, contentDescription = "错题本") }
        }

        // 子层：点气泡钻入 → 从气泡位置放大展开
        AnimatedVisibility(
            visible = openKey != null,
            // 进入：从点击的气泡位置放大展开；返回：气泡此时已居中，统一朝屏幕中心缩回
            enter = fadeIn() + scaleIn(initialScale = 0.55f, transformOrigin = openOrigin),
            exit = fadeOut() + scaleOut(targetScale = 0.55f, transformOrigin = TransformOrigin.Center)
        ) {
            shownKey.value?.let { key ->
                ChapterDetailScreen(
                    model = model,
                    chapter = key,
                    focusId = focusFormula,
                    contentPadding = contentPadding,
                    onBack = { openKeyStr = null },
                    onFormulaClick = onFormulaClick,
                    onJump = { targetId ->
                        model.node(targetId)?.let { n ->
                            focusFormula = targetId
                            openKeyStr = n.subject + "::" + n.chapter
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GraphOverviewCanvas(
    model: GraphModel,
    overview: OverviewLayout,
    contentPadding: PaddingValues,
    onBubbleTap: (ChapterKey, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(IntSize.Zero) }
    // rememberSaveable：pan 位置跨导航（跳学习/详情页返回）保留
    var tx by rememberSaveable { mutableStateOf(Float.NaN) }
    var ty by rememberSaveable { mutableStateOf(Float.NaN) }
    val scale = 1f
    var selected by remember { mutableStateOf<ChapterKey?>(null) }
    var animJob by remember { mutableStateOf<Job?>(null) }

    // 世界坐标(dp) → 屏幕像素
    fun wpx(worldDp: Float) = worldDp * density
    fun centerOn(b: BubbleLayout) {   // 瞬时居中，仅用于初次定位
        tx = stage.width / 2f - scale * wpx(b.center.x)
        ty = stage.height / 2f - scale * wpx(b.center.y)
    }
    fun animateCenterOn(b: BubbleLayout) {   // 缓动居中，供吸附 / 点气泡共用
        val startX = tx; val startY = ty
        val endX = stage.width / 2f - scale * wpx(b.center.x)
        val endY = stage.height / 2f - scale * wpx(b.center.y)
        animJob?.cancel()   // 打断上一段动画，防连点打架
        animJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(360)) { f, _ ->
                tx = startX + (endX - startX) * f
                ty = startY + (endY - startY) * f
            }
        }
    }
    fun nearestBubble(): Map.Entry<ChapterKey, BubbleLayout>? {
        if (stage == IntSize.Zero) return null
        val wx = (stage.width / 2f - tx) / scale / density
        val wy = (stage.height / 2f - ty) / scale / density
        return overview.bubbles.entries.minByOrNull {
            val dx = it.value.center.x - wx; val dy = it.value.center.y - wy; dx * dx + dy * dy
        }
    }
    fun snap() {
        val n = nearestBubble() ?: return
        selected = n.key
        animateCenterOn(n.value)
    }

    // 跨章关联（章节对，去重）
    val chapterEdges = remember(model) {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<ChapterKey, ChapterKey>>()
        model.edges.forEach { e ->
            val a = model.node(e.fromId); val b = model.node(e.toId)
            if (a != null && b != null) {
                val ka = ChapterKey(a.subject, a.chapter); val kb = ChapterKey(b.subject, b.chapter)
                if (ka != kb) {
                    val key = listOf("${ka.subject}/${ka.chapter}", "${kb.subject}/${kb.chapter}").sorted().joinToString("|")
                    if (seen.add(key)) out.add(ka to kb)
                }
            }
        }
        out
    }

    Box(
        modifier = modifier
            .onSizeChanged { s ->
                stage = s
                // 仅未定位过时初次居中；tx 已有保存值（返回场景）则不覆盖
                if (tx.isNaN() && s != IntSize.Zero) {
                    overview.bubbles.entries.firstOrNull()?.let { centerOn(it.value); selected = it.key }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { animJob?.cancel() },   // 拖动打断吸附/点击动画
                    onDrag = { _, drag -> tx += drag.x; ty += drag.y },
                    onDragEnd = { snap() }
                )
            }
            .pointerInput(overview) {
                detectTapGestures { pos ->
                    val wx = (pos.x - tx) / scale / density
                    val wy = (pos.y - ty) / scale / density
                    val hit = overview.bubbles.entries.firstOrNull {
                        val dx = it.value.center.x - wx; val dy = it.value.center.y - wy
                        dx * dx + dy * dy <= it.value.radius * it.value.radius
                    }
                    if (hit != null) {
                        selected = hit.key
                        animateCenterOn(hit.value)   // 母层焦点跟随点击，返回时落在该气泡
                        onBubbleTap(hit.key, pos)
                    }
                }
            }
    ) {
        if (tx.isNaN()) return@Box

        // 相机层：Canvas 边 + Composable 气泡共用世界坐标
        Box(
            Modifier.graphicsLayer {
                translationX = tx; translationY = ty
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        ) {
            // 跨章边（在气泡之下）
            Canvas(Modifier.size(overview.world.w.dp, overview.world.h.dp)) {
                chapterEdges.forEach { (ka, kb) ->
                    val a = overview.bubbles[ka]?.center ?: return@forEach
                    val b = overview.bubbles[kb]?.center ?: return@forEach
                    drawLine(
                        color = EdgeColor,
                        start = Offset(wpx(a.x), wpx(a.y)),
                        end = Offset(wpx(b.x), wpx(b.y)),
                        strokeWidth = 2f * density
                    )
                }
            }
            // 气泡
            overview.bubbles.forEach { (key, b) ->
                val ids = model.idsOf(key)
                val mastered = ids.count { model.node(it)?.state == NodeState.MASTERED }
                val prog = if (ids.isEmpty()) 0f else mastered.toFloat() / ids.size
                Bubble(
                    key = key,
                    layout = b,
                    count = ids.size,
                    progress = prog,
                    selected = key == selected
                )
            }
        }

        // 底部进度条（当前选中/居中气泡）
        selected?.let { key ->
            val ids = model.idsOf(key)
            val mastered = ids.count { model.node(it)?.state == NodeState.MASTERED }
            ChapterProgressBar(
                key = key,
                mastered = mastered,
                total = ids.size,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding())
            )
        }
    }
}

@Composable
private fun Bubble(
    key: ChapterKey,
    layout: BubbleLayout,
    count: Int,
    progress: Float,
    selected: Boolean
) {
    val r = layout.radius
    val fill = SubjectColors[key.subject] ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .offset(x = (layout.center.x - 55f).dp, y = (layout.center.y - r).dp)
            .width(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size((2 * r).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = fill)
                if (progress > 0f) {
                    val sw = 4f * density
                    drawArc(
                        color = MasterColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset(sw, sw),
                        size = androidx.compose.ui.geometry.Size(size.width - 2 * sw, size.height - 2 * sw),
                        style = Stroke(width = sw)
                    )
                }
            }
            Text(
                text = "$count",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = key.chapter,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ChapterProgressBar(
    key: ChapterKey,
    mastered: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val prog = if (total == 0) 0f else mastered.toFloat() / total
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(key.chapter, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "  ${(prog * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MasterColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            LinearProgressIndicator(
                progress = { prog },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MasterColor
            )
            Text(
                "${key.subject} · 已掌握 $mastered/$total 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ── 子层：章节内块内分层子图（Sprint 4 Task 4.2 增量②）────────────────────────

@Composable
private fun ChapterDetailScreen(
    model: GraphModel,
    chapter: ChapterKey,
    focusId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFormulaClick: (String, Boolean) -> Unit,
    onJump: (String) -> Unit
) {
    val density = LocalDensity.current.density
    val ids = remember(model, chapter) { model.idsOf(chapter) }
    // 每个公式的跨章邻居（供 ↗ 角标 + 跳转列表）
    val crossMap = remember(model, chapter) { ids.associateWith { model.crossChapterNeighbors(it) } }
    var crossFor by remember { mutableStateOf<String?>(null) }
    // 聚焦公式脉冲
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(focusId, chapter) {
        if (focusId != null && focusId in ids) {
            pulse.snapTo(1f); pulse.animateTo(1.2f, tween(220)); pulse.animateTo(1f, tween(320))
        }
    }
    val rawPos = remember(model, chapter) {
        val idSet = ids.toHashSet()
        val derivEdges = model.edges
            .filter { it.fromId in idSet && it.toId in idSet && it.type == EntryRelationType.DERIVATION }
            .map { DerivationEdge(it.fromId, it.toId) }
        WithinChapterLayout.layout(ids, derivEdges)
    }
    // 归一化：把最左节点平移出边距，避免负坐标跑出画布
    val pos = remember(rawPos) {
        val minX = rawPos.values.minOfOrNull { it.x } ?: 0f
        val dx = if (minX < 60f) 60f - minX else 0f
        rawPos.mapValues { GraphPoint(it.value.x + dx, it.value.y) }
    }
    val innerEdges = remember(model, chapter) {
        val idSet = ids.toHashSet()
        model.edges.filter { it.fromId in idSet && it.toId in idSet }
    }
    val worldW = (pos.values.maxOfOrNull { it.x } ?: 360f) + 200f
    val worldH = (pos.values.maxOfOrNull { it.y } ?: 300f) + 120f

    var stage by remember { mutableStateOf(IntSize.Zero) }
    // rememberSaveable(chapter)：同一章节跨导航（跳学习/详情返回）保留浏览位置；换章节重置
    var tx by rememberSaveable(chapter) { mutableStateOf(Float.NaN) }
    var ty by rememberSaveable(chapter) { mutableStateOf(Float.NaN) }
    fun wpx(v: Float) = v * density

    val subjectColor = SubjectColors[chapter.subject] ?: MaterialTheme.colorScheme.primary
    val mastered = remember(model, chapter) { ids.count { model.node(it)?.state == NodeState.MASTERED } }
    val frameShape = RoundedCornerShape(24.dp)

    // 相机初始居中：用 LaunchedEffect 而非 onSizeChanged（换章 size 不变时后者不重触发，
    // 会导致跳转后 tx 停在 NaN / 不居中）。有聚焦公式对准它，否则对准全章质心。
    LaunchedEffect(chapter, stage) {
        if (tx.isNaN() && stage != IntSize.Zero && pos.isNotEmpty()) {
            val fp = focusId?.let { pos[it] }
            val cx = fp?.x ?: pos.values.map { it.x }.average().toFloat()
            val cy = fp?.y ?: pos.values.map { it.y }.average().toFloat()
            tx = stage.width / 2f - wpx(cx)
            ty = stage.height / 2f - wpx(cy)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 内缩圆角画布框：避开屏幕圆角 / 状态栏 / 导航栏，四角可见
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 6.dp,
                    start = 8.dp, end = 8.dp
                )
                .clip(frameShape)
                .border(width = 2.dp, color = subjectColor, shape = frameShape)   // 父气泡（学科）色框
                .onSizeChanged { stage = it }
                .pointerInput(chapter) {
                    // key=chapter：跳转换章后 tx/ty 是新 state，pointerInput 须重启以重新捕获（否则拖的是旧死状态）
                    detectDragGestures { _, drag -> tx += drag.x; ty += drag.y }
                }
        ) {
            if (!tx.isNaN()) {
                Box(
                    Modifier.graphicsLayer {
                        translationX = tx; translationY = ty
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                ) {
                    Canvas(Modifier.size(worldW.dp, worldH.dp)) {
                        innerEdges.forEach { e ->
                            val a = pos[e.fromId] ?: return@forEach
                            val b = pos[e.toId] ?: return@forEach
                            val col = when (e.type) {
                                EntryRelationType.DERIVATION -> DerivEdge
                                EntryRelationType.CONFUSABLE -> ConfEdge
                                EntryRelationType.SIBLING -> SibEdge
                            }
                            drawLine(
                                color = col,
                                start = Offset(wpx(a.x), wpx(a.y)),
                                end = Offset(wpx(b.x), wpx(b.y)),
                                strokeWidth = 2f * density,
                                pathEffect = if (e.type == EntryRelationType.SIBLING)
                                    PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                            )
                        }
                    }
                    ids.forEach { id ->
                        val n = model.node(id) ?: return@forEach
                        val p = pos[id] ?: return@forEach
                        NodeChip(
                            node = n,
                            x = p.x,
                            y = p.y,
                            crossCount = crossMap[id]?.size ?: 0,
                            pulse = if (id == focusId) pulse.value else 1f,
                            onClick = { onFormulaClick(id, n.state != NodeState.NEW) },
                            onCrossTap = { crossFor = id }
                        )
                    }
                }
            }
        }

        // 浮动返回键（圆形，左上，M3 tonal 图标按钮）
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = contentPadding.calculateTopPadding() + 14.dp, start = 18.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }

        // 浮动标题胶囊（居中，含掌握进度；M3 surfaceContainerHigh + tonal 高度）
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding() + 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    chapter.chapter,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${chapter.subject} · 已掌握 $mastered/${ids.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 连线图例（底部居中）：三种公式关系
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EdgeLegendItem(DerivEdge, "推导")
                EdgeLegendItem(ConfEdge, "易混")
                EdgeLegendItem(SibEdge, "同族")
            }
        }

        // 跨章关联列表浮层（点节点 ↗ 角标弹出）→ 选一条跳到目标章并居中该公式
        crossFor?.let { srcId ->
            val targets = crossMap[srcId].orEmpty()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .pointerInput(Unit) { detectTapGestures { crossFor = null } }
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .widthIn(max = 320.dp)
            ) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        "跨章关联 · ${targets.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                    targets.forEach { tId ->
                        val tn = model.node(tId) ?: return@forEach
                        Surface(
                            onClick = { crossFor = null; onJump(tId) },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                Text(tn.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "→ ${tn.subject} · ${tn.chapter}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeChip(
    node: GraphNode,
    x: Float,
    y: Float,
    crossCount: Int,
    pulse: Float,
    onClick: () -> Unit,
    onCrossTap: () -> Unit
) {
    val border = when {
        node.isLeech -> LeechColor
        node.state == NodeState.MASTERED -> MasterColor
        node.state == NodeState.LEARNING -> LearningColor
        else -> NewColor
    }
    Box(
        modifier = Modifier
            .offset(x = (x - 64f).dp, y = (y - 22f).dp)
            .width(128.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }   // 聚焦脉冲
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, border),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.state == NodeState.MASTERED) {
                    Text("✓ ", color = MasterColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (node.isLeech) {
            Text(
                "🔥",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-9).dp)
            )
        }
        // 跨章关联角标 ↗N（点击弹关联列表）
        if (crossCount > 0) {
            Surface(
                onClick = onCrossTap,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                // 挪到边框外右下角紧贴：整体偏出芯片右下角
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 12.dp, y = 14.dp)
            ) {
                Text(
                    "↗$crossCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EdgeLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
