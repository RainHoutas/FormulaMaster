package com.example.formulamaster.domain.graph

import com.example.formulamaster.domain.EntryRelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 4 Task 4.2：图模型装配器 + GraphModel 派生查询校验。
 */
class GraphModelBuilderTest {

    private fun n(id: String, subject: String, chapter: String) =
        GraphNodeInput(id, "公式$id", subject, chapter)

    private val nodes = listOf(
        n("a", "高数", "极限"),
        n("b", "高数", "极限"),
        n("c", "高数", "中值"),
        n("d", "线代", "行列式")
    )
    private val edges = listOf(
        GraphEdge("b", "a", EntryRelationType.CONFUSABLE),  // 同章
        GraphEdge("c", "a", EntryRelationType.DERIVATION),  // 跨章（中值←极限）
        GraphEdge("d", "c", EntryRelationType.SIBLING)      // 跨学科
    )

    @Test
    fun `章节簇按首次出现顺序 且计数正确`() {
        val cs = GraphModelBuilder.clustersOf(nodes)
        assertEquals(listOf("极限", "中值", "行列式"), cs.map { it.chapter })
        assertEquals(2, cs.first { it.chapter == "极限" }.nodeCount)
        assertEquals(1, cs.first { it.chapter == "中值" }.nodeCount)
    }

    @Test
    fun `build 注入状态与顽固标记 缺省为未学非顽固`() {
        val m = GraphModelBuilder.build(
            nodes, edges,
            stateById = mapOf("a" to NodeState.MASTERED, "b" to NodeState.LEARNING),
            leechIds = setOf("b")
        )
        assertEquals(NodeState.MASTERED, m.node("a")!!.state)
        assertEquals(NodeState.LEARNING, m.node("b")!!.state)
        assertEquals("缺省未学", NodeState.NEW, m.node("c")!!.state)
        assertTrue(m.node("b")!!.isLeech)
        assertFalse(m.node("a")!!.isLeech)
    }

    @Test
    fun `idsOf 返回该章节公式`() {
        val m = GraphModelBuilder.build(nodes, edges, emptyMap(), emptySet())
        assertEquals(listOf("a", "b"), m.idsOf(ChapterKey("高数", "极限")))
    }

    @Test
    fun `crossChapterNeighbors 只含跨章邻居 不含同章`() {
        val m = GraphModelBuilder.build(nodes, edges, emptyMap(), emptySet())
        // a 的邻居：b(同章,排除) / c(跨章,保留)
        assertEquals(listOf("c"), m.crossChapterNeighbors("a"))
        // c 的邻居：a(跨章) + d(跨学科)
        assertEquals(setOf("a", "d"), m.crossChapterNeighbors("c").toSet())
    }

    @Test
    fun `NodeState of 派生映射`() {
        assertEquals(NodeState.NEW, NodeState.of(activated = false, learningState = null))
        assertEquals(NodeState.MASTERED, NodeState.of(activated = true, learningState = 3))
        assertEquals(NodeState.LEARNING, NodeState.of(activated = true, learningState = 1))
        assertEquals(NodeState.LEARNING, NodeState.of(activated = true, learningState = 2))
    }
}
