package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem

/**
 * C2 加权 cloze 卡的「公式骨架」生成器（Task 2.3 真机验收补全，2026-05-29）。
 *
 * 把公式 [latexCode] 里被抽中的挖空位置替换成可渲染的洞，交给 KaTeX 显示，
 * 让用户**看着公式骨架**作答，而不是凭空选部件（对齐 RFC §2「展开公式 → cloze 候选填空」）。
 *
 * 标记规则（用户拍板 2026-05-29：编号方框 + 实时填入）：
 * - **未填**：`\boxed{i}`，i 为该空在卡片中的序号（从 1 起，与下方「空 1/2/3」一一对应）
 * - **已填**：`\boxed{<用户所选 latex>}`，实时填入预览，所见即所得
 *
 * 实现注意：
 * - 用 [String.replaceFirst]（字面量，非正则）逐个替换，避免 placeholder 在公式中
 *   多次出现时被 [String.replace] 全量替换；
 * - [blanks] 的列表顺序即「空 1/2/3」的编号顺序（与 C2ClozePane 的 forEachIndexed 对齐）；
 * - placeholder 必须是 [latexCode] 的子串才会被替换（种子数据已校验，见 FormulaSeedIntegrationTest）。
 */
object ClozeSkeletonBuilder {

    /**
     * @param latexCode  公式完整 LaTeX 源码
     * @param blanks     本卡抽中的挖空列表（顺序 = 空 1/2/3 编号）
     * @param selections 用户当前选择：blankIndex（[ClozeItem.index]）→ 已选 latex；未选则不在 map 中
     * @return 带洞（或带已填内容）的骨架 LaTeX
     */
    fun build(
        latexCode: String,
        blanks: List<ClozeItem>,
        selections: Map<Int, String>,
    ): String {
        var result = latexCode
        blanks.forEachIndexed { i, blank ->
            val label = i + 1
            val filled = selections[blank.index]
            val inner = filled ?: "\\,$label\\,"
            result = result.replaceFirst(blank.placeholder, "\\boxed{$inner}")
        }
        return result
    }

    /**
     * 判分态骨架（Sprint 3 Task 3.5，用户拍板 2026-06-05）：提交后顶部骨架每个方框**填正确答案**，
     * 并用 `\colorbox` 整框上色——答对绿底 / 答错红底，一眼看出哪个位置错了。
     *
     * 前景色用 `\textcolor` 锁死（深绿 / 深红），不依赖 KaTeX 模板注入的主题色，
     * 避免暗色模式下浅底配浅字看不清（真机验收重点核对对比度）。
     *
     * @param perBlankCorrect index（[ClozeItem.index]）→ 该空是否答对；缺失视为答错（保守）
     */
    fun buildGraded(
        latexCode: String,
        blanks: List<ClozeItem>,
        perBlankCorrect: Map<Int, Boolean>,
    ): String {
        var result = latexCode
        blanks.forEach { blank ->
            val correct = perBlankCorrect[blank.index] == true
            val bg = if (correct) COLOR_CORRECT_BG else COLOR_WRONG_BG
            val fg = if (correct) COLOR_CORRECT_FG else COLOR_WRONG_FG
            // \colorbox 给整框底色；内部 \textcolor 锁前景，\boxed 保留方框边框视觉
            val inner = "\\colorbox{$bg}{\$\\textcolor{$fg}{\\boxed{${blank.placeholder}}}\$}"
            result = result.replaceFirst(blank.placeholder, inner)
        }
        return result
    }

    // 判分态配色（浅底 + 深字，自洽不依赖主题色）
    private const val COLOR_CORRECT_BG = "#C8E6C9"  // 浅绿
    private const val COLOR_CORRECT_FG = "#1B5E20"  // 深绿
    private const val COLOR_WRONG_BG = "#FFCDD2"     // 浅红
    private const val COLOR_WRONG_FG = "#B71C1C"     // 深红
}
