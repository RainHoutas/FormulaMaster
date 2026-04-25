package com.example.formulamaster.ui.component

import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 离线 KaTeX 公式渲染组件（Task 6.2 起改用 [WebViewPool] 复用）。
 *
 * ## 渲染流程
 * 1. [WebViewPool.acquire] 从池中取出（或新建）已预配置好 settings 的 WebView
 * 2. `AndroidView(factory = { webView })` 将其挂入 Compose 视图树
 * 3. `update` 块将 LaTeX + 主题注入 HTML 模板并调用 `loadDataWithBaseURL`
 * 4. `DisposableEffect.onDispose` 在 Composable 离开 Composition 时把 WebView 归还池
 *
 * ## 深色模式
 * 由 Compose 端 `isSystemInDarkTheme()` 注入 `body.dark` CSS class，
 * 不依赖 WebView 的 `prefers-color-scheme`（各厂商 ROM 实现不一致）。
 *
 * ## BaseURL
 * 必须用 `loadDataWithBaseURL("file:///android_asset/", …)` 而非 `loadData`，
 * 否则 WebView 安全策略会阻止 KaTeX JS/CSS/字体文件的加载。
 *
 * @param latex    LaTeX 源码，无需包裹 $$ 符号，HTML 模板已启用 displayMode
 * @param modifier Compose Modifier
 */
@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // 模板只读取一次：在此 Composable 实例的生命周期内复用
    val template = remember {
        context.assets.open("math_template.html")
            .bufferedReader()
            .use { it.readText() }
    }

    // Task 6.2：从复用池取出 WebView（池空则新建）。
    // remember 无 key → 此 Composable 实例存活期间持有同一个 WebView 引用。
    val webView: WebView = remember { WebViewPool.acquire(context) }

    // Composable 离开 Composition（滚出屏幕、路由切换等）时归还到池。
    DisposableEffect(webView) {
        onDispose { WebViewPool.release(webView) }
    }

    AndroidView(
        // factory 仅在 AndroidView 首次进入 Composition 时调用一次，
        // 后续重组走 update 块，不会重建 WebView。
        factory = { webView },
        update = { wv ->
            val html = template
                .replace("{{LATEX}}", latex.escapeForHtmlAttribute())
                .replace("{{THEME}}", if (isDark) "dark" else "light")
            wv.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}

/**
 * HTML 属性转义：处理 LaTeX 中可能出现的 & < > " 四个特殊字符。
 * 反斜杠在 HTML 属性中无需转义。
 */
private fun String.escapeForHtmlAttribute(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
