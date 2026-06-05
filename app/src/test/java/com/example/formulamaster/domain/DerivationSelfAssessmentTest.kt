package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sprint 3 Task 3.1 — C4 推导卡三档自评 → FSRS 评分映射单测。
 *
 * 锁定用户 2026-06-05 拍板的 1/2/4 映射，防止后续误改档位。
 */
class DerivationSelfAssessmentTest {

    @Test
    fun `不会 maps to rating 1`() {
        assertEquals(1, DerivationSelfAssessment.CANNOT_RECALL.rating)
    }

    @Test
    fun `查看了 maps to rating 2`() {
        assertEquals(2, DerivationSelfAssessment.VIEWED.rating)
    }

    @Test
    fun `推出来了 maps to rating 4 跳过 Good`() {
        assertEquals(4, DerivationSelfAssessment.DERIVED.rating)
    }

    @Test
    fun `三档恰好三个且评分严格递增`() {
        val ratings = DerivationSelfAssessment.entries.map { it.rating }
        assertEquals(listOf(1, 2, 4), ratings)
    }

    @Test
    fun `有意跳过评分 3 Good`() {
        val ratings = DerivationSelfAssessment.entries.map { it.rating }.toSet()
        assert(3 !in ratings) { "C4 三档不应包含评分 3（高难度卡，查看与推出之间不设中间档）" }
    }

    @Test
    fun `label 与档位语义对应`() {
        assertEquals("不会", DerivationSelfAssessment.CANNOT_RECALL.label)
        assertEquals("查看了", DerivationSelfAssessment.VIEWED.label)
        assertEquals("推出来了", DerivationSelfAssessment.DERIVED.label)
    }
}
