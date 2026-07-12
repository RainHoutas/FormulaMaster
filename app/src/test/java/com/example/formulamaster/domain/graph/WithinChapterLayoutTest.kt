package com.example.formulamaster.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 4 Task 4.2：子层块内分层布局校验——确定性 + 父上子下 + 环保护 + 跨簇边忽略。
 */
class WithinChapterLayoutTest {

    @Test
    fun `空输入返回空`() {
        assertTrue(WithinChapterLayout.layout(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `单节点居中`() {
        val pos = WithinChapterLayout.layout(listOf("a"), emptyList())
        assertEquals(1, pos.size)
        assertEquals(200f, pos.getValue("a").x)
    }

    @Test
    fun `推导链父上子下——层递增 y 递增`() {
        // b 由 a 推导，c 由 b 推导 → a(层0) 在上，c(层2) 在下
        val edges = listOf(DerivationEdge("b", "a"), DerivationEdge("c", "b"))
        val pos = WithinChapterLayout.layout(listOf("a", "b", "c"), edges)
        assertTrue("父应在子之上", pos.getValue("a").y < pos.getValue("b").y)
        assertTrue("父应在子之上", pos.getValue("b").y < pos.getValue("c").y)
    }

    @Test
    fun `确定性——两次布局一致`() {
        val ids = listOf("x", "a", "m", "b")
        val edges = listOf(DerivationEdge("b", "a"))
        assertEquals(
            WithinChapterLayout.layout(ids, edges),
            WithinChapterLayout.layout(ids, edges)
        )
    }

    @Test
    fun `跨簇边被忽略——引用簇外 id 不影响不崩`() {
        val pos = WithinChapterLayout.layout(listOf("a", "b"), listOf(DerivationEdge("a", "外部公式")))
        // 外部父被忽略 → a 仍是第 0 层
        assertEquals(pos.getValue("a").y, pos.getValue("b").y)
    }

    @Test
    fun `同层多节点横向分开且按 id 排序`() {
        val pos = WithinChapterLayout.layout(listOf("z", "a"), emptyList())
        assertEquals("同层应同 y", pos.getValue("a").y, pos.getValue("z").y)
        assertTrue("a 应排在 z 左侧（按 id 升序）", pos.getValue("a").x < pos.getValue("z").x)
    }

    @Test
    fun `推导成环不无限递归`() {
        // a 由 b 推导、b 由 a 推导（人为环）——环保护应截断，不栈溢出
        val edges = listOf(DerivationEdge("a", "b"), DerivationEdge("b", "a"))
        val pos = WithinChapterLayout.layout(listOf("a", "b"), edges)
        assertEquals(2, pos.size)
    }
}
