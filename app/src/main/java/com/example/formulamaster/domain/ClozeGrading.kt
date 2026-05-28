package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem

/**
 * Sprint 2 Task 2.3：C2 加权 cloze 卡判分（纯函数）。
 *
 * 用户为每个挖空从 chip 候选里选一个 → 系统拿选项与 [ClozeItem.placeholder] 精确比对
 * → 映射成 FSRS 评分。`options` 中的正确项字符串与 `placeholder` 一致（数据约定）。
 *
 * 评分映射（用户拍板 2026-05-28）：**全对 → 4，任一错/漏答 → 1**。
 *
 * ⚠ 待校准：chip 为多选识别（比自由回忆易），全对给 4(Easy) 可能偏宽；
 *   若日后觉得太松，把 [RATING_ALL_CORRECT] 改为 3(Good) 即可（单测会同步提示）。
 */
object ClozeGrading {

    const val RATING_ALL_CORRECT = 4
    const val RATING_ANY_WRONG = 1

    /**
     * @property perBlankCorrect index → 该空是否答对
     * @property allCorrect      是否全对（空列表视为非全对）
     * @property rating          映射后的 FSRS 评分（1 或 4）
     */
    data class Result(
        val perBlankCorrect: Map<Int, Boolean>,
        val allCorrect: Boolean,
        val rating: Int
    )

    /**
     * @param blanks     本轮挖空（每个含正确 [ClozeItem.placeholder]）
     * @param selections index → 用户所选 option 字符串；缺失（未答）按错处理
     */
    fun grade(blanks: List<ClozeItem>, selections: Map<Int, String>): Result {
        val perBlank = blanks.associate { it.index to (selections[it.index] == it.placeholder) }
        val allCorrect = blanks.isNotEmpty() && perBlank.values.all { it }
        return Result(
            perBlankCorrect = perBlank,
            allCorrect = allCorrect,
            rating = if (allCorrect) RATING_ALL_CORRECT else RATING_ANY_WRONG
        )
    }
}
