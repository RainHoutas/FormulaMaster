package com.example.formulamaster.domain

/**
 * 错题反向标记计数（学习流程重构 Sprint 3 Task 3.4）。
 *
 * 统计近 [LeechDetector.ERROR_MARK_WINDOW_MS] 时间窗内，每个 formulaId 被多少**条错题**标记。
 * 同一条错题即使把某公式列多次也只计 1（按错题条数去重），供 [LeechDetector.isLeech] 第 2 条路径。
 *
 * 纯函数，JSON 解析由调用方（ViewModel）完成后传入已解析结果。
 */
object ErrorMarkTally {

    /**
     * @param reports 每条错题的 `(createdAt, wrongFormulaIds)`（JSON 已解析）
     * @param nowMs   当前时间；窗口 = `[nowMs - ERROR_MARK_WINDOW_MS, nowMs]`
     * @return formulaId → 近窗口内标记它的去重错题条数（未被标记的不在 map 中）
     */
    fun countRecent(reports: List<Pair<Long, List<String>>>, nowMs: Long): Map<String, Int> {
        val cutoff = nowMs - LeechDetector.ERROR_MARK_WINDOW_MS
        val counts = HashMap<String, Int>()
        reports.forEach { (createdAt, ids) ->
            if (createdAt < cutoff) return@forEach
            ids.toSet().forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
        }
        return counts
    }
}
