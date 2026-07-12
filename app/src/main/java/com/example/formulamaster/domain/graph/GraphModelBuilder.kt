package com.example.formulamaster.domain.graph

/**
 * 图模型装配器（Sprint 4 Task 4.2）。纯函数：把最小节点输入 + 已定型的边 + 状态/顽固映射，
 * 组装成 [GraphModel]，并派生章节簇（母层布局所需）。
 *
 * VM/仓库负责实体→输入的映射（公式→[GraphNodeInput]、entry_relations→[GraphEdge]、
 * 子卡聚合→stateById、错题+lapses→leechIds），本装配器不碰 Room / 聚合算法。
 */
object GraphModelBuilder {

    fun build(
        nodes: List<GraphNodeInput>,
        edges: List<GraphEdge>,
        stateById: Map<String, NodeState>,
        leechIds: Set<String>
    ): GraphModel {
        val graphNodes = nodes.map { n ->
            GraphNode(
                id = n.id,
                title = n.title,
                subject = n.subject,
                chapter = n.chapter,
                state = stateById[n.id] ?: NodeState.NEW,
                isLeech = n.id in leechIds
            )
        }
        return GraphModel(graphNodes, edges, clustersOf(nodes))
    }

    /**
     * 按「学科 → 章节」派生章节簇，保持首次出现顺序（母层分带 / 带内排布依赖此顺序稳定）。
     */
    fun clustersOf(nodes: List<GraphNodeInput>): List<ChapterCluster> {
        val order = LinkedHashMap<ChapterKey, Int>()   // key → count
        nodes.forEach { n ->
            val k = ChapterKey(n.subject, n.chapter)
            order[k] = (order[k] ?: 0) + 1
        }
        return order.map { (k, cnt) -> ChapterCluster(k.subject, k.chapter, cnt) }
    }
}
