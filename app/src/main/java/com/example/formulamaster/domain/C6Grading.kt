package com.example.formulamaster.domain

/**
 * 学习流程重构 Sprint 3 Task 3.2 — C6 题型反查卡判分。
 *
 * 看一道题面 → 从同章节公式池**多选**该题该用哪条公式 → 系统自动判对错（用户不自评）。
 *
 * 用户 2026-06-05 拍板：选对→4 / 选错→1。判对的定义是**选中集恰好等于正确集**
 * （多选不漏不多）。当前数据每道题面只映射到「所属公式」一条，故正确集恒为单条；
 * 多选 UI + 集合判等是为未来「一题多公式」留口子，逻辑无需改动。
 *
 * 纯函数（无 Android 依赖），与 [ClozeGrading] 同构，单测覆盖。
 */
object C6Grading {

    const val RATING_CORRECT = 4
    const val RATING_WRONG = 1

    data class Result(val allCorrect: Boolean, val rating: Int)

    /**
     * @param selectedFormulaIds 用户选中的公式 id 集合
     * @param correctFormulaIds  该题面的正确公式 id 集合（当前恒为单条）
     * @return 选中集恰好等于非空正确集 → allCorrect=true / rating=4；否则 false / rating=1
     */
    fun grade(selectedFormulaIds: Set<String>, correctFormulaIds: Set<String>): Result {
        val allCorrect = correctFormulaIds.isNotEmpty() && selectedFormulaIds == correctFormulaIds
        return Result(allCorrect, if (allCorrect) RATING_CORRECT else RATING_WRONG)
    }
}
