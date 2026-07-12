package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 5：学习阶段配置 + 按距考天数自动建议校验。
 */
class StudyPhaseTest {

    @Test
    fun `fromName 未知或 null 兜底默认一轮`() {
        assertEquals(StudyPhase.OneRound, StudyPhase.fromName(null))
        assertEquals(StudyPhase.OneRound, StudyPhase.fromName("不存在"))
        assertEquals(StudyPhase.Sprint, StudyPhase.fromName("Sprint"))
    }

    @Test
    fun `order 单向递增`() {
        val orders = StudyPhase.entries.map { it.order }
        assertEquals(listOf(1, 2, 3, 4, 5), orders)
    }

    @Test
    fun `新卡上限随阶段递减 冲刺与保持关新卡`() {
        assertTrue(StudyPhase.OneRound.newCardsPerDay > StudyPhase.TwoRound.newCardsPerDay)
        assertTrue(StudyPhase.TwoRound.newCardsPerDay > StudyPhase.ThreeRound.newCardsPerDay)
        assertTrue(StudyPhase.Sprint.newCardsClosed)
        assertTrue(StudyPhase.Maintenance.newCardsClosed)
    }

    @Test
    fun `retention 越高 intervalFactor 越小（复习更勤）`() {
        // 一轮 90%→1.0 基线；冲刺/保持高保留→更小；二轮低保留→更大
        assertEquals(1.0, StudyPhase.OneRound.intervalFactor, 1e-9)
        assertTrue(StudyPhase.TwoRound.intervalFactor > 1.0)          // 88% → 间隔更长
        assertTrue(StudyPhase.Sprint.intervalFactor < 1.0)           // 93% → 更勤
        assertTrue(StudyPhase.Maintenance.intervalFactor < StudyPhase.Sprint.intervalFactor) // 95% 最勤
    }

    @Test
    fun `交错策略随阶段推进`() {
        assertEquals(Interleave.BLOCK, StudyPhase.OneRound.interleave)
        assertEquals(Interleave.WITHIN_CHAPTER, StudyPhase.TwoRound.interleave)
        assertEquals(Interleave.FULL, StudyPhase.ThreeRound.interleave)
        assertEquals(Interleave.FULL, StudyPhase.Sprint.interleave)
    }

    @Test
    fun `suggestedFor 按距考天数分段`() {
        assertEquals(StudyPhase.OneRound, StudyPhase.suggestedFor(200))
        assertEquals(StudyPhase.TwoRound, StudyPhase.suggestedFor(100))
        assertEquals(StudyPhase.ThreeRound, StudyPhase.suggestedFor(45))
        assertEquals(StudyPhase.Sprint, StudyPhase.suggestedFor(20))
        assertEquals(StudyPhase.Maintenance, StudyPhase.suggestedFor(3))
    }

    @Test
    fun `suggestedFor 边界`() {
        assertEquals(StudyPhase.Maintenance, StudyPhase.suggestedFor(6))
        assertEquals(StudyPhase.Sprint, StudyPhase.suggestedFor(7))
        assertEquals(StudyPhase.ThreeRound, StudyPhase.suggestedFor(30))
        assertEquals(StudyPhase.TwoRound, StudyPhase.suggestedFor(60))
        assertEquals(StudyPhase.OneRound, StudyPhase.suggestedFor(150))
    }

    @Test
    fun `无考试日期返回 null 不建议`() {
        assertNull(StudyPhase.suggestedFor(-1))
    }
}
