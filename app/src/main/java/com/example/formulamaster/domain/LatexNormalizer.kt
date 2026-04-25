package com.example.formulamaster.domain

/**
 * LaTeX 规范化后处理器（Sprint 1 Task 1.3）
 *
 * 对 OCR 识别器（A1 Mathpix / A2 SimpleTex / 未来端侧模型）输出的原始候选进行规范化，
 * 使其能被 KaTeX 正确渲染，同时过滤明显无效的候选。
 *
 * ## 处理流程（按顺序执行）
 * 1. **Unicode → LaTeX 命令**：将 Unicode 数学符号替换为对应命令（∫→\int, α→\alpha 等）
 * 2. **函数名补全**：将裸函数名加反斜杠（sin x→\sin x，arctan(→\arctan(）
 * 3. **花括号补齐**：统计未闭合的 `{`，在末尾补齐对应数量的 `}`
 * 4. **空格清理**：多余连续空格压缩为单个
 *
 * ## 设计原则
 * - **保守优先**：函数名替换要求后接非字母字符，避免误处理变量名（如 `signal`、`sinusoidal`）
 * - **幂等**：对已规范的标准 LaTeX 调用 `normalize` 不产生副作用
 * - **考研覆盖**：Unicode 映射表覆盖高数、线代、概率统计的常用符号
 *
 * ## 用法
 * ```kotlin
 * val clean = LatexNormalizer.normalize(rawLatex)         // 规范化单条
 * val list  = LatexNormalizer.normalizeAndFilter(rawList) // 批量规范化+过滤
 * ```
 */
object LatexNormalizer {

    /**
     * 规范化单条 LaTeX 字符串。
     * 对已是标准 LaTeX 的输入保持幂等。
     */
    fun normalize(raw: String): String {
        var s = raw.trim()
        s = replaceUnicodeSymbols(s)
        s = normalizeFunctions(s)
        s = fixUnmatchedBraces(s)
        s = cleanupSpaces(s)
        return s
    }

    /**
     * 批量规范化并过滤：对每个候选调用 [normalize]，
     * 再过滤掉明显无效的结果（空白、纯中文等），最后去重。
     */
    fun normalizeAndFilter(candidates: List<String>): List<String> =
        candidates
            .map { normalize(it) }
            .filter { isLikelyFormula(it) }
            .distinct()

    /**
     * 判断字符串是否"可能是数学公式"。
     *
     * 以下情况判定为公式：
     * - 含 LaTeX 结构字符（`\ ^ _ { }`）
     * - 含数学运算符（`+ - = < > / *`）
     * - 含数字
     *
     * 以下情况判定为非公式（过滤掉）：
     * - 空白字符串
     * - 全部由 CJK 汉字/假名/韩文及空白构成（OCR 误识为文字）
     */
    fun isLikelyFormula(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (candidate.all { it.isCjk() || it.isWhitespace() }) return false
        if (candidate.any { it in MATH_STRUCTURE_CHARS }) return true
        if (candidate.any { it in MATH_OPERATOR_CHARS }) return true
        if (candidate.any { it.isDigit() }) return true
        return false
    }

    // ── 私有实现 ──────────────────────────────────────────────────────────

    /**
     * 将 Unicode 数学符号批量替换为对应的 LaTeX 命令。
     * 替换字符串末尾带一个空格（LaTeX 命令分隔要求），后续 [cleanupSpaces] 会处理多余空格。
     */
    private fun replaceUnicodeSymbols(s: String): String {
        var result = s
        UNICODE_TO_LATEX.forEach { (unicode, latex) ->
            result = result.replace(unicode, latex)
        }
        return result
    }

    /**
     * 规范化数学函数名：对**无反斜杠**的函数名前置 `\` 并追加空格。
     *
     * 匹配条件：
     * - 函数名前不能是 `\`（避免重复处理已有的 `\sin`）
     * - 函数名前不能是其他字母（避免误处理 `signal`、`cosine` 等）
     * - 函数名后必须是非字母字符（空格/括号/下划线/数字等）或字符串末尾
     *   （保守策略：`sinx` 不替换，因为 `x` 可能是函数名的一部分）
     *
     * 按长度降序处理，确保 `arcsin` 先于 `sin`、`limsup` 先于 `lim`。
     */
    private fun normalizeFunctions(s: String): String {
        var result = s
        FUNCTION_NAMES.forEach { name ->
            // look-behind: 前面不是 \ 也不是字母
            // look-ahead:  后面不是字母，或是字符串末尾
            val pattern = Regex("(?<![\\\\a-zA-Z])${Regex.escape(name)}(?=[^a-zA-Z]|\$)")
            result = pattern.replace(result) { "\\$name " }
        }
        return result
    }

    /**
     * 补齐未闭合的花括号。
     * 扫描字符串统计 `{` 和 `}` 的差值，在末尾追加缺失的 `}`。
     * 多余的 `}`（即 `}` 多于 `{`）不做处理，保留原样。
     */
    private fun fixUnmatchedBraces(s: String): String {
        var openCount = 0
        for (ch in s) {
            when (ch) {
                '{' -> openCount++
                '}' -> if (openCount > 0) openCount--
            }
        }
        return if (openCount > 0) s + "}".repeat(openCount) else s
    }

    /**
     * 清理多余空格：多个连续空格 → 单个空格，首尾 trim。
     */
    private fun cleanupSpaces(s: String): String =
        s.replace(Regex("\\s{2,}"), " ").trim()

    // ── 字符分类 ──────────────────────────────────────────────────────────

    private val MATH_STRUCTURE_CHARS = setOf('\\', '^', '_', '{', '}')
    private val MATH_OPERATOR_CHARS  = setOf('+', '-', '=', '<', '>', '/', '*')

