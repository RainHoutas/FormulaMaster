package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.SubCardStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Sprint 5：学习阶段 intervalFactor 对复习间隔的缩放校验。
 */
class ReviewSchedulerIntervalFactorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = 1_700_000_000_000L   // 固定时刻

    private fun card() = SubCardStateEntity(
        formulaId = "f", cardType = "c1",
        stability = 10.0, difficulty = 3.0,
        lastReviewTime = now, nextReviewTime = now,
        totalReviews = 3, lapses = 0
    )

    private fun daysOut(intervalFactor: Double): Double {
        val r = ReviewScheduler.calculate(
            current = card(), rating = 3, isTestMode = false,
            currentTimeMs = now, hourOfDay = 8, minute = 0, zoneId = zone,
            intervalFactor = intervalFactor
        )
        return (r.nextReviewTime - now).toDouble() / 86_400_000L
    }

    @Test
    fun `intervalFactor 越大 下次复习越远`() {
        val base = daysOut(1.0)
        val longer = daysOut(1.21)     // 二轮
        val shorter = daysOut(0.49)    // 保持
        assertTrue("1.21× 应比基线远", longer > base)
        assertTrue("0.49× 应比基线近", shorter < base)
    }

    @Test
    fun `基线 1_0 与不传参一致`() {
        val explicit = ReviewScheduler.calculate(
            current = card(), rating = 3, currentTimeMs = now,
            hourOfDay = 8, minute = 0, zoneId = zone, intervalFactor = 1.0
        ).nextReviewTime
        val default = ReviewScheduler.calculate(
            current = card(), rating = 3, currentTimeMs = now,
            hourOfDay = 8, minute = 0, zoneId = zone
        ).nextReviewTime
        assertEquals(default, explicit)
    }
}
