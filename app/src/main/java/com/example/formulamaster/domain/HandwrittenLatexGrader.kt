package com.example.formulamaster.domain

/**
 * 手写作答判定器（Sprint 6.8）：把 OCR 识别出的 LaTeX 与标准答案 LaTeX 做**判等**，
 * 供学习巩固 / 复习识别 / 复习默写在**手写识别模式**下自动判对错（弃纯自评）。
 *
 * 判等策略：两边都过一遍**判等专用规范化** [canonical]（比 [LatexNormalizer.normalize] 更激进：
 * 统一 frac 变体 / 去 `\left\right` / 去间距命令 / 去全部空白 / 收单字符花括号），再逐字符比较。
 * 因为两边用同一套 canonical，只要形态一致即判对——容忍 `\dfrac`vs`\frac`、`x^{2}`vs`x^2`、空格差异等
 * 书写/识别的表层差异。用户 2026-07-16 拍板"先做成自动判断，有问题再改"。
 *
 * 纯函数、无 Android 依赖，可直接单测。
 */
object HandwrittenLatexGrader {

    /** 单个候选是否与答案判等（候选空白直接判否）。 */
    fun isMatch(candidate: String, answer: String): Boolean =
        candidate.isNotBlank() && canonical(candidate) == canonical(answer)

    /** 多候选任一命中即判对（OCR 常返回多个候选）。 */
    fun isMatchAny(candidates: List<String>, answer: String): Boolean =
        candidates.any { isMatch(it, answer) }

    /** 判等专用规范化：对已标准的输入保持稳定；同一输入幂等。 */
    fun canonical(latex: String): String {
        var s = LatexNormalizer.normalize(latex)
        // 统一 frac 变体
        s = s.replace("\\dfrac", "\\frac").replace("\\tfrac", "\\frac")
        // 去 \left \right（括号大小不影响语义相等）
        s = s.replace("\\left", "").replace("\\right", "")
        // 去间距命令：\, \! \; \: \quad \qquad 及反斜杠+空格
        s = s.replace(Regex("""\\[,!;: ]"""), "")
            .replace("\\quad", "").replace("\\qquad", "")
        // 去全部空白
        s = s.replace(Regex("""\s+"""), "")
        // 收单字符花括号：{2}->2、x^{2}->x^2（循环到稳定；两边一致即可，不求 LaTeX 合法）
        val single = Regex("""\{([A-Za-z0-9])\}""")
        var prev: String
        do { prev = s; s = single.replace(s, "$1") } while (s != prev)
        return s
    }
}
