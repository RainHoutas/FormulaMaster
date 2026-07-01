package com.example.formulamaster.domain

/**
 * 顽固节点（leech）判定（学习流程重构 Sprint 3 Task 3.4）。
 *
 * 两条触发路径任一成立即判 leech：
 * 1. **累计遗忘**：`lapses >= LAPSE_THRESHOLD`（原有规则）
 * 2. **错题反向高频**：近 [ERROR_MARK_WINDOW_DAYS] 日内被错题本反向标记
 *    `>= ERROR_MARK_THRESHOLD` 次（Task 3.3 错题本录入后才有此信号）
 *
 * 纯函数，无 Android / Room 依赖，可直接单测。
 */
object LeechDetector {

    /** 累计遗忘次数阈值：lapses ≥ 4 判 leech（原 MemoryScreen 内联规则外提）。 */
    const val LAPSE_THRESHOLD = 4

    /** 错题反向标记的时间窗（天）。 */
    const val ERROR_MARK_WINDOW_DAYS = 7

    /** 时间窗内被错题标记次数阈值：≥ 2 次判 leech。 */
    const val ERROR_MARK_THRESHOLD = 2

    /** [ERROR_MARK_WINDOW_DAYS] 对应的毫秒数，供调用方算截止时间。 */
    const val ERROR_MARK_WINDOW_MS = ERROR_MARK_WINDOW_DAYS * 86_400_000L

    /**
     * @param lapses            该公式子卡聚合的累计遗忘次数
     * @param recentErrorMarks  近 [ERROR_MARK_WINDOW_DAYS] 日内被错题反向标记的**去重错题条数**
     */
    fun isLeech(lapses: Int, recentErrorMarks: Int): Boolean =
        lapses >= LAPSE_THRESHOLD || recentErrorMarks >= ERROR_MARK_THRESHOLD
}
