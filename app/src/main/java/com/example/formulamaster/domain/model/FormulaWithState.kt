package com.example.formulamaster.domain.model

import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.domain.LeechDetector
import com.example.formulamaster.domain.SubCardAggregator.DerivedProgress

/**
 * 公式 + 学习状态的聚合视图对象，供 UI 层使用。
 *
 * Sprint 2 Task 2.6（2026-05-29）：母卡 study_states 退役，状态改由**子卡聚合**派生。
 * [derived] 为 null 表示该公式尚未激活（无任何 sub_card_states 记录 = 未结业七步仪式）。
 *
 * Sprint 3 Task 3.4：[recentErrorMarks] = 近 7 日被错题反向标记的去重错题条数，
 * 与 lapses 共同决定 [isLeech]（判定逻辑外提到 [LeechDetector]，UI 不再内联阈值）。
 */
data class FormulaWithState(
    val formula: FormulaEntity,
    val derived: DerivedProgress? = null,
    val recentErrorMarks: Int = 0
) {
    val lapses: Int get() = derived?.lapses ?: 0
    val learningState: Int get() = derived?.learningState ?: 0
    val isActivated: Boolean get() = derived != null

    /** 顽固节点：累计遗忘达阈值 或 近 7 日被错题高频标记。 */
    val isLeech: Boolean get() = LeechDetector.isLeech(lapses, recentErrorMarks)
}
