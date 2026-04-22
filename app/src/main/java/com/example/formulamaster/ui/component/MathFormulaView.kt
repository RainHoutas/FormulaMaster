package com.example.formulamaster.ui.component

import android.graphics.Color
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
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
 * 深色模式由 Compose 端读取 isSystemInDarkTheme() 后注入 CSS class，
 * 不依赖 WebView 自身的 prefers-color-scheme 支持（各厂商 ROM 实现不一致）。
 *
 * @param latex    LaTeX 源码，无需包裹 $$ 符号，组件已启用 displayMode
 * @param modifier Compose Modifier
 */
@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

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
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                }
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { webView ->
            val html = template
                .replace("{{LATEX}}", latex.escapeForHtmlAttribute())
                .replace("{{THEME}}", if (isDark) "dark" else "light")
            webView.loadDataWithBaseURL(
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
 * HTML 属性转义：处理 & < > " 四个特殊字符。
 * LaTeX 中的反斜杠在 HTML 属性中无需转义。
 */
private fun String.escapeForHtmlAttribute(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
