package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeParserTest {

    // 与 Project_Spec.md §3.1 示例结构一致
    private val sampleJson = """
        [
          {
            "index": 1,
            "placeholder": "\\frac{1}{n!}",
            "options": ["\\frac{1}{n!}", "\\frac{1}{(n+1)!}", "n!"]
          },
          {
            "index": 2,
            "placeholder": "x^n",
            "options": ["x^n", "x^{n+1}", "nx"]
          }
        ]
    """.trimIndent()

    @Test
    fun `parse returns correct item count`() {
        val result = ClozeParser.parse(sampleJson)
        assertEquals(2, result.size)
    }

    @Test
    fun `parse returns correct placeholder`() {
        val result = ClozeParser.parse(sampleJson)
        assertEquals("\\frac{1}{n!}", result[0].placeholder)
        assertEquals("x^n", result[1].placeholder)
    }

    @Test
    fun `parse returns correct options`() {
        val result = ClozeParser.parse(sampleJson)
        assertEquals(3, result[0].options.size)
        assertTrue(result[0].options.contains("\\frac{1}{n!}"))
        assertTrue(result[0].options.contains("\\frac{1}{(n+1)!}"))
    }

    @Test
    fun `parse returns correct index`() {
        val result = ClozeParser.parse(sampleJson)
        assertEquals(1, result[0].index)
        assertEquals(2, result[1].index)
    }

    @Test
    fun `parse returns empty list for blank input`() {
        assertTrue(ClozeParser.parse("").isEmpty())
        assertTrue(ClozeParser.parse("   ").isEmpty())
    }

    @Test
    fun `parse returns empty list for invalid json`() {
        assertTrue(ClozeParser.parse("not json").isEmpty())
    }
}
