package com.example.formulamaster.domain.model

/**
 * 公式「拆块」单块（Sprint 6.2 · 七步仪式 Step 2 拆块阅读）。
 * 对应 [com.example.formulamaster.data.local.entity.FormulaEntity.chunks] JSON 数组的一个元素。
 *
 * 把整条公式拆成若干**有意义的片段**，逐块讲解含义，帮用户理解结构而非死记整体。
 * 与 [DerivationStep] 同构（`{latex, note}`）。
 *
 * JSON 示例：
 * `{ "latex": "\\sum_{i=1}^{n}", "note": "对所有原因 Bᵢ（样本空间的划分）求和" }`
 *
 * 字段语义：
 * - [latex]：该块的 LaTeX 片段（公式的一部分）；允许空字符串（纯文字说明块）
 * - [note]：该块的中文讲解，必填（否则拆块无意义）
 */
data class FormulaChunk(
    val latex: String,
    val note: String
)
