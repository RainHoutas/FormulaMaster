package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 5：复习会话交错策略校验（BLOCK / WITHIN_CHAPTER / FULL）。
 */
class SessionInterleaverTest {

    // 3 章：A(a1,a2,a3) B(b1,b2) C(c1)
    private val order = listOf("a1", "a2", "a3", "b1", "b2", "c1")
    private val chapterOf = mapOf(
        "a1" to "A", "a2" to "A", "a3" to "A",
        "b1" to "B", "b2" to "B", "c1" to "C"
    )

    @Test
    fun `BLOCK 同章连续`() {
        val r = SessionInterleaver.interleave(order, chapterOf, Interleave.BLOCK)
        // 章块连续：A 的三个相邻、B 的两个相邻
        assertEquals(listOf("a1", "a2", "a3", "b1", "b2", "c1"), r)
    }

    @Test
    fun `FULL 跨章 round-robin 连续元素尽量不同章`() {
        val r = SessionInterleaver.interleave(order, chapterOf, Interleave.FULL)
        // 首轮各章各取一个：A,B,C
        assertEquals(setOf("a1", "b1", "c1"), r.take(3).toSet())
        // 相邻元素多数不同章（round-robin 特性）
        var sameAdjacent = 0
        for (i in 1 until r.size) if (chapterOf[r[i]] == chapterOf[r[i - 1]]) sameAdjacent++
        assertTrue("全交错应尽量减少同章相邻", sameAdjacent <= 2)
    }

    @Test
    fun `WITHIN_CHAPTER 章仍成块但章内顺序被打散`() {
        val r = SessionInterleaver.interleave(order, chapterOf, Interleave.WITHIN_CHAPTER)
        // 章块仍连续（A 三个在一起、B 两个在一起）
        val chapters = r.map { chapterOf[it] }
        assertEquals(listOf("A", "A", "A", "B", "B", "C"), chapters)
        // A 章内集合不变
        assertEquals(setOf("a1", "a2", "a3"), r.take(3).toSet())
    }

    @Test
    fun `确定性 同输入同输出`() {
        listOf(Interleave.BLOCK, Interleave.WITHIN_CHAPTER, Interleave.FULL).forEach { m ->
            assertEquals(
                SessionInterleaver.interleave(order, chapterOf, m),
                SessionInterleaver.interleave(order, chapterOf, m)
            )
        }
    }

    @Test
    fun `所有模式都是原集合的排列 不增不减`() {
        listOf(Interleave.BLOCK, Interleave.WITHIN_CHAPTER, Interleave.FULL).forEach { m ->
            assertEquals(order.toSet(), SessionInterleaver.interleave(order, chapterOf, m).toSet())
            assertEquals(order.size, SessionInterleaver.interleave(order, chapterOf, m).size)
        }
    }

    @Test
    fun `单元素或空 原样返回`() {
        assertEquals(listOf("x"), SessionInterleaver.interleave(listOf("x"), mapOf("x" to "A"), Interleave.FULL))
        assertEquals(emptyList<String>(), SessionInterleaver.interleave(emptyList(), emptyMap(), Interleave.BLOCK))
    }
}
