package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 2 Task 2.3 补全：[ClozeSkeletonBuilder] 公式骨架生成纯函数单测。
 *
 * 覆盖：未填→编号方框 / 已填→实时填入 / 多空编号顺序 / 重复 placeholder 只替首个 /
 * placeholder 不在公式中时原样保留 / 空列表兜底。
 */
class ClozeSkeletonBuilderTest {

    private fun blank(index: Int, answer: String) =
        ClozeItem(index = index, placeholder = answer, options = listOf(answer, "干扰$index"))

    @Test
    fun `单空未填渲染为编号方框 1`() {
        val latex = "P(A)=\\sum P(B_{i})P(A|B_{i})"
        val blanks = listOf(blank(1, "P(B_{i})P(A|B_{i})"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, emptyMap())
        assertEquals("P(A)=\\sum \\boxed{\\,1\\,}", out)
    }

    @Test
    fun `单空已填实时填入所选 latex`() {
        val latex = "P(A)=\\sum P(B_{i})P(A|B_{i})"
        val blanks = listOf(blank(1, "P(B_{i})P(A|B_{i})"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, mapOf(1 to "P(B_{i})P(A|B_{i})"))
        assertEquals("P(A)=\\sum \\boxed{P(B_{i})P(A|B_{i})}", out)
    }

    @Test
    fun `多空按卡片顺序编号 1 2 3`() {
        // 模拟期望与方差三空（aligned 块）
        val latex = "E(aX+b)=aE(X)+b; D(aX+b)=a^{2}D(X); D(X+Y)=D(X)+D(Y)"
        val blanks = listOf(
            blank(1, "aE(X)+b"),
            blank(2, "a^{2}D(X)"),
            blank(3, "D(X)+D(Y)"),
        )
        val out = ClozeSkeletonBuilder.build(latex, blanks, emptyMap())
        assertEquals(
            "E(aX+b)=\\boxed{\\,1\\,}; D(aX+b)=\\boxed{\\,2\\,}; D(X+Y)=\\boxed{\\,3\\,}",
            out
        )
    }

    @Test
    fun `多空部分填入混合显示编号与内容`() {
        val latex = "E(aX+b)=aE(X)+b; D(aX+b)=a^{2}D(X)"
        val blanks = listOf(blank(1, "aE(X)+b"), blank(2, "a^{2}D(X)"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, mapOf(1 to "aE(X)+b"))
        // 空1已填内容，空2仍为编号方框
        assertEquals("E(aX+b)=\\boxed{aE(X)+b}; D(aX+b)=\\boxed{\\,2\\,}", out)
    }

    @Test
    fun `placeholder 重复出现只替换首个`() {
        val latex = "x + x"
        val blanks = listOf(blank(1, "x"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, emptyMap())
        // 只替首个 x，第二个保留
        assertEquals("\\boxed{\\,1\\,} + x", out)
    }

    @Test
    fun `placeholder 不在公式中时原样保留`() {
        val latex = "P(A)=B"
        val blanks = listOf(blank(1, "不存在的片段"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, emptyMap())
        assertEquals("P(A)=B", out)
    }

    @Test
    fun `空挖空列表返回原公式`() {
        val latex = "P(A)=B"
        val out = ClozeSkeletonBuilder.build(latex, emptyList(), emptyMap())
        assertEquals(latex, out)
    }

    @Test
    fun `已填内容含反斜杠不被当作正则替换组`() {
        // newValue 走字面量替换，$ 与 \ 不应触发分组/转义异常
        val latex = "f=PLACEHOLDER"
        val blanks = listOf(blank(1, "PLACEHOLDER"))
        val out = ClozeSkeletonBuilder.build(latex, blanks, mapOf(1 to "\\frac{a}{b}"))
        assertEquals("f=\\boxed{\\frac{a}{b}}", out)
    }
}
