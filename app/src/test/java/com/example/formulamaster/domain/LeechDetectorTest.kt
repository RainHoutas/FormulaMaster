package com.example.formulamaster.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习流程重构 Sprint 3 Task 3.4：[LeechDetector] 两条 leech 触发路径纯函数单测。
 */
class LeechDetectorTest {

    @Test
    fun `lapses 达阈值判 leech（错题标记为 0）`() {
        assertTrue(LeechDetector.isLeech(lapses = 4, recentErrorMarks = 0))
        assertTrue(LeechDetector.isLeech(lapses = 8, recentErrorMarks = 0))
    }

    @Test
    fun `lapses 未达阈值且无错题标记 不判 leech`() {
        assertFalse(LeechDetector.isLeech(lapses = 3, recentErrorMarks = 0))
        assertFalse(LeechDetector.isLeech(lapses = 0, recentErrorMarks = 1))
    }

    @Test
    fun `错题标记达阈值判 leech（lapses 未到）`() {
        assertTrue(LeechDetector.isLeech(lapses = 0, recentErrorMarks = 2))
        assertTrue(LeechDetector.isLeech(lapses = 1, recentErrorMarks = 3))
    }

    @Test
    fun `错题标记差一次不判 leech`() {
        assertFalse(LeechDetector.isLeech(lapses = 3, recentErrorMarks = 1))
    }

    @Test
    fun `两条路径同时满足仍判 leech`() {
        assertTrue(LeechDetector.isLeech(lapses = 5, recentErrorMarks = 3))
    }

    @Test
    fun `阈值边界 lapses 恰为 4 与错题恰为 2`() {
        assertTrue(LeechDetector.isLeech(lapses = LeechDetector.LAPSE_THRESHOLD, recentErrorMarks = 0))
        assertTrue(LeechDetector.isLeech(lapses = 0, recentErrorMarks = LeechDetector.ERROR_MARK_THRESHOLD))
        assertFalse(LeechDetector.isLeech(lapses = LeechDetector.LAPSE_THRESHOLD - 1, recentErrorMarks = LeechDetector.ERROR_MARK_THRESHOLD - 1))
    }
}
