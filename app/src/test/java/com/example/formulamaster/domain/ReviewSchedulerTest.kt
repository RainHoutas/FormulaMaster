package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.StudyStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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

    // ══════════════════════════════════════════════════════════════════════════
    // 刷新时刻截断：Task 2.2 新增用例
    // ══════════════════════════════════════════════════════════════════════════

    private val UTC = ZoneId.of("UTC")
    private val SHANGHAI = ZoneId.of("Asia/Shanghai")
    private val LOS_ANGELES = ZoneId.of("America/Los_Angeles")

    /** 把 ZonedDateTime 转毫秒戳，方便构造精确时刻 */
    private fun zdt(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int = 0, second: Int = 0,
        zone: ZoneId = UTC
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone)
        .toInstant().toEpochMilli()

    /** 把毫秒戳转为指定时区的小时数（0-23） */
    private fun hourIn(ms: Long, zone: ZoneId) =
        Instant.ofEpochMilli(ms).atZone(zone).hour

    /** 把毫秒戳转为指定时区的分钟数 */
    private fun minuteIn(ms: Long, zone: ZoneId) =
        Instant.ofEpochMilli(ms).atZone(zone).minute

    /** 把毫秒戳转为指定时区的日期（dayOfYear） */
    private fun dayIn(ms: Long, zone: ZoneId) =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    // ── 场景 7：同日两次复习 nextReviewTime 落到同一刷新时刻（UTC，S=7.0）────────
    @Test
    fun `same-day reviews truncated to same refresh time`() {
        val state = makeState(stability = 7.0, difficulty = 3.0)
        // 09:00 UTC
        val morningReview  = zdt(2024, 1, 10, 9,  zone = UTC)
        // 15:00 UTC
        val afternoonReview = zdt(2024, 1, 10, 15, zone = UTC)

        val resultA = ReviewScheduler.calculate(state, rating = 3, currentTimeMs = morningReview,
            hourOfDay = 8, zoneId = UTC)
        val resultB = ReviewScheduler.calculate(state, rating = 3, currentTimeMs = afternoonReview,
            hourOfDay = 8, zoneId = UTC)

        assertEquals(
            "同日复习（09:00 与 15:00 UTC）nextReviewTime 应相同",
            resultA.nextReviewTime, resultB.nextReviewTime
        )
        assertEquals("刷新整点应为 08:00", 8, hourIn(resultA.nextReviewTime, UTC))
        assertEquals("刷新整点秒应为 00", 0, minuteIn(resultA.nextReviewTime, UTC))
    }

    // ── 场景 8：truncateToRefreshHour 在 UTC 时区截断正确 ─────────────────────
    @Test
    fun `truncateToRefreshHour UTC - hour set to refresh hour`() {
        // 2024-01-10 15:30 UTC → 截断到 08:00 同日 UTC
        val input    = zdt(2024, 1, 10, 15, 30, zone = UTC)
        val expected = zdt(2024, 1, 10, 8,       zone = UTC)

        val result = ReviewScheduler.truncateToRefreshHour(input, hourOfDay = 8, zoneId = UTC)

        assertEquals("UTC 截断后应为 08:00 同日", expected, result)
    }

    // ── 场景 9：truncateToRefreshHour 在 Asia/Shanghai（UTC+8）时区截断正确 ────
    @Test
    fun `truncateToRefreshHour Asia-Shanghai - truncation respects UTC offset`() {
        // 2024-01-10 23:30 CST（= 15:30 UTC）→ 仍在 Jan 10 Shanghai 时区
        // 截断目标：2024-01-10 08:00 CST = 2024-01-10 00:00 UTC
        val input    = zdt(2024, 1, 10, 23, 30, zone = SHANGHAI)
        val expected = zdt(2024, 1, 10,  8,      zone = SHANGHAI)

        val result = ReviewScheduler.truncateToRefreshHour(input, hourOfDay = 8, zoneId = SHANGHAI)

        assertEquals("Shanghai 截断后应为 08:00 CST 同日", expected, result)
        // 验证等价的 UTC 绝对时刻
        assertEquals("等价 UTC 应为 00:00", 0, hourIn(result, UTC))
    }

    // ── 场景 10：truncateToRefreshHour 在 America/Los_Angeles（PST=UTC-8）截断正确 ─
    @Test
    fun `truncateToRefreshHour America-Los_Angeles PST - truncation correct`() {
        // 2024-01-10 15:30 UTC = 2024-01-10 07:30 PST（日期仍为 Jan 10 LA）
        // 截断目标：2024-01-10 08:00 PST = 2024-01-10 16:00 UTC
        val inputUtc = zdt(2024, 1, 10, 15, 30, zone = UTC)
        val expected = zdt(2024, 1, 10,  8,      zone = LOS_ANGELES)

        val result = ReviewScheduler.truncateToRefreshHour(inputUtc, hourOfDay = 8, zoneId = LOS_ANGELES)

        assertEquals("LA 截断后应为 08:00 PST 同日", expected, result)
        assertEquals("截断后 LA 小时应为 8", 8, hourIn(result, LOS_ANGELES))
        assertEquals("截断后 UTC 小时应为 16（PST+8）", 16, hourIn(result, UTC))
    }

    // ── 场景 11：跨 DST 边界（美国 2024-03-10 春令时切换）截断不抖动 ────────────
    @Test
    fun `truncateToRefreshHour across DST spring-forward boundary - no jitter`() {
        // 2024-03-10 LA：凌晨 2:00 PST 拨快到 3:00 PDT（PST→PDT，UTC-8→UTC-7）
        // 08:00 PDT 在切换后，是正常存在的时刻
        // 输入：2024-03-10 10:00 PDT（= 17:00 UTC）
        // 截断到：2024-03-10 08:00 PDT（= 15:00 UTC）
        val input    = zdt(2024, 3, 10, 10, zone = LOS_ANGELES)
        val expected = zdt(2024, 3, 10,  8, zone = LOS_ANGELES)

        val result = ReviewScheduler.truncateToRefreshHour(input, hourOfDay = 8, zoneId = LOS_ANGELES)

        assertEquals("DST 切换日截断应为 08:00 PDT", expected, result)
        assertEquals("截断后 LA 小时应为 8（PDT）", 8, hourIn(result, LOS_ANGELES))
        assertEquals("截断后 UTC 小时应为 15（PDT=UTC-7）", 15, hourIn(result, UTC))
    }

    // ── 场景 12：刷新整点已过（极短稳定性）→ 顺延到次日同一时刻 ─────────────────
    @Test
    fun `nextReviewTime never before currentTimeMs when refresh hour already passed`() {
        // currentTimeMs = 10:00 UTC；S_new ≈ 0.2（初始 S=1 遗忘后）
        // raw ≈ 10:00 + 4.8h = 14:48 UTC 同日
        // truncate to 08:00 同日 → 08:00 < 10:00（已过）→ 应顺延到次日 08:00
        val tenAM = zdt(2024, 6, 15, 10, zone = UTC)
        val state  = makeState(stability = 1.0, difficulty = 3.0)   // S_new = 1.0*0.2 = 0.2

        val result = ReviewScheduler.calculate(
            state, rating = 1, currentTimeMs = tenAM, hourOfDay = 8, zoneId = UTC
        )

        assertTrue(
            "nextReviewTime(${result.nextReviewTime}) 应 > currentTimeMs($tenAM)",
            result.nextReviewTime > tenAM
        )
        assertEquals("顺延后应落在次日（Jun 16）", 16, dayIn(result.nextReviewTime, UTC).dayOfMonth)
        assertEquals("顺延后小时应为 08:00", 8, hourIn(result.nextReviewTime, UTC))
    }

    // ── 场景 13：calculate() hourOfDay 参数端到端：整点正确落地 ──────────────────
    @Test
    fun `calculate with custom hourOfDay - nextReviewTime at correct hour`() {
        // hourOfDay = 20（20:00），currentTimeMs = 10:00 UTC，S=7.0
        val tenAM  = zdt(2024, 1, 10, 10, zone = UTC)
        val state  = makeState(stability = 7.0, difficulty = 3.0)

        val result = ReviewScheduler.calculate(
            state, rating = 3, currentTimeMs = tenAM, hourOfDay = 20, zoneId = UTC
        )

        // S_new = 7.0 * (1 + 2/3) ≈ 11.667d → raw ≈ 10:00 + 11.667d ≈ 02:00 on day+12
        // truncate to 20:00 on day+12（与 raw 同日，且 20:00 > 02:00）→ no advance
        assertEquals("刷新整点应为 20:00", 20, hourIn(result.nextReviewTime, UTC))
        assertEquals("刷新分钟应为 00", 0, minuteIn(result.nextReviewTime, UTC))
        assertTrue(
            "nextReviewTime 应晚于 currentTimeMs",
            result.nextReviewTime > tenAM
        )
    }
}
