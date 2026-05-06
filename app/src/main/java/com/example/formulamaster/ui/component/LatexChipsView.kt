package com.example.formulamaster.ui.component

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson

/**
 * Sprint 3 Task 3.4 — 通用 KaTeX chip 渲染组件
 *
 * 单 WebView 一次性渲染 N 个 LaTeX 字符串为 chip 形态，flex-wrap 自动换行，
 * 每个 chip 宽度自适应公式实际渲染宽度。
 *
 * ## 两种使用模式（[selectable] 控制）
 *
 * **selectable = true（多选模式）**：[FeedbackDialog] "标记错误部件"区使用
 * - chip 可点击切换选中态（CSS transition 平滑过渡）
 * - 选中时主色 2dp 描边 + ✓ 图标 + 主色容器背景
 * - 选中状态由 JS 内部 Set 维护，每次变化通过 [Bridge.onSelectionChanged] 上报
 *
 * **selectable = false（只读模式）**：[FeedbackDialog] "识别返回候选"区使用
 * - chip 视觉退化为类似 [androidx.compose.material3.AssistChip] 禁用态：灰描边、不响应点击
 * - JS 不绑定 click 监听，不会触发 onSelectionChanged 回调
 * - [onSelectionChanged] 参数被忽略
 *
 * ## 为什么用单 WebView 渲染整片
 * 原方案考虑过 N 个 [MathFormulaView] 各承载一个 chip，但：
 * - 每个 [MathFormulaView] = 一个 WebView 实例，N 个 chip = N 个 WebView 同时初始化
 * - WebView 冷启动 ~80ms，KaTeX JS 引擎每个 WebView 各 parse 一次
 * - 8 个 chip 的 dialog 会有 ~600ms 阻塞
 *
 * 改用**单个 WebView 一次性渲染所有 chip**：
 * - WebView 冷启动只一次（~80ms）
 * - KaTeX JS 引擎只 parse 一次，N 次 render 调用合并为同一进程内的 DOM 操作
 * - CSS `flex-wrap` 自然换行，无需固定 chip 宽度
 *
 * ## 双向桥
 * - **Compose → JS**：`items` / `selectable` / 主题颜色注入到 HTML 模板，`loadDataWithBaseURL` 加载
 * - **JS → Compose**：[Bridge.onHeightChanged] 上报 body.scrollHeight；
 *   selectable=true 时 [Bridge.onSelectionChanged] 上报选中索引集
 *
 * ## 高度自适应
 * WebView 不能 wrap_content，初始 [INITIAL_HEIGHT_DP] 占位，JS 用 [ResizeObserver]
 * 持续监听 `body.scrollHeight` 变化（涵盖 KaTeX 字体异步加载、容器宽度变化等场景），
 * 通过 bridge 回报后 Compose 更新 [Modifier.height]。
 *
 * JS 上报的 `body.scrollHeight` 单位为 CSS px。因 [WebViewPool] 设了
 * `useWideViewPort = false`，1 CSS px = 1 dp，**直接当 Dp 用**，不要走 density 二次转换
 * （会把数值除掉 density 倍 → 容器变矮，曾踩坑）。
 *
 * ## 选中状态来源（仅 selectable=true）
 * 选中状态由**JS 内部维护**（`Set` 对象），每次变化 emit 排序后的索引列表给 Compose。
 * Compose 不直接持有 chip 视觉的 selected 状态——避免 Compose state 改变导致 LaunchedEffect
 * 重跑、HTML 重载、选中视觉清空的 race。
 *
 * @param items LaTeX 字符串列表
 * @param selectable 是否允许多选交互；false 时退化为只读展示
 * @param onSelectionChanged 选中索引集变化回调（仅 selectable=true 时触发）
 */
