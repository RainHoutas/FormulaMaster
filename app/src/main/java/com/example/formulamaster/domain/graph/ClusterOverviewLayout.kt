package com.example.formulamaster.domain.graph

/**
 * 母层布局：章节聚类分区（Sprint 4 Task 4.2，RFC §9.4 D17）。
 *
 * 把章节簇按**学科分带**（学科在固定竖直区域内成网格），每个章节 = 一个气泡，
 * 气泡大小随公式数增长。**确定性算法**：同一批章节簇 + 同一学科顺序 → 像素级一致的坐标，
 * 保证「章节固定方位 = 肌肉记忆锚点」。新增章节落进其学科带、不惊动别的带。
 *
 * 纯函数、无 Room / Compose 依赖，可直接单测。坐标为无量纲世界坐标。
 */
object ClusterOverviewLayout {

    private const val PAD = 40f
    private const val CELL_W = 180f       // 气泡水平间距
    private const val CELL_H = 168f       // 气泡竖直间距
    private const val SUBJECT_GAP = 64f   // 学科带之间的额外留白
    private const val BASE_R = 26f
    private const val PER_NODE = 3f
    private const val MAX_R = 52f
    private const val COLS = 3             // 每学科带内列数

    /**
     * @param clusters     章节簇列表（同学科的簇按输入顺序在带内排布）
     * @param subjectOrder 学科展示顺序（如 高数/线代/概率论）；未列出的学科稳定追加到末尾
     */
    fun layout(clusters: List<ChapterCluster>, subjectOrder: List<String>): OverviewLayout {
        val bubbles = LinkedHashMap<ChapterKey, BubbleLayout>()
        if (clusters.isEmpty()) return OverviewLayout(bubbles, GraphRect(0f, 0f, 2 * PAD, 2 * PAD))

        // 学科顺序：先按 subjectOrder，未列出的按首次出现顺序补在后面
        val present = subjectOrder.filter { s -> clusters.any { it.subject == s } }
        val extra = clusters.map { it.subject }.distinct().filter { it !in subjectOrder }
        val subjects = present + extra

        var cursorY = PAD
        var worldRight = 2 * PAD
        for (subject in subjects) {
            val group = clusters.filter { it.subject == subject }   // 保持输入顺序，稳定
            if (group.isEmpty()) continue
            val rows = (group.size + COLS - 1) / COLS
            group.forEachIndexed { i, c ->
                val col = i % COLS
                val row = i / COLS
                val cx = PAD + col * CELL_W + CELL_W / 2f
                val cy = cursorY + row * CELL_H + CELL_H / 2f
                val r = (BASE_R + c.nodeCount * PER_NODE).coerceAtMost(MAX_R)
                bubbles[c.key] = BubbleLayout(GraphPoint(cx, cy), r)
            }
            val cols = minOf(COLS, group.size)
            worldRight = maxOf(worldRight, PAD + cols * CELL_W + PAD)
            cursorY += rows * CELL_H + SUBJECT_GAP
        }
        val worldBottom = cursorY - SUBJECT_GAP + PAD
        return OverviewLayout(bubbles, GraphRect(0f, 0f, worldRight, worldBottom))
    }
}
