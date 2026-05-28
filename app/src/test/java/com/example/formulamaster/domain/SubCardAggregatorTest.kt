package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.SubCardStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 2 Task 2.6 building block：[SubCardAggregator] 纯函数单测。
 *
 * 覆盖：
 * - 空列表 → 未激活
 * - learningState 三档边界（MIN<1 学习 / AVG>30 已掌握 / 其余 复习）
 * - 结业初始 stability=1.0 的边界（钉住「字面 < 1.0 不命中 → 复习中」当前行为）
 * - nextReviewTime=MIN / lapses=SUM / stability=AVG 派生
 * - deriveAll 分组
 */
class SubCardAggregatorTest {

    private fun card(
        formulaId: String = "f1",
        cardType: String = "c1",
        stability: Double = 1.0,
        nextReviewTime: Long = 1_000L,
        lapses: Int = 0
    ) = SubCardStateEntity(
        formulaId = formulaId,
        cardType = cardType,
        stability = stability,
        difficulty = 3.0,
        lastReviewTime = 0L,
        nextReviewTime = nextReviewTime,
        totalReviews = 0,
        lapses = lapses
    )

    // ── 未激活 ──────────────────────────────────────────────────────────────────

    @Test
    fun `empty list yields NOT_ACTIVATED`() {
        val result = SubCardAggregator.derive(emptyList())
        assertEquals(SubCardAggregator.DerivedProgress.NOT_ACTIVATED, result)
        assertFalse(result.isActivated)
        assertEquals(SubCardAggregator.STATE_NOT_ACTIVATED, result.learningState)
        assertEquals(0.0, result.stability, 0.0)
        assertNull(result.nextReviewTime)
        assertEquals(0, result.lapses)
    }

    // ── learningState 三档 ──────────────────────────────────────────────────────

    @Test
    fun `any sub-card stability below 1 marks learning`() {
        val cards = listOf(
            card(cardType = "c1", stability = 0.5),
            card(cardType = "c2", stability = 50.0),
            card(cardType = "c3", stability = 80.0)
        )
        assertEquals(SubCardAggregator.STATE_LEARNING, SubCardAggregator.derive(cards).learningState)
    }

    @Test
    fun `high average stability with no weak card marks mastered`() {
        val cards = listOf(
            card(cardType = "c1", stability = 40.0),
            card(cardType = "c2", stability = 35.0),
            card(cardType = "c3", stability = 31.0)
        )
        assertEquals(SubCardAggregator.STATE_MASTERED, SubCardAggregator.derive(cards).learningState)
    }

    @Test
    fun `mid stability marks reviewing`() {
        val cards = listOf(
            card(cardType = "c1", stability = 5.0),
            card(cardType = "c2", stability = 10.0),
            card(cardType = "c3", stability = 3.0)
        )
        assertEquals(SubCardAggregator.STATE_REVIEWING, SubCardAggregator.derive(cards).learningState)
    }

    @Test
    fun `weak card dominates even when average exceeds mastered threshold`() {
        // MIN<1 优先级高于 AVG>30：一张极弱卡把整体拉回「学习中」
        val cards = listOf(
            card(cardType = "c1", stability = 0.3),
            card(cardType = "c2", stability = 90.0),
            card(cardType = "c3", stability = 90.0)
        )
        val r = SubCardAggregator.derive(cards)
        assertEquals(SubCardAggregator.STATE_LEARNING, r.learningState)
        assertTrue("AVG 应 > 30", r.stability > 30.0)
    }

    // ── 边界：结业初始 stability=1.0（钉住当前字面行为，待用户拍板）──────────────

    @Test
    fun `freshly graduated cards at exactly 1_0 are reviewing not learning`() {
        // ⚠ 当前严格按 TODO 字面 `MIN < 1.0`：1.0 不小于 1.0 → 不判学习中。
        // 若日后改阈值比较为 `<=`，本用例预期需同步改为 STATE_LEARNING。
        val cards = (1..6).map { card(cardType = "c$it", stability = 1.0) }
        assertEquals(SubCardAggregator.STATE_REVIEWING, SubCardAggregator.derive(cards).learningState)
    }

    @Test
    fun `single sub-card just below threshold is learning`() {
        val cards = listOf(card(stability = 0.999))
        assertEquals(SubCardAggregator.STATE_LEARNING, SubCardAggregator.derive(cards).learningState)
    }

    @Test
    fun `mastered boundary is strict greater than`() {
        // AVG == 30.0 恰好不命中 mastered（> 而非 >=），落到复习中
        val cards = listOf(
            card(cardType = "c1", stability = 30.0),
            card(cardType = "c2", stability = 30.0)
        )
        assertEquals(SubCardAggregator.STATE_REVIEWING, SubCardAggregator.derive(cards).learningState)
    }

    // ── 数值派生 ────────────────────────────────────────────────────────────────

    @Test
    fun `nextReviewTime is the minimum`() {
        val cards = listOf(
            card(cardType = "c1", nextReviewTime = 5_000L),
            card(cardType = "c2", nextReviewTime = 2_000L),
            card(cardType = "c3", nextReviewTime = 9_000L)
        )
        assertEquals(2_000L, SubCardAggregator.derive(cards).nextReviewTime)
    }

    @Test
    fun `lapses is the sum`() {
        val cards = listOf(
            card(cardType = "c1", lapses = 1),
            card(cardType = "c2", lapses = 4),
            card(cardType = "c3", lapses = 2)
        )
        assertEquals(7, SubCardAggregator.derive(cards).lapses)
    }

    @Test
    fun `stability is the average`() {
        val cards = listOf(
            card(cardType = "c1", stability = 2.0),
            card(cardType = "c2", stability = 4.0),
            card(cardType = "c3", stability = 6.0)
        )
        assertEquals(4.0, SubCardAggregator.derive(cards).stability, 1e-9)
    }

    @Test
    fun `single card derives its own values`() {
        val cards = listOf(card(stability = 12.5, nextReviewTime = 777L, lapses = 3))
        val r = SubCardAggregator.derive(cards)
        assertTrue(r.isActivated)
        assertEquals(12.5, r.stability, 1e-9)
        assertEquals(777L, r.nextReviewTime)
        assertEquals(3, r.lapses)
        assertEquals(SubCardAggregator.STATE_REVIEWING, r.learningState)
    }

    // ── deriveAll 分组 ──────────────────────────────────────────────────────────

    @Test
    fun `deriveAll groups by formulaId`() {
        val all = listOf(
            card(formulaId = "fa", cardType = "c1", stability = 0.5),
            card(formulaId = "fa", cardType = "c2", stability = 50.0),
            card(formulaId = "fb", cardType = "c1", stability = 40.0),
            card(formulaId = "fb", cardType = "c2", stability = 40.0)
        )
        val map = SubCardAggregator.deriveAll(all)
        assertEquals(2, map.size)
        assertEquals(SubCardAggregator.STATE_LEARNING, map["fa"]!!.learningState)
        assertEquals(SubCardAggregator.STATE_MASTERED, map["fb"]!!.learningState)
    }

    @Test
    fun `deriveAll on empty returns empty map`() {
        assertTrue(SubCardAggregator.deriveAll(emptyList()).isEmpty())
    }
}
