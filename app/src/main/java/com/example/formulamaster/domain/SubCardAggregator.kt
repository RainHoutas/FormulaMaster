package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.SubCardStateEntity

/**
 * Sprint 2 Task 2.6 building block：把一条公式的 6 张子卡状态聚合成「公式整体进度」。
 *
 * 母 [com.example.formulamaster.data.local.entity.StudyStateEntity] 退役后，Memory Tab /
 * Sprint Mode / 通知不再读母卡，而是读 `sub_card_states` 后用本纯函数派生整体视图。
 *
 * **本类是纯函数**（无 Room / 协程依赖），可被单测全覆盖；UI 接线（MemoryViewModel /
 * MemoryScreen / SprintModeManager / DailyReminderWorker）属 Task 2.6 后续步骤，
 * 改变可见行为需真机回归，故与本算法分离。
 *
 * 派生规则（TODO §2.6）：
 * - `learningState`：`MIN(stability) < [LEARNING_STABILITY_THRESHOLD]` → 学习中(1)；
 *   否则 `AVG(stability) > [MASTERED_STABILITY_THRESHOLD]` → 已掌握(3)；其余 → 复习中(2)
 * - `nextReviewTime`：`MIN(sub_cards.nextReviewTime)`
 * - `lapses`：`SUM(sub_cards.lapses)`
 * - `stability`：`AVG(sub_cards.stability)`
 *
 * ⚠ **待用户拍板的边界**：结业时子卡初始 `stability = 1.0`，与 [LEARNING_STABILITY_THRESHOLD]
 * 严格小于号比较时**不命中**，故刚激活的公式整体被判为「复习中(2)」而非「学习中(1)」。
 * 当前严格按 TODO 字面（`< 1.0`）实现；若需"刚激活→学习中"，把阈值比较改为 `<=` 即可
 * （见 [SubCardAggregatorTest] 中钉住此行为的用例）。
 */
object SubCardAggregator {

    /** learningState 语义：见 MemoryScreen.LearningStateChip。 */
    const val STATE_NOT_ACTIVATED = 0
    const val STATE_LEARNING = 1
    const val STATE_REVIEWING = 2
    const val STATE_MASTERED = 3

    /** MIN(stability) 低于此值视为「学习中」。TODO §2.6 字面值 1.0。 */
    const val LEARNING_STABILITY_THRESHOLD = 1.0

    /** AVG(stability) 高于此值视为「已掌握」。TODO §2.6 字面值 30.0（天）。 */
    const val MASTERED_STABILITY_THRESHOLD = 30.0

    /**
     * 一条公式的派生整体进度。
     *
     * @property isActivated   是否已激活（拥有至少一张子卡）
     * @property learningState 0 未激活 / 1 学习中 / 2 复习中 / 3 已掌握
     * @property stability     子卡 stability 均值（未激活为 0.0）
     * @property nextReviewTime 子卡最早到期时间；未激活为 null
     * @property lapses        子卡 lapses 之和
     */
    data class DerivedProgress(
        val isActivated: Boolean,
        val learningState: Int,
        val stability: Double,
        val nextReviewTime: Long?,
        val lapses: Int
    ) {
        companion object {
            val NOT_ACTIVATED = DerivedProgress(
                isActivated = false,
                learningState = STATE_NOT_ACTIVATED,
                stability = 0.0,
                nextReviewTime = null,
                lapses = 0
            )
        }
    }

    /**
     * 把某条公式的子卡列表聚合成 [DerivedProgress]。
     * 空列表（未激活）返回 [DerivedProgress.NOT_ACTIVATED]。
     *
     * 调用方负责保证传入的子卡同属一条公式（本函数不校验 formulaId）。
     */
    fun derive(subCards: List<SubCardStateEntity>): DerivedProgress {
        if (subCards.isEmpty()) return DerivedProgress.NOT_ACTIVATED

        val minStability = subCards.minOf { it.stability }
        val avgStability = subCards.sumOf { it.stability } / subCards.size
        val minNextReview = subCards.minOf { it.nextReviewTime }
        val totalLapses = subCards.sumOf { it.lapses }

        val state = when {
            minStability < LEARNING_STABILITY_THRESHOLD -> STATE_LEARNING
            avgStability > MASTERED_STABILITY_THRESHOLD -> STATE_MASTERED
            else -> STATE_REVIEWING
        }

        return DerivedProgress(
            isActivated = true,
            learningState = state,
            stability = avgStability,
            nextReviewTime = minNextReview,
            lapses = totalLapses
        )
    }

    /**
     * 批量版：把全量子卡按 formulaId 分组后逐组派生。
     * 返回 `formulaId -> DerivedProgress`，未激活的公式不在 map 中（调用方按缺键当未激活处理）。
     */
    fun deriveAll(allSubCards: List<SubCardStateEntity>): Map<String, DerivedProgress> =
        allSubCards.groupBy { it.formulaId }
            .mapValues { (_, cards) -> derive(cards) }
}
