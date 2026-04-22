package com.example.formulamaster.domain.model

/**
 * 填空题单个挖空项，对应 FormulaEntity.clozeData JSON 数组中的一个元素。
 *
 * JSON 示例：
 * { "index": 1, "placeholder": "\\frac{1}{n!}", "options": ["\\frac{1}{n!}", "\\frac{1}{(n+1)!}", "n!"] }
 *
 * @param index       挖空在公式中的序号（从 1 开始）
 * @param placeholder 正确答案的 LaTeX 字符串
 * @param options     所有候选项（含正确答案和干扰项），顺序由 JSON 决定
 */
data class ClozeItem(
    val index: Int,
    val placeholder: String,
    val options: List<String>
)
