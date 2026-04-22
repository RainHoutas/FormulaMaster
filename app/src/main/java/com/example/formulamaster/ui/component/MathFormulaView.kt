package com.example.formulamaster.ui.component

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 离线 KaTeX 公式渲染组件。
 *
 * 从 assets/math_template.html 读取模板，将 [latex] 注入后通过 WebView 渲染。
 * 使用 loadDataWithBaseURL 确保本地 KaTeX 资源（JS/CSS/字体）可被正常加载。
 * 深色模式由 CSS @media (prefers-color-scheme: dark) 自动适配（Android 10+）。
 *
 * @param latex  LaTeX 源码字符串，无需包裹 $$ 符号，组件已启用 displayMode
 * @param modifier Compose Modifier
 */
@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val template = remember {
        context.assets.open("math_template.html")
            .bufferedReader()
            .use { it.readText() }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // 允许通过 file:// URL 访问本地 assets（KaTeX JS/CSS/字体）
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                }
                // 背景透明，与 Compose Surface 完美融合
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { webView ->
            val html = template.replace("{{LATEX}}", latex.escapeForHtmlAttribute())
            webView.loadDataWithBaseURL(
                "file:///android_asset/",   // baseUrl：让 KaTeX 能加载本地资源
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
 * 将 LaTeX 字符串 HTML 属性转义。
 *
 * LaTeX 中 \frac、\int 等反斜杠无需转义（HTML 属性允许），
 * 只需处理 & < > " 四个 HTML 特殊字符，避免破坏 data-latex 属性的引号边界。
 */
private fun String.escapeForHtmlAttribute(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
