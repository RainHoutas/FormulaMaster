package com.example.formulamaster.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Sprint 1 Task 1.3 — LatexNormalizer 单元测试
 * Done 标准：20+ 条规范化用例全部通过
 */
class LatexNormalizerTest {

    // ── 1. Unicode → LaTeX 命令 ─────────────────────────────────────────

    @Test fun `积分符号转换`() {
        // ∫ → \int（带空格）+ 后续内容
        val result = LatexNormalizer.normalize("∫x dx")
        assertEquals("\\int x dx", result)
    }

    @Test fun `带上下限积分转换`() {
        val result = LatexNormalizer.normalize("∫_0^1 x dx")
        assertEquals("\\int _0^1 x dx", result)
    }

    @Test fun `求和符号转换`() {
        val result = LatexNormalizer.normalize("∑_{n=1}^{∞}")
        // ∑→\sum , ∞→\infty , 末尾 } 补齐（花括号计数：{ 2 个，} 1 个，补 1 个）
        // 不对，∑_{n=1}^{∞} 中 { 共 2 个，} 共 1 个（^{ 后的 } 算一个）
        // wait: ∑_{n=1}^{∞} → \sum _{n=1}^{\infty }
        // { 计数：{ 在 ^{ 处 = 1，} 在 ∞ 后 = 1，均衡
        // 其实 ∑_{n=1}^{∞} → 中花括号：^{ 是一个 {，最后 } 是一个 } → 平衡
        assertEquals("\\sum _{n=1}^{\\infty }", result)
    }

    @Test fun `偏微分符号转换`() {
        val result = LatexNormalizer.normalize("∂f/∂x")
        assertEquals("\\partial f/\\partial x", result)
    }

    @Test fun `无穷大转换`() {
        val result = LatexNormalizer.normalize("x→∞")
        assertEquals("x\\to \\infty", result)
    }

    @Test fun `小写希腊字母转换`() {
        val result = LatexNormalizer.normalize("α+β")
        assertEquals("\\alpha +\\beta", result)
    }

    @Test fun `大写希腊字母转换`() {
        val result = LatexNormalizer.normalize("ΣΩ")
        assertEquals("\\Sigma \\Omega", result)
    }

    @Test fun `不等号转换`() {
        val result = LatexNormalizer.normalize("a≤b≥c")
        assertEquals("a\\leq b\\geq c", result)
    }

    @Test fun `乘法点积转换`() {
        val result = LatexNormalizer.normalize("a·b")
        assertEquals("a\\cdot b", result)
    }

    @Test fun `根号转换`() {
        // √ → \sqrt（不加空格，后面通常接 { 或数字）
        val result = LatexNormalizer.normalize("√{x+1}")
        assertEquals("\\sqrt{x+1}", result)
    }

    @Test fun `集合符号转换`() {
        val result = LatexNormalizer.normalize("x∈A∪B")
        assertEquals("x\\in A\\cup B", result)
    }

    // ── 2. 函数名规范化 ───────────────────────────────────────────────────

    @Test fun `sin函数名前有空格时补反斜杠`() {
        // "sin x" → "\sin x"（sin 后面是空格，符合匹配条件）
        val result = LatexNormalizer.normalize("sin x")
        assertTrue("应含 \\sin，实际：$result", result.contains("\\sin"))
    }

    @Test fun `cos函数名前有括号时补反斜杠`() {
        val result = LatexNormalizer.normalize("cos(x)")
        assertTrue("应含 \\cos，实际：$result", result.contains("\\cos"))
    }

    @Test fun `ln函数名后有括号时补反斜杠`() {
        val result = LatexNormalizer.normalize("ln(x+1)")
        assertTrue("应含 \\ln，实际：$result", result.contains("\\ln"))
    }

    @Test fun `arctan函数名规范化`() {
        val result = LatexNormalizer.normalize("arctan(x)")
        assertTrue("应含 \\arctan，实际：$result", result.contains("\\arctan"))
    }

    @Test fun `arcsin优先于sin匹配`() {
        // arcsin( 不能被拆成 arc + \sin(
        val result = LatexNormalizer.normalize("arcsin(x)")
        assertTrue("应含 \\arcsin，实际：$result", result.contains("\\arcsin"))
        // 不能出现孤立的 \sin（已被 arcsin 整体匹配）
        assertFalse("不应出现单独的 \\\\sin（只能有 \\\\arcsin）", result.contains("arc\\sin"))
    }

