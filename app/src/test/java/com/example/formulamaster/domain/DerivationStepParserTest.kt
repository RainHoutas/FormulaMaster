package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习流程重构 Sprint 1 Task 1.2 — DerivationStepParser 单测。
 *
 * 覆盖:
 *  - 新格式 [{latex, note}, ...] 正常解析
 *  - 空 / blank 输入返回空列表
 *  - 旧格式(纯字符串数组)兼容降级:返回空列表(不抛)
 *  - 解析错误兜底
 */
class DerivationStepParserTest {

    private val newFormatJson = """
        [
          {"latex": "a_n = \\frac{f^{(n)}(0)}{n!}", "note": "比较系数得通项"},
          {"latex": "", "note": "对 f(x) 在 x=0 处做幂级数展开"}
        ]
    """.trimIndent()

    @Test
    fun `parse returns correct step count`() {
        val result = DerivationStepParser.parse(newFormatJson)
        assertEquals(2, result.size)
    }

    @Test
    fun `parse returns correct latex and note`() {
        val result = DerivationStepParser.parse(newFormatJson)
        assertEquals("a_n = \\frac{f^{(n)}(0)}{n!}", result[0].latex)
        assertEquals("比较系数得通项", result[0].note)
        assertEquals("", result[1].latex)
        assertEquals("对 f(x) 在 x=0 处做幂级数展开", result[1].note)
    }

    @Test
    fun `parse returns empty list for blank input`() {
        assertTrue(DerivationStepParser.parse("").isEmpty())
        assertTrue(DerivationStepParser.parse("   ").isEmpty())
    }

    @Test
    fun `parse returns empty list for invalid json`() {
        assertTrue(DerivationStepParser.parse("not json").isEmpty())
    }

    @Test
    fun `parse legacy string-array format degrades to empty list without throwing`() {
        // 原型阶段格式:纯字符串数组,与新对象数组结构不兼容,应被吞为空列表
        val legacy = """["设 f(x) 在 x=0 处任意阶可导","对 f(x) 在 x=0 处做幂级数展开"]"""
        val result = DerivationStepParser.parse(legacy)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse handles empty array`() {
        assertTrue(DerivationStepParser.parse("[]").isEmpty())
    }
}
