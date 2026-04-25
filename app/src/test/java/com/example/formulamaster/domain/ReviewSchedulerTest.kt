package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.StudyStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {

    // ── 测试用固定时间戳，避免 System.currentTimeMillis() 不确定性 ──────────────
    private val NOW = 1_700_000_000_000L

    /**
     * 构造测试用 StudyStateEntity，只暴露算法关心的字段。
     */
    private fun makeState(
        stability: Double = 5.0,
        difficulty: Double = 3.0,
        learningState: Int = 2,
        lapses: Int = 0
    ) = StudyStateEntity(
        formulaId            = "test_formula",
        learningState        = learningState,
        stability            = stability,
        difficulty           = difficulty,
        lastReviewTime       = 0L,
        nextReviewTime       = 0L,
        totalReviews         = 0,
        lapses               = lapses,
        consecutiveGoodReviews = 0
    )

    // ── 场景 1：R=1 遗忘 ──────────────────────────────────────────────────────
    @Test
    fun `R=1 forgetting - stability decreases and lapses increments`() {
        val state = makeState(stability = 5.0, lapses = 0)
        val result = ReviewScheduler.calculate(state, rating = 1, currentTimeMs = NOW)

        assertTrue(
            "遗忘后 S_new(${result.newStability}) 应 < S_old(${state.stability})",
            result.newStability < state.stability
        )
        assertEquals(
            "遗忘后 lapses 应 +1",
            state.lapses + 1,
            result.newLapses
        )
        // S_new = min(1.0, 5.0 * 0.2) = 1.0
        assertEquals(1.0, result.newStability, 1e-9)
    }

    // ── 场景 2：R=4 极易 ──────────────────────────────────────────────────────
    @Test
    fun `R=4 easy - stability increases and difficulty decreases`() {
        val state = makeState(stability = 5.0, difficulty = 3.0)
        val result = ReviewScheduler.calculate(state, rating = 4, currentTimeMs = NOW)

        assertTrue(
            "极易后 S_new(${result.newStability}) 应 > S_old(${state.stability})",
            result.newStability > state.stability
        )
        assertTrue(
            "极易后 D_new(${result.newDifficulty}) 应 < D_old(${state.difficulty})",
            result.newDifficulty < state.difficulty
        )
        // D_new = 3.0 + 0.5*(3-4) = 2.5；S_new = 5.0 * (1 + 3/2.5) = 11.0
        assertEquals(2.5,  result.newDifficulty, 1e-9)
        assertEquals(11.0, result.newStability,  1e-9)
    }

    // ── 场景 3：高稳定性 R=3 → 自动晋升 Mastered ─────────────────────────────
    @Test
    fun `S_old=35 R=3 - learningState auto-promoted to Mastered`() {
        val state = makeState(stability = 35.0, difficulty = 3.0, learningState = 2)
        val result = ReviewScheduler.calculate(state, rating = 3, currentTimeMs = NOW)

        // S_new = 35.0 * (1 + 2/3.0) ≈ 58.3 > 30 → Mastered
        assertTrue("S_new 应 > 30，实际：${result.newStability}", result.newStability > 30.0)
        assertEquals(
            "learningState 应自动变为 3（Mastered）",
            3, result.newLearningState
        )
    }

    // ── 场景 4：测试模式奖励 R=4 → S_new 约为普通模式 1.5 倍 ─────────────────
    @Test
    fun `test mode R=4 - stability is 1_5x normal mode`() {
        val state = makeState(stability = 5.0, difficulty = 3.0)
        val normal   = ReviewScheduler.calculate(state, rating = 4, isTestMode = false, currentTimeMs = NOW)
        val testMode = ReviewScheduler.calculate(state, rating = 4, isTestMode = true,  currentTimeMs = NOW)

        assertEquals(
            "测试模式 S_new 应是普通模式的 1.5 倍",
            1.5,
            testMode.newStability / normal.newStability,
            1e-9
        )
    }

    // ── 场景 5：测试模式 R=1 → 强制 learningState 降回 1 ────────────────────
    @Test
    fun `test mode R=1 - learningState forced back to Learning`() {
        // 从 Mastered(3) 状态出发
        val state = makeState(stability = 40.0, learningState = 3)
        val result = ReviewScheduler.calculate(state, rating = 1, isTestMode = true, currentTimeMs = NOW)

        assertEquals(
            "测试模式遗忘应强制 learningState = 1（Learning）",
            1, result.newLearningState
        )
        assertEquals("lapses 应 +1", state.lapses + 1, result.newLapses)
    }

    // ── 场景 6：难度边界不越界 ────────────────────────────────────────────────
    @Test
    fun `difficulty clamps within 1_0 to 5_0`() {
        // 下界：D=1.0，R=4 → D_new 不能低于 1.0
        val lowerState = makeState(difficulty = 1.0)
        val lowerResult = ReviewScheduler.calculate(lowerState, rating = 4, currentTimeMs = NOW)
        assertEquals(
            "难度下界应钳制到 1.0",
            1.0, lowerResult.newDifficulty, 1e-9
        )

        // 上界：D=5.0，R=1 → D_new 不能高于 5.0
        val upperState = makeState(difficulty = 5.0)
        val upperResult = ReviewScheduler.calculate(upperState, rating = 1, currentTimeMs = NOW)
        assertEquals(
            "难度上界应钳制到 5.0",
            5.0, upperResult.newDifficulty, 1e-9
        )
    }
}
