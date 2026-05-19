package com.example.formulamaster.domain

/**
 * 6 类子卡（学习流程重构 RFC D10）：每张子卡独立 FSRS 状态。
 *
 * [code] 是落库时存 [com.example.formulamaster.data.local.entity.SubCardStateEntity.cardType]
 * 字段的字符串，不要更名，否则旧记录将无法匹配。
 */
enum class CardType(val code: String) {
    /** 识别卡：题面 → 公式名 */
    C1_Recognition("c1"),

    /** 加权 Cloze 卡：公式挖空（按 weight × 错次加权） */
    C2_Cloze("c2"),

    /** 条件先行卡：先给条件 → 匹配公式 */
    C3_Precondition("c3"),

    /** 推导卡：按推导步骤逐步重构 */
    C4_Derivation("c4"),

    /** 易混卡：N 选 1 区分相近公式 */
    C5_Discrimination("c5"),

    /** 题型反查卡：题型 → 应使用的公式 */
    C6_TypicalProblem("c6");

    companion object {
        fun fromCode(code: String): CardType? = entries.firstOrNull { it.code == code }
    }
}
