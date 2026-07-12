package com.example.formulamaster.domain

/**
 * 复习会话公式排列 · 交错策略（Sprint 5，研究报告 §7.2）。
 *
 * 复习路由器按 [order] 的公式顺序跨公式轮转。本纯函数按学习阶段的 [Interleave] 重排该顺序，
 * 实现"三轮策略切换"：一轮章内 block（同章连续，先形成模板）→ 二轮章内交错（同章内打散）→
 * 三轮起全交错（跨章 round-robin，逼大脑判断该用哪条公式）。
 *
 * 确定性（同输入同输出），无 Room / Android 依赖，可直接单测。
 */
object SessionInterleaver {

    fun interleave(
        order: List<String>,
        chapterOf: Map<String, String>,
        mode: Interleave
    ): List<String> {
        if (order.size <= 1) return order
        // 按章节分组，保留章节首次出现顺序 + 章内输入顺序（输入已按强标记/考频优先排过）
        val byChapter = LinkedHashMap<String, MutableList<String>>()
        order.forEach { id ->
            byChapter.getOrPut(chapterOf[id] ?: "") { mutableListOf() }.add(id)
        }
        return when (mode) {
            // 章内 block：同章连续
            Interleave.BLOCK -> byChapter.values.flatten()

            // 章内交错：章仍成块，但章内公式按稳定 hash 打散（破坏同族相邻）
            Interleave.WITHIN_CHAPTER ->
                byChapter.values.flatMap { chapter -> chapter.sortedBy { stableHash(it) } }

            // 全交错：跨章 round-robin，连续公式尽量来自不同章
            Interleave.FULL -> {
                val queues = byChapter.values.map { ArrayDeque(it) }
                val out = ArrayList<String>(order.size)
                while (queues.any { it.isNotEmpty() }) {
                    queues.forEach { q -> if (q.isNotEmpty()) out.add(q.removeFirst()) }
                }
                out
            }
        }
    }

    /** FNV-1a 稳定哈希，用于确定性"打散"（不引入随机，保证同池顺序唯一）。 */
    private fun stableHash(s: String): Int {
        var h = -2128831035  // 2166136261 as Int
        for (c in s) { h = h xor c.code; h *= 16777619 }
        return h
    }
}
