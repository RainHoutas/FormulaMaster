package com.example.formulamaster.domain.graph

/**
 * 子层布局：块内分层（Sprint 4 Task 4.2，RFC §9.4 D17）。
 *
 * 单个章节钻入后，把该章公式按**推导链纵向分层**：父（上游）在上、子在下，
 * 同层横向铺开。层 = 该节点在簇内推导边上的最长父链长度（无父 = 第 0 层，居顶）。
 * 无推导边的节点自然落在第 0 层。**确定性**：层内按 id 升序稳定排列。
 *
 * 纯函数、无 Room / Compose 依赖，可直接单测。坐标为无量纲世界坐标。
 */
object WithinChapterLayout {

    private const val TOP = 60f
    private const val ROW_H = 108f    // 层间距
    private const val COL_W = 150f    // 同层节点间距
    private const val CENTER_X = 200f

    /**
     * @param nodeIds        本章节的公式 id（顺序不影响结果，层内会按 id 排序）
     * @param derivationEdges 推导边（[DerivationEdge.childId] 由 [DerivationEdge.parentId] 推导）；
     *                        只有两端都在 [nodeIds] 内的边参与分层，跨章边由母层承载
     */
    fun layout(nodeIds: List<String>, derivationEdges: List<DerivationEdge>): Map<String, GraphPoint> {
        if (nodeIds.isEmpty()) return emptyMap()
        val idSet = nodeIds.toHashSet()
        val parents = HashMap<String, MutableList<String>>()
        nodeIds.forEach { parents[it] = mutableListOf() }
        derivationEdges.forEach { e ->
            if (e.childId in idSet && e.parentId in idSet && e.childId != e.parentId) {
                parents.getValue(e.childId).add(e.parentId)
            }
        }

        val layer = HashMap<String, Int>()
        fun longestParentChain(id: String, stack: MutableSet<String>): Int {
            layer[id]?.let { return it }
            if (id in stack) return 0            // 环保护：遇到回边按 0 截断
            stack.add(id)
            var m = 0
            parents.getValue(id).forEach { p -> m = maxOf(m, longestParentChain(p, stack) + 1) }
            stack.remove(id)
            layer[id] = m
            return m
        }
        nodeIds.forEach { longestParentChain(it, HashSet()) }

        val pos = HashMap<String, GraphPoint>()
        nodeIds.groupBy { layer.getValue(it) }.toSortedMap().forEach { (ly, ids) ->
            val row = ids.sorted()
            val n = row.size
            row.forEachIndexed { j, id ->
                val x = if (n == 1) CENTER_X else CENTER_X + (j - (n - 1) / 2f) * COL_W
                pos[id] = GraphPoint(x, TOP + ly * ROW_H)
            }
        }
        return pos
    }
}