@Composable
fun LatexChipsView(
    items: List<String>,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    onSelectionChanged: (Set<Int>) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // 主题色取一次（Composable 退出后不会变）
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onSurface
    // 只读 chip 的填充色：surfaceVariant 灰底 + onSurfaceVariant 弱化文字色
    // 与可选中 chip 的 outlined 风格形成 M3 标准对比
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 用 rememberUpdatedState 包外部回调，确保 bridge 内调用永远拿最新版
    val onSelectionChangedState by rememberUpdatedState(onSelectionChanged)

    // 模板只读取一次：在此 Composable 实例的生命周期内复用
    val template = remember {
        context.assets.open(TEMPLATE_FILE)
            .bufferedReader().use { it.readText() }
    }

    // WebView 复用池里取一个实例（dispose 时归还）
    val webView: WebView = remember { WebViewPool.acquire(context) }

    // JS 上报的内容高度，初始用占位值避免 AndroidView 0 高度被布局忽略
    var measuredHeight by remember { mutableStateOf(INITIAL_HEIGHT_DP.dp) }

    // ── JS Bridge ────────────────────────────────────────────────────────────
    //
    // Bridge 在 WebView 后台线程被调用；写 mutableStateOf 必须 post 回主线程
    DisposableEffect(webView) {
        // WebViewPool 复用时 client 状态可能未知，给一个空 client 避免 about:blank 残留
        webView.webViewClient = WebViewClient()

        val bridge = Bridge(
            onHeight = { cssPxAsDp ->
                webView.post {
                    measuredHeight = cssPxAsDp.dp
                }
            },
            onSelection = { ids ->
                webView.post {
                    onSelectionChangedState(ids)
                }
            }
        )
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)

        onDispose {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            // 解绑 client，避免 release 时的 about:blank 触发已 dispose 的回调
            webView.webViewClient = WebViewClient()
            WebViewPool.release(webView)
        }
    }

    // ── 渲染：items / 主题 / selectable 变化时重载 HTML ─────────────────────
    LaunchedEffect(webView, items, isDark, selectable) {
        val gson = Gson()
        val html = template
            .replace("{{ITEMS_JSON}}", gson.toJson(items))
            .replace("{{INTERACTIVE}}", if (selectable) "true" else "false")
            .replace("{{INTERACTIVE_CLASS}}", if (selectable) "interactive" else "")
            .replace("{{PRIMARY}}", primary.toHexCss())
            .replace("{{OUTLINE}}", outline.toHexCss())
            .replace("{{SELECTED_BG}}", selectedBg.toHexCss())
            .replace("{{TEXT_COLOR}}", textColor.toHexCss())
            .replace("{{SURFACE_VARIANT}}", surfaceVariant.toHexCss())
            .replace("{{ON_SURFACE_VARIANT}}", onSurfaceVariant.toHexCss())
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    AndroidView(
        factory = { webView },
        update = {},
        modifier = modifier.height(measuredHeight)
    )
}

/**
 * JS → Compose 桥接对象。
 *
 * `@JavascriptInterface` 注解的方法才能被 JS 调用（API 17+ 安全约束）。
 * 方法在 WebView 内部线程调用，不能直接修改 Compose state——必须在外层 post 到主线程。
 */
private class Bridge(
    val onHeight: (Int) -> Unit,
    val onSelection: (Set<Int>) -> Unit
) {
    @JavascriptInterface
    fun onHeightChanged(px: Int) {
        onHeight(px)
    }

    @JavascriptInterface
    fun onSelectionChanged(json: String) {
        // JS 传入形如 "[0, 2, 5]" 的整数数组 JSON
        val ids = parseIntArrayJson(json)
        onSelection(ids)
    }

    private fun parseIntArrayJson(json: String): Set<Int> {
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isBlank()) return emptySet()
        return trimmed.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }
}

/**
 * M3 [Color] → CSS 十六进制字符串 `#RRGGBB`。
 * 不带 alpha——M3 主题色 alpha 一律 1.0。
 */
private fun Color.toHexCss(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

private const val TEMPLATE_FILE = "latex_chips_template.html"
private const val BRIDGE_NAME = "AndroidBridge"
/** WebView 高度初始占位（约 2 行 chip 的容纳量），JS 上报后被覆盖。 */
private const val INITIAL_HEIGHT_DP = 96