    @Test fun `lim下标场景规范化`() {
        // lim_{x→0}：lim 后面是 _（非字母），匹配
        val result = LatexNormalizer.normalize("lim_{x→0}")
        assertTrue("应含 \\lim，实际：$result", result.contains("\\lim"))
    }

    @Test fun `det行列式规范化`() {
        val result = LatexNormalizer.normalize("det(A)")
        assertTrue("应含 \\det，实际：$result", result.contains("\\det"))
    }

    @Test fun `已有反斜杠的函数名不重复处理`() {
        // 幂等：\sin 不会变成 \\sin 或 \\\sin
        val input = "\\sin x + \\cos x"
        val result = LatexNormalizer.normalize(input)
        assertEquals(input, result)
    }

    // ── 3. 花括号补齐 ─────────────────────────────────────────────────────

    @Test fun `单个未闭合花括号末尾补齐`() {
        val result = LatexNormalizer.normalize("\\frac{1}{2")
        assertEquals("\\frac{1}{2}", result)
    }

    @Test fun `两个未闭合花括号补齐`() {
        val result = LatexNormalizer.normalize("\\frac{a+b}{c")
        assertEquals("\\frac{a+b}{c}", result)
    }

    @Test fun `已平衡花括号不改变`() {
        val input = "\\frac{1}{2}"
        assertEquals(input, LatexNormalizer.normalize(input))
    }

    @Test fun `多余闭合括号不补开括号`() {
        // 多余的 } 不处理（公式本身有误，不自动补 {）
        val input = "x^{2}}"
        val result = LatexNormalizer.normalize(input)
        assertFalse("不应在开头补 {", result.startsWith("{"))
        assertTrue("原内容应保留", result.contains("x^{2}}"))
    }

    // ── 4. 空格清理 ───────────────────────────────────────────────────────

    @Test fun `多余连续空格压缩为单个`() {
        val result = LatexNormalizer.normalize("x  +  y")
        assertEquals("x + y", result)
    }

    @Test fun `首尾空格去除`() {
        val result = LatexNormalizer.normalize("  x^2  ")
        assertEquals("x^2", result)
    }

    // ── 5. 有效性判断（isLikelyFormula） ─────────────────────────────────

    @Test fun `空字符串判定为非公式`() {
        assertFalse(LatexNormalizer.isLikelyFormula(""))
        assertFalse(LatexNormalizer.isLikelyFormula("   "))
    }

    @Test fun `纯中文判定为非公式`() {
        assertFalse(LatexNormalizer.isLikelyFormula("这不是公式"))
    }

    @Test fun `含LaTeX命令判定为公式`() {
        assertTrue(LatexNormalizer.isLikelyFormula("\\int_0^1 x dx"))
    }

    @Test fun `含数字判定为公式`() {
        assertTrue(LatexNormalizer.isLikelyFormula("x+1=0"))
    }

    @Test fun `含运算符判定为公式`() {
        assertTrue(LatexNormalizer.isLikelyFormula("a=b"))
    }

    // ── 6. 批量规范化过滤（normalizeAndFilter） ──────────────────────────

    @Test fun `批量过滤掉空字符串和中文`() {
        val candidates = listOf(
            "∫x dx",
            "",
            "这不是公式",
            "\\frac{1}{2"
        )
        val result = LatexNormalizer.normalizeAndFilter(candidates)
        assertEquals("应保留 2 条", 2, result.size)
        assertTrue("应有 \\int", result.any { it.contains("\\int") })
        assertTrue("花括号应补齐", result.any { it == "\\frac{1}{2}" })
    }

    @Test fun `批量去重`() {
        // 两个相同的原始字符串（规范化后相同），应只保留一条
        val candidates = listOf("∫x dx", "∫x dx", "x^2")
        val result = LatexNormalizer.normalizeAndFilter(candidates)
        assertEquals("去重后应有 2 条", 2, result.size)
    }

    // ── 7. 幂等性（已规范公式不改变） ────────────────────────────────────

    @Test fun `标准积分公式幂等`() {
        val input = "\\int_0^1 x^2 \\, dx"
        assertEquals(input, LatexNormalizer.normalize(input))
    }

    @Test fun `标准分式幂等`() {
        val input = "\\frac{d}{dx}f(x)"
        assertEquals(input, LatexNormalizer.normalize(input))
    }

    @Test fun `标准求和公式幂等`() {
        val input = "\\sum_{n=1}^{\\infty} \\frac{1}{n^2}"
        assertEquals(input, LatexNormalizer.normalize(input))
    }
}
