package com.example.formulamaster.ui.component

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.MainThread

/**
 * Task 6.2：WebView 复用池（单例，容量 [CAPACITY] = 3）。
 *
 * ## 解决的问题
 * `MathFormulaView` 基于 `AndroidView`，每次 Composable 进入 Composition（例如
 * LazyColumn 滚动、HorizontalPager 翻页）都会触发 `factory` 回调创建新 `WebView`。
 * WebView 初始化成本高（JS 引擎、V8 堆分配），在 ReviewCard 快速翻页时会产生
 * 明显的内存抖动和首帧白屏。
 *
 * ## 工作原理
 * 1. `acquire(context)`：先从池里取，取不到再新建；返回已配置好 settings 的 WebView
 * 2. `release(webView)`：Composable 离开 Composition 时归还；先从父布局 detach，
 *    再放回池尾；若池已满则丢弃（让 GC 处理）
 * 3. `warmUp(context, count)`：可选预热，在 Application/Activity 启动时提前建好
 *    若干 WebView，消除首次渲染的冷启动白屏
 *
 * ## 线程安全
 * WebView 只能在主线程操作，Compose 组合也在主线程运行，故无需额外同步。
 * **不要在后台线程调用本对象的任何方法。**
 *
 * ## 容量选择 = 3
 * 同屏最多同时可见：ReviewCard 中 1 个公式视图 + FormulaDetail 1 个 + Dialog 1 个 = 3。
 * 超出容量的 WebView 直接放弃（正常 GC），不影响正确性。
 *
 * ## Sprint 2 Task 2.1 诊断（2026-04-29）
 * 临时插桩 acquire/release/warmUp 的池大小变化 + WebView identity + 当前 URL，
 * 用于定位 Tab 切换时的卡顿和残留问题。完成后会移除这部分代码（搜索 [PerfDiag]）。
 */
object WebViewPool {

    const val CAPACITY = 3

    private val pool = ArrayDeque<WebView>(CAPACITY)

    // ── 公共 API ──────────────────────────────────────────────────────────────

    /**
     * 从池中获取一个已配置好的 WebView。
     * 池空时立即创建新实例。
     *
     * @param context 优先传 Activity context（避免 Application context 下的主题缺失），
     *                但 WebView 渲染不依赖 theme，两者均可。
     */
    @MainThread
    fun acquire(context: Context): WebView {
        val before = pool.size
        val reused = pool.removeFirstOrNull()
        val webView = reused ?: createWebView(context)
        if (DIAG_ENABLED) {
            Log.d(
                DIAG_TAG,
                "acquire ${webView.identityHashCode()} | reused=${reused != null} " +
                "| pool $before→${pool.size} | url=${webView.url ?: "null"}"
            )
        }
        return webView
    }

    /**
     * 将 WebView 归还池中。
     * 会先从当前父布局 detach，再 `loadUrl("about:blank")` 清空内容，最后放回池尾。
     * 若池已达 [CAPACITY]，直接丢弃（正常 GC）。
     *
     * ## Sprint 2 Task 2.1 修复 A（2026-04-29）
     * 此前 release 不清空内容，下次 acquire 复用时 attach 到屏幕的瞬间仍带着旧 KaTeX
     * 渲染，造成详情页/复习卡片切换时的"上一公式残留"。改为 release 时强制清空，
     * 从池里取出来时 WebView 已是空白状态，后续 [MathFormulaView] 的 update 块
     * `loadDataWithBaseURL` 才不会被旧帧抢屏。
     *
     * **注意**：`loadUrl("about:blank")` 为异步操作（WebView 内部排队），调用本身耗时
     * 微秒级，不阻塞主线程。真正的渲染切换在下一个 vsync 完成。
     */
    @MainThread
    fun release(webView: WebView) {
        val before = pool.size
        if (pool.size >= CAPACITY) {
            if (DIAG_ENABLED) {
                Log.d(
                    DIAG_TAG,
                    "release ${webView.identityHashCode()} DROPPED (pool full $before) " +
                    "| url=${webView.url ?: "null"}"
                )
            }
            return
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        // 修复 A：清空旧 KaTeX 内容，避免下次复用时残留
        webView.loadUrl("about:blank")
        pool.addLast(webView)
        if (DIAG_ENABLED) {
            Log.d(
                DIAG_TAG,
                "release ${webView.identityHashCode()} | pool $before→${pool.size} " +
                "| url=${webView.url ?: "null"} (cleared to about:blank)"
            )
        }
    }

    /**
     * 预热：提前创建 [count] 个 WebView 放入池中。
     * 建议在 MainActivity.onCreate 的 lifecycleScope 里调用，
     * 消除第一次进入复习页时的渲染白屏。
     *
     * @param count 预热数量，不超过 [CAPACITY]
     */
    @MainThread
    fun warmUp(context: Context, count: Int = CAPACITY) {
        val before = pool.size
        val need = minOf(count, CAPACITY) - pool.size
        repeat(need) { pool.addLast(createWebView(context)) }
        if (DIAG_ENABLED) {
            Log.d(DIAG_TAG, "warmUp request=$count created=$need | pool $before→${pool.size}")
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun createWebView(context: Context): WebView =
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true

                // 关键：必须显式关闭 wide viewport，否则 WebView 会用屏幕设备宽度
                // （而不是 WebView 自身 frame 宽度）作为 viewport，导致 body.clientWidth
                // 报告 412 之类的设备宽度，但实际 WebView 只有 ~280dp 宽，
                // 内容被自动缩放后看起来比 JS 计算的字号小得多。
                useWideViewPort = false
                // 关闭"概览模式"：避免 WebView 把整页缩放到刚好放下（造成额外缩小）
                loadWithOverviewMode = false
            }
            setBackgroundColor(Color.TRANSPARENT)
        }

    private fun WebView.identityHashCode(): String =
        "wv@${System.identityHashCode(this).toString(16)}"

    // [PerfDiag] Sprint 2 Task 2.1 诊断（已收尾，2026-04-30）。
    // 保留代码备查，将来若再有性能问题打开 DIAG_ENABLED = true 即可立刻获得 acquire/release/warmUp 的池大小变化日志。
    private const val DIAG_ENABLED = false
    private const val DIAG_TAG = "PerfDiag.Pool"
}
