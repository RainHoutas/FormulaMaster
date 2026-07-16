package com.example.formulamaster.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwrittenLatexGraderTest {

    @Test fun `完全相同判对`() =
        assertTrue(HandwrittenLatexGrader.isMatch("x^2+1", "x^2+1"))

    @Test fun `dfrac 与 frac 视为相同`() =
        assertTrue(HandwrittenLatexGrader.isMatch("\\dfrac{1}{2}", "\\frac{1}{2}"))

    @Test fun `上标花括号可省略`() =
        assertTrue(HandwrittenLatexGrader.isMatch("x^{2}", "x^2"))

    @Test fun `空格差异不影响`() =
        assertTrue(HandwrittenLatexGrader.isMatch("\\sin  x", "\\sin x"))

    @Test fun `left right 括号与普通括号相等`() =
        assertTrue(HandwrittenLatexGrader.isMatch("\\left(a+b\\right)", "(a+b)"))

    @Test fun `间距命令被忽略`() =
        assertTrue(HandwrittenLatexGrader.isMatch("a\\,+\\,b", "a+b"))

    @Test fun `不同公式判错`() =
        assertFalse(HandwrittenLatexGrader.isMatch("x^2+1", "x^2-1"))

    @Test fun `大小写有意义不可混`() =
        assertFalse(HandwrittenLatexGrader.isMatch("X", "x"))

    @Test fun `空白候选判否`() =
        assertFalse(HandwrittenLatexGrader.isMatch("   ", "x"))

    @Test fun `多候选任一命中即对`() {
        assertTrue(HandwrittenLatexGrader.isMatchAny(listOf("乱识别", "\\dfrac{1}{2}"), "\\frac{1}{2}"))
        assertFalse(HandwrittenLatexGrader.isMatchAny(listOf("a", "b"), "c"))
    }

    @Test fun `canonical 幂等`() {
        val once = HandwrittenLatexGrader.canonical("\\dfrac{x^{2}}{2}")
        assertTrue(once == HandwrittenLatexGrader.canonical(once))
    }
}
