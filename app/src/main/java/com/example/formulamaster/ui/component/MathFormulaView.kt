package com.example.formulamaster.ui.component

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 离线 KaTeX 公式渲染组件（Task 6.2 起改用 [WebViewPool] 复用）。
 *
 * ## 渲染流程
 * 1. [WebViewPool.acquire] 从池中取出（或新建）已预配置好 settings 的 WebView
 * 2. `AndroidView(factory = { webView })` 将其挂入 Compose 视图树
 * 3. `LaunchedEffect(latex, isDark)` 把 LaTeX + 主题注入 HTML 模板并调用 `loadDataWithBaseURL`
 * 4. `WebViewClient.onPageFinished` 将 [contentReady] 置 true，触发遮罩淡出
 * 5. `DisposableEffect.onDispose` 在 Composable 离开 Composition 时把 WebView 归还池
 *
 * ## Sprint 2 Task 2.1 修复 C（2026-04-29）
 * 此前 WebView attach 到屏幕的瞬间到 `loadDataWithBaseURL` 完成新内容渲染之间的
 * 50–300ms 内（取决于设备性能 + KaTeX JS 解析），用户会看到旧公式残留或闪烁。
 * 现在用 `AnimatedVisibility` + surface 同色遮罩兜住 WebView：
 * - acquire / latex 变化 → contentReady 立即设 false → 遮罩瞬间出现
 * - WebViewClient.onPageFinished 触发（非 about:blank） → contentReady 设 true → 遮罩淡出
 * - 200ms 淡出比 NavHost 的 120ms fade 略长，确保 KaTeX 已绘制完成才显露
 *
 * ## 深色模式
 * 由 Compose 端 `isSystemInDarkTheme()` 注入 `body.dark` CSS class。
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

    // Task 6.2：从复用池取出 WebView（池空则新建）
    val webView: WebView = remember { WebViewPool.acquire(context) }

    // 修复 C：遮罩状态。每次 latex/isDark 变化先重置 false（遮罩立即覆盖），
    // 等 onPageFinished 后再 true（遮罩淡出）
    var contentReady by remember { mutableStateOf(false) }

    // 设置 WebViewClient 监听加载完成（webView 一生只设一次；下次复用时新 Composable 会再设一次覆盖）
    DisposableEffect(webView) {
        if (DIAG_ENABLED) {
            Log.d(DIAG_TAG, "ENTER wv@${System.identityHashCode(webView).toString(16)} latex=${latex.take(20)}")
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // about:blank 是 release 时清空触发的，不算"内容就绪"
                if (url != "about:blank") {
                    contentReady = true
                    if (DIAG_ENABLED) {
                        Log.d(DIAG_TAG, "READY wv@${System.identityHashCode(webView).toString(16)}")
                    }
                }
            }
        }
        onDispose {
            if (DIAG_ENABLED) {
                Log.d(DIAG_TAG, "LEAVE wv@${System.identityHashCode(webView).toString(16)} latex=${latex.take(20)}")
            }
            // 解绑 client，避免后续 release 触发的 about:blank 加载回调到已 dispose 的 setter
            webView.webViewClient = WebViewClient()
            WebViewPool.release(webView)
        }
    }

    // 修复 C：内容加载副作用提到 LaunchedEffect（取代原 update 块的 reload），
    // 把状态写入和 WebView 操作分开，避免 update 块写 mutableState 触发无限重组
    LaunchedEffect(webView, latex, isDark) {
        contentReady = false
        val html = template
            .replace("{{LATEX}}", latex.escapeForHtmlAttribute())
            .replace("{{THEME}}", if (isDark) "dark" else "light")
        if (DIAG_ENABLED) {
            Log.d(DIAG_TAG, "LOAD  wv@${System.identityHashCode(webView).toString(16)} latex=${latex.take(20)}")
        }
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { wv ->
                if (DIAG_ENABLED) {
                    Log.d(DIAG_TAG, "FACTORY wv@${System.identityHashCode(webView).toString(16)} latex=${latex.take(20)}")
                }
                webView
            },
            // update 块保持空（实际加载已被 LaunchedEffect 接管）
            update = {},
            modifier = Modifier.fillMaxSize()
        )
        // 修复 C：加载遮罩。AnimatedVisibility 默认 enter=fadeIn(0)+fadeOut(200) 已合用，
        // 但显式声明语义更清晰
        AnimatedVisibility(
            visible = !contentReady,
            enter = fadeIn(tween(0)),    // 立即出现遮罩
            exit = fadeOut(tween(MASK_FADE_OUT_MS))  // 200ms 淡出
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
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

// 修复 C：遮罩淡出时长（200ms 比 NavHost 120ms 略长，确保 KaTeX 已绘制完成才显露）
private const val MASK_FADE_OUT_MS = 200

// [PerfDiag] Sprint 2 Task 2.1 诊断（已收尾，2026-04-30）。开关复位 false，将来再排性能问题改 true 即可。
private const val DIAG_ENABLED = false
private const val DIAG_TAG = "PerfDiag.MathView"
