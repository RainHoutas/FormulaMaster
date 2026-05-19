package com.example.formulamaster.domain.model

/**
 * 填空题单个挖空项，对应 FormulaEntity.clozeData JSON 数组中的一个元素。
 *
 * Sprint 1 Task 1.4：新增 [weight] 和 [mustBlank] 字段，支持加权抽样和强制挖空。
 *
 * JSON 示例（新版，含可选权重）：
 * { "index": 1, "placeholder": "\\frac{f^{(n)}(0)}{n!}", "options": [...],
 *   "weight": 5, "mustBlank": true }
 *
 * 兼容旧 JSON：缺 [weight] 视为 1，缺 [mustBlank] 视为 false。
 *
 * @param index       挖空在公式中的序号（从 1 开始）
 * @param placeholder 正确答案的 LaTeX 字符串
 * @param options     所有候选项（含正确答案和干扰项），顺序由 JSON 决定
 * @param weight      抽样权重（≥1），用于 C2 加权 cloze 卡。默认 1。
 * @param mustBlank   强制挖空标志：true 时无视权重抽样必入选，用于关键位置。
 */
data class ClozeItem(
    val index: Int,
    val placeholder: String,
    val options: List<String>,
    val weight: Int = 1,
    val mustBlank: Boolean = false
)
