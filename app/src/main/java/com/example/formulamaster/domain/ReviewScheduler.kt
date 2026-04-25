package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.StudyStateEntity

/**
 * FSRS 调度结果
 */
data class SchedulerResult(
    val newDifficulty: Double,    // 更新后难度 D，范围 [1.0, 5.0]
    val newStability: Double,     // 更新后稳定性 S（单位：天）
    val newLearningState: Int,    // 更新后学习状态 0~3
    val nextReviewTime: Long,     // 下次复习时间戳（Unix ms）
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
 */
object ReviewScheduler {

    private const val DAY_MS = 86_400_000L   // 1 天的毫秒数

    fun calculate(
        current: StudyStateEntity,
        rating: Int,                                         // 用户自评：1（忘了）~ 4（太简单）
        isTestMode: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis()
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

        // ── 6. 下次复习时间 ───────────────────────────────────────────────────
        val nextReviewTime = currentTimeMs + (sNew * DAY_MS).toLong()

        return SchedulerResult(
            newDifficulty    = dNew,
            newStability     = sNew,
            newLearningState = newLearningState,
            nextReviewTime   = nextReviewTime,
            newLapses        = newLapses
        )
    }
}
