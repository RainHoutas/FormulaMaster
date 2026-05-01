package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.StudyStateEntity
import java.time.Instant
import java.time.ZoneId

/**
 * FSRS 调度结果
 */
data class SchedulerResult(
    val newDifficulty: Double,    // 更新后难度 D，范围 [1.0, 5.0]
    val newStability: Double,     // 更新后稳定性 S（单位：天）
    val newLearningState: Int,    // 更新后学习状态 0~3
    val nextReviewTime: Long,     // 下次复习时间戳（Unix ms），已截断到当日刷新整点
    val newLapses: Int            // 累计遗忘次数
)

/**
 * FSRS 变体调度器（算法参数：α=0.5，D 范围 [1.0, 5.0]）
 *
 * 算法规则：
 * 1. 难度更新：D_new = clamp(D_old + 0.5 * (3 - R), 1.0, 5.0)
 * 2. 稳定性更新：
 *    - R == 1 → S_new = min(1.0, S_old × 0.2)      （遗忘重置）
 *    - R  > 1 → S_new = S_old × (1 + (R-1) / D_new) （记忆巩固）
 * 3. 测试模式奖励：isTestMode=true 且 R=4 → S_new × 1.5
 * 4. 状态流转：S_new > 30 → learningState = 3（Mastered）
 * 5. 遗忘惩罚：R=1 → lapses++；isTestMode=true 且 R=1 → 强制 learningState = 1
 * 6. nextReviewTime 截断到"目标日 hourOfDay:00（本地时区）"，消除同日复习割裂
 */
object ReviewScheduler {

    private const val DAY_MS = 86_400_000L   // 1 天的毫秒数

    /**
     * 将时间戳 [timeMs] 截断到同一日历天的 [hourOfDay]:[minute]:00（[zoneId] 时区）。
     * 纯字面截断，不做任何"是否过期"判断。
     *
     * 用途：
     * - [calculate] 内部计算 nextReviewTime
     * - 切换每日刷新时刻时，把库内既有 `nextReviewTime` 重新截断（保留原日期）
     */
    fun truncateToRefreshHour(
        timeMs: Long,
        hourOfDay: Int,
        minute: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(timeMs).atZone(zoneId).toLocalDate()
        return date.atTime(hourOfDay, minute, 0).atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * 把"原始 nextReviewTime"截断到刷新时刻，且保证 > [currentTimeMs]。
     * 若刷新时刻已过（极短稳定性场景），顺延到次日同一时刻（DST 安全）。
     */
    fun adjustToRefreshHour(
        rawTimeMs: Long,
        currentTimeMs: Long,
        hourOfDay: Int,
        minute: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        var t = truncateToRefreshHour(rawTimeMs, hourOfDay, minute, zoneId)
        if (t <= currentTimeMs) {
            t = Instant.ofEpochMilli(t).atZone(zoneId).plusDays(1).toInstant().toEpochMilli()
        }
        return t
    }

    fun calculate(
        current: StudyStateEntity,
        rating: Int,                                         // 用户自评：1（忘了）~ 4（太简单）
        isTestMode: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis(),
        hourOfDay: Int = 8,                                  // 每日刷新整点（24h 制），默认 08
        minute: Int = 0,                                     // 每日刷新分钟（0-59），默认 0
        zoneId: ZoneId = ZoneId.systemDefault()             // 时区，单测可注入固定值
    ): SchedulerResult {
        require(rating in 1..4) { "rating 必须在 1..4，实际值：$rating" }

        // ── 1. 难度更新 ───────────────────────────────────────────────────────
        val dNew = (current.difficulty + 0.5 * (3 - rating)).coerceIn(1.0, 5.0)

        // ── 2. 稳定性更新 ─────────────────────────────────────────────────────
        var sNew = if (rating == 1) {
            (current.stability * 0.2).coerceAtMost(1.0)
        } else {
            current.stability * (1.0 + (rating - 1).toDouble() / dNew)
        }

        // ── 3. 测试模式奖励 ───────────────────────────────────────────────────
        if (isTestMode && rating == 4) {
            sNew *= 1.5
        }

        // ── 4. 遗忘次数 ───────────────────────────────────────────────────────
        val newLapses = if (rating == 1) current.lapses + 1 else current.lapses

        // ── 5. 状态流转 ───────────────────────────────────────────────────────
        val newLearningState = when {
            isTestMode && rating == 1 -> 1      // 测试模式遗忘：强制降回 Learning
            sNew > 30.0               -> 3      // 稳定性足够高：晋升 Mastered
            else                      -> current.learningState
        }

        // ── 6. 下次复习时间（截断到当日刷新整点，永不早于 currentTimeMs）──────────
        // 将原始区间截断到目标日 hourOfDay:00，消除同日多次复习的时间割裂
        val rawNextReviewTime = currentTimeMs + (sNew * DAY_MS).toLong()
        val nextReviewTime = adjustToRefreshHour(rawNextReviewTime, currentTimeMs, hourOfDay, minute, zoneId)

        return SchedulerResult(
            newDifficulty    = dNew,
            newStability     = sNew,
            newLearningState = newLearningState,
            nextReviewTime   = nextReviewTime,
            newLapses        = newLapses
        )
    }
}
