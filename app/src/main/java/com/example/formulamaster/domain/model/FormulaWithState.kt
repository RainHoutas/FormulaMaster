package com.example.formulamaster.domain.model

import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.StudyStateEntity

/**
 * 公式 + 学习状态的聚合视图对象，供 UI 层使用。
 * studyState 为 null 表示该公式尚未激活学习。
 */
data class FormulaWithState(
    val formula: FormulaEntity,
    val studyState: StudyStateEntity? = null
) {
    val lapses: Int get() = studyState?.lapses ?: 0
    val learningState: Int get() = studyState?.learningState ?: 0
    val isActivated: Boolean get() = studyState != null
}
