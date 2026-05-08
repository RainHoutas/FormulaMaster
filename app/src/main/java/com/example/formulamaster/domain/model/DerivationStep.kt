package com.example.formulamaster.domain.model

/**
 * 学习流程重构 Sprint 1 Task 1.2 — 推导链单步,对应 [com.example.formulamaster.data.local.entity.FormulaEntity.derivationSteps]
 * JSON 数组中的一个元素。Sprint 2 Task 2.5 的 FormulaDetail 推导链 + Sprint 3 Task 3.1 的 C4 推导卡均消费此结构。
 *
 * JSON 示例:
 * `{ "latex": "a_n = \\frac{f^{(n)}(0)}{n!}", "note": "比较系数得通项" }`
 *
 * 字段语义:
 * - [latex]: 该步的 LaTeX 表达式;允许空字符串(纯文字描述步)
 * - [note]: 该步的中文注释 / 解释,必填(否则 C4 推导卡只能"念 LaTeX")
 *
 * 用 D9 决策 = B(对象数组重写),取代原型阶段纯 LaTeX 字符串数组格式。
 */
data class DerivationStep(
    val latex: String,
    val note: String
)