    private fun Char.isCjk(): Boolean {
        val cp = code
        return cp in 0x4E00..0x9FFF   // CJK 统一汉字
            || cp in 0x3040..0x309F   // 平假名
            || cp in 0x30A0..0x30FF   // 片假名
            || cp in 0xAC00..0xD7AF   // 韩文音节
    }

    // ── 数据表 ────────────────────────────────────────────────────────────

    /**
     * 函数名列表（不含反斜杠）。
     * **按长度降序**，保证长名优先匹配（arcsin 在 sin 之前，limsup 在 lim 之前）。
     */
    private val FUNCTION_NAMES = listOf(
        // 反三角（长优先）
        "arcsin", "arccos", "arctan", "arccot", "arcsec", "arccsc",
        // 双曲（长优先）
        "sinh", "cosh", "tanh", "coth",
        // 三角
        "sin", "cos", "tan", "cot", "sec", "csc",
        // 对数 / 指数
        "ln", "log", "exp",
        // 极限（长优先）
        "limsup", "liminf", "lim",
        // 最值
        "max", "min", "sup", "inf",
        // 线代
        "det", "tr", "rank",
        // 其他
        "arg", "sign", "sgn"
    )

    /**
     * Unicode 数学符号 → LaTeX 命令映射。
     * 覆盖考研高数、线代、概率统计常用符号。
     * 替换值末尾带空格（LaTeX 命令与后续内容的分隔），[cleanupSpaces] 会清理多余空格。
     */
    private val UNICODE_TO_LATEX: Map<String, String> = mapOf(
        // ── 积分 / 求和 / 求积 ──────────────────────────────────────────
        "∫"  to "\\int ",
        "∬"  to "\\iint ",
        "∭"  to "\\iiint ",
        "∮"  to "\\oint ",
        "∑"  to "\\sum ",
        "∏"  to "\\prod ",
        // ── 根号 ────────────────────────────────────────────────────────
        "√"  to "\\sqrt",       // 不加空格，后面通常接 { 或数字
        // ── 箭头 ────────────────────────────────────────────────────────
        "→"  to "\\to ",
        "←"  to "\\leftarrow ",
        "↔"  to "\\leftrightarrow ",
        "⇒"  to "\\Rightarrow ",
        "⟹"  to "\\Rightarrow ",
        "⇔"  to "\\Leftrightarrow ",
        "↑"  to "\\uparrow ",
        "↓"  to "\\downarrow ",
        // ── 比较 / 等价 ─────────────────────────────────────────────────
        "≤"  to "\\leq ",
        "≥"  to "\\geq ",
        "≠"  to "\\neq ",
        "≈"  to "\\approx ",
        "≡"  to "\\equiv ",
        "∝"  to "\\propto ",
        "≪"  to "\\ll ",
        "≫"  to "\\gg ",
        // ── 无穷 / 极限 ─────────────────────────────────────────────────
        "∞"  to "\\infty ",
        // ── 集合 ────────────────────────────────────────────────────────
        "∈"  to "\\in ",
        "∉"  to "\\notin ",
        "⊂"  to "\\subset ",
        "⊆"  to "\\subseteq ",
        "⊃"  to "\\supset ",
        "⊇"  to "\\supseteq ",
        "∪"  to "\\cup ",
        "∩"  to "\\cap ",
        "∅"  to "\\emptyset ",
        "∀"  to "\\forall ",
        "∃"  to "\\exists ",
        // ── 微积分 ──────────────────────────────────────────────────────
        "∂"  to "\\partial ",
        "∇"  to "\\nabla ",
        // ── 算术 ────────────────────────────────────────────────────────
        "·"  to "\\cdot ",
        "×"  to "\\times ",
        "÷"  to "\\div ",
        "±"  to "\\pm ",
        "∓"  to "\\mp ",
        "⊗"  to "\\otimes ",
        "⊕"  to "\\oplus ",
        // ── 省略号 ──────────────────────────────────────────────────────
        "…"  to "\\ldots ",
        "⋯"  to "\\cdots ",
        "⋮"  to "\\vdots ",
        "⋱"  to "\\ddots ",
        // ── 小写希腊字母 ─────────────────────────────────────────────────
        "α"  to "\\alpha ",
        "β"  to "\\beta ",
        "γ"  to "\\gamma ",
        "δ"  to "\\delta ",
        "ε"  to "\\varepsilon ",
        "ζ"  to "\\zeta ",
        "η"  to "\\eta ",
        "θ"  to "\\theta ",
        "ι"  to "\\iota ",
        "κ"  to "\\kappa ",
        "λ"  to "\\lambda ",
        "μ"  to "\\mu ",
        "ν"  to "\\nu ",
        "ξ"  to "\\xi ",
        "π"  to "\\pi ",
        "ρ"  to "\\rho ",
        "σ"  to "\\sigma ",
        "τ"  to "\\tau ",
        "υ"  to "\\upsilon ",
        "φ"  to "\\varphi ",
        "χ"  to "\\chi ",
        "ψ"  to "\\psi ",
        "ω"  to "\\omega ",
        // ── 大写希腊字母 ─────────────────────────────────────────────────
        "Γ"  to "\\Gamma ",
        "Δ"  to "\\Delta ",
        "Θ"  to "\\Theta ",
        "Λ"  to "\\Lambda ",
        "Ξ"  to "\\Xi ",
        "Π"  to "\\Pi ",
        "Σ"  to "\\Sigma ",
        "Υ"  to "\\Upsilon ",
        "Φ"  to "\\Phi ",
        "Ψ"  to "\\Psi ",
        "Ω"  to "\\Omega ",
    )
}
