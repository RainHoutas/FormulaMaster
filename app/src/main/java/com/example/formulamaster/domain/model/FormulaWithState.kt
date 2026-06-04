package com.example.formulamaster.domain.model

import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.domain.SubCardAggregator.DerivedProgress

/**
 * 公式 + 学习状态的聚合视图对象，供 UI 层使用。
 *
 * Sprint 2 Task 2.6（2026-05-29）：母卡 study_states 退役，状态改由**子卡聚合**派生。
 * [derived] 为 null 表示该公式尚未激活（无任何 sub_card_states 记录 = 未结业七步仪式）。
 */
data class FormulaWithState(
    val formula: FormulaEntity,
    val derived: DerivedProgress? = null
) {
    val lapses: Int get() = derived?.lapses ?: 0
    val learningState: Int get() = derived?.learningState ?: 0
    val isActivated: Boolean get() = derived != null
}
