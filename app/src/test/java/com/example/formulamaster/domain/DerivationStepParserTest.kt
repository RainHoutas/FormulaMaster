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

    // ── Sprint 1 Task 1.8：边界补强 ────────────────────────────────────────────

    @Test
    fun `parse preserves step order across many steps`() {
        val json = """
            [
              {"latex": "step_1", "note": "n1"},
              {"latex": "step_2", "note": "n2"},
              {"latex": "step_3", "note": "n3"},
              {"latex": "step_4", "note": "n4"},
              {"latex": "step_5", "note": "n5"}
            ]
        """.trimIndent()
        val result = DerivationStepParser.parse(json)
        assertEquals(5, result.size)
        result.forEachIndexed { idx, s ->
            assertEquals("step_${idx + 1}", s.latex)
            assertEquals("n${idx + 1}", s.note)
        }
    }

    @Test
    fun `parse field missing fills nullable defaults`() {
        // Gson 对 data class 反射构造时，缺 note 字段会填 null（Kotlin String? 兜底）
        val json = """[{"latex": "no_note_here"}]"""
        val result = DerivationStepParser.parse(json)
        assertEquals(1, result.size)
        assertEquals("no_note_here", result[0].latex)
        // note 可为 null 或 ""，仅断言 latex 解析正确
    }

    @Test
    fun `parse rejects single object (not array) returns empty`() {
        // 单个对象而非数组：Gson 反序列化失败 → 空列表
        val json = """{"latex": "x", "note": "n"}"""
        assertTrue(DerivationStepParser.parse(json).isEmpty())
    }

    @Test
    fun `parse handles malformed nested json gracefully`() {
        // 半截 JSON 异常被吞为空列表，不抛
        assertTrue(DerivationStepParser.parse("[{\"latex\": \"x\"").isEmpty())
        assertTrue(DerivationStepParser.parse("[{,}]").isEmpty())
    }
}
