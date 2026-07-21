package com.example.formulamaster.domain

/**
 * 复习子卡「空值驱动可出性」判定（Sprint 6.5，用户拍板 2026-07-21）。
 *
 * 规则：某卡型只在该公式**有对应数据**时才出现；没有数据 → 剔除该卡（不回落成通用"看答案"，
 * 也不凑空题面/无干扰项）。若一条公式所有卡型都无数据 → 该公式整体退出本次复习。
 *
 * 纯函数、无 Room / Android 依赖，可直接单测。数据存在性由调用方（[com.example.formulamaster.ui.viewmodel.RouterReviewViewModel]）
 * 从 `FormulaEntity` 字段解析后以 [FormulaData] 传入，保持本判定器与数据层解耦。
 */
object ReviewCardAvailability {

    /**
     * 某公式各卡型所依赖数据的存在性快照。
     * @param hasCloze          clozeData 有可挖空项（C2）
     * @param hasPreconditions  preconditions 非空（C3 条件先行的"条件"）
     * @param hasDerivation     derivationSteps 非空（C4）
     * @param hasPurpose        purpose 非空（C5 题干线索）
     * @param hasConfusable     有易混邻居（CONFUSABLE 边，C5 干扰项来源）
     * @param hasTypicalProblems typicalProblems 非空（C6 题面）
     * @param chapterPoolSize   同章公式数含自身（C6 候选池，需 ≥2 才有干扰项）
     */
    data class FormulaData(
        val hasCloze: Boolean,
        val hasPreconditions: Boolean,
        val hasDerivation: Boolean,
        val hasPurpose: Boolean,
        val hasConfusable: Boolean,
        val hasTypicalProblems: Boolean,
        val chapterPoolSize: Int,
    )

    /** 单卡型是否可出。C1 识别只需公式本体（恒有）故恒 true。 */
    fun isAvailable(cardType: CardType, d: FormulaData): Boolean = when (cardType) {
        CardType.C1_Recognition    -> true
        CardType.C2_Cloze          -> d.hasCloze
        CardType.C3_Precondition   -> d.hasPreconditions
        CardType.C4_Derivation     -> d.hasDerivation
        CardType.C5_Discrimination -> d.hasConfusable && d.hasPurpose
        CardType.C6_TypicalProblem -> d.chapterPoolSize >= 2 && d.hasTypicalProblems
    }

    /** 该公式当前可出的全部卡型（空集 = 该公式无卡可考，应整体剔除）。 */
    fun availableCards(d: FormulaData): Set<CardType> =
        CardType.entries.filter { isAvailable(it, d) }.toSet()
}
