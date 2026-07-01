package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习流程重构 Sprint 3 Task 3.4：[ErrorMarkTally] 近 7 日错题标记去重计数纯函数单测。
 */
class ErrorMarkTallyTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `窗口内多条错题分别计数`() {
        val reports = listOf(
            now - day to listOf("f1", "f2"),
            now - 2 * day to listOf("f1"),
        )
        val counts = ErrorMarkTally.countRecent(reports, now)
        assertEquals(2, counts["f1"])
        assertEquals(1, counts["f2"])
    }

    @Test
    fun `超过 7 日窗口的错题不计入`() {
        val reports = listOf(
            now - 8 * day to listOf("f1"),   // 窗外
            now - 1 * day to listOf("f1"),   // 窗内
        )
        assertEquals(1, ErrorMarkTally.countRecent(reports, now)["f1"])
    }

    @Test
    fun `同一条错题内重复列同公式只计 1`() {
        val reports = listOf(now - day to listOf("f1", "f1", "f1"))
        assertEquals(1, ErrorMarkTally.countRecent(reports, now)["f1"])
    }

    @Test
    fun `恰在窗口边界的错题计入`() {
        val reports = listOf(now - LeechDetector.ERROR_MARK_WINDOW_MS to listOf("f1"))
        assertEquals(1, ErrorMarkTally.countRecent(reports, now)["f1"])
    }

    @Test
    fun `未被标记的公式不在 map 中`() {
        val counts = ErrorMarkTally.countRecent(listOf(now to listOf("f1")), now)
        assertNull(counts["f_other"])
        assertFalse(counts.containsKey("f_other"))
    }

    @Test
    fun `空错题列表返回空 map`() {
        assertEquals(emptyMap<String, Int>(), ErrorMarkTally.countRecent(emptyList(), now))
    }

    @Test
    fun `达阈值的公式配合 LeechDetector 判 leech`() {
        val reports = listOf(
            now - day to listOf("f1"),
            now - 2 * day to listOf("f1"),
        )
        val marks = ErrorMarkTally.countRecent(reports, now)["f1"] ?: 0
        assertEquals(2, marks)
        assertTrue(LeechDetector.isLeech(lapses = 0, recentErrorMarks = marks))
    }
}
