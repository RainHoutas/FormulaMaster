package com.example.formulamaster.domain.graph

import com.example.formulamaster.domain.EntryRelationType

/**
 * 公式族图谱 · 数据层模型（Sprint 4 Task 4.2，RFC §9.4 D17）。
 *
 * 原子化三层引擎的**数据层**产物：把公式 + 关系 + 学习状态组装成与 Room / Compose 无关的
 * 纯图模型，供布局层（算坐标）与渲染层（画节点/边/状态）消费。
 */

/** 节点学习状态（渲染层据此着色）。顽固为叠加标记，见 [GraphNode.isLeech]，不占此枚举。 */
enum class NodeState {
    /** 未学：无子卡记录（未结业七步仪式）。 */
    NEW,
    /** 学习中 / 复习中：已激活但未达掌握。 */
    LEARNING,
    /** 已掌握。 */
    MASTERED;

    companion object {
        /**
         * 由子卡聚合的 learningState 派生（见 `domain.SubCardAggregator`）：
         * 未激活 → [NEW]；learningState==3（已掌握）→ [MASTERED]；其余（学习中/复习中）→ [LEARNING]。
         */
        fun of(activated: Boolean, learningState: Int?): NodeState = when {
            !activated -> NEW
            learningState == 3 -> MASTERED
            else -> LEARNING
        }
    }
}

/** 图节点 = 一条公式。[subject]/[chapter] 取显示缓存；[isLeech] 顽固叠加标记（🔥 红边）。 */
data class GraphNode(
    val id: String,
    val title: String,
    val subject: String,
    val chapter: String,
    val state: NodeState,
    val isLeech: Boolean
)

/** 图边 = 一条关系。方向与 `entry_relations` 一致（推导：from 由 to 推导）。 */
data class GraphEdge(
    val fromId: String,
    val toId: String,
    val type: EntryRelationType
)

/** 供母层布局的最小节点输入（与 Room 实体解耦）。 */
data class GraphNodeInput(
    val id: String,
    val title: String,
    val subject: String,
    val chapter: String
)

/**
 * 完整图模型：节点（含状态）+ 边 + 章节簇。
 * - [clusters] 供 [ClusterOverviewLayout] 算母层气泡。
 * - 子层某章节的推导边由渲染/VM 从 [edges] 过滤（同章 + 推导型）转 [DerivationEdge]。
 */
data class GraphModel(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val clusters: List<ChapterCluster>
) {
    private val byId: Map<String, GraphNode> by lazy { nodes.associateBy { it.id } }
    fun node(id: String): GraphNode? = byId[id]

    /** 某章节内的公式 id（保持 [nodes] 顺序）。 */
    fun idsOf(key: ChapterKey): List<String> =
        nodes.filter { it.subject == key.subject && it.chapter == key.chapter }.map { it.id }

    /** 跨章关联：与 [id] 相连、但位于其它章节的邻居 id（去重，保持稳定顺序）。 */
    fun crossChapterNeighbors(id: String): List<String> {
        val self = node(id) ?: return emptyList()
        val out = LinkedHashSet<String>()
        edges.forEach { e ->
            val other = when (id) { e.fromId -> e.toId; e.toId -> e.fromId; else -> null } ?: return@forEach
            val on = node(other) ?: return@forEach
            if (on.subject != self.subject || on.chapter != self.chapter) out.add(other)
        }
        return out.toList()
    }
}
