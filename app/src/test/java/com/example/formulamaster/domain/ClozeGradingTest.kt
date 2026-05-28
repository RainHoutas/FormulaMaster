package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 2 Task 2.3：[ClozeGrading] 判分纯函数单测。
 *
 * 覆盖：全对→4 / 有错→1 / 漏答按错 / 逐空对错明细 / 空列表兜底。
 */
class ClozeGradingTest {

    private fun blank(index: Int, answer: String) =
        ClozeItem(index = index, placeholder = answer, options = listOf(answer, "干扰$index"))

    @Test
    fun `全对映射 4`() {
        val blanks = listOf(blank(1, "a"), blank(2, "b"))
        val r = ClozeGrading.grade(blanks, mapOf(1 to "a", 2 to "b"))
        assertTrue(r.allCorrect)
        assertEquals(ClozeGrading.RATING_ALL_CORRECT, r.rating)
        assertEquals(4, r.rating)
        assertTrue(r.perBlankCorrect.values.all { it })
    }

    @Test
    fun `任一错映射 1 且逐空明细正确`() {
        val blanks = listOf(blank(1, "a"), blank(2, "b"))
        val r = ClozeGrading.grade(blanks, mapOf(1 to "a", 2 to "干扰2"))
        assertFalse(r.allCorrect)
        assertEquals(ClozeGrading.RATING_ANY_WRONG, r.rating)
        assertEquals(1, r.rating)
        assertTrue(r.perBlankCorrect[1]!!)
        assertFalse(r.perBlankCorrect[2]!!)
    }

    @Test
    fun `漏答某空按错处理`() {
        val blanks = listOf(blank(1, "a"), blank(2, "b"))
        val r = ClozeGrading.grade(blanks, mapOf(1 to "a")) // 空 2 未答
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
        assertFalse(r.perBlankCorrect[2]!!)
    }

    @Test
    fun `全错映射 1`() {
        val blanks = listOf(blank(1, "a"), blank(2, "b"))
        val r = ClozeGrading.grade(blanks, mapOf(1 to "x", 2 to "y"))
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }

    @Test
    fun `单空全对映射 4`() {
        val r = ClozeGrading.grade(listOf(blank(1, "a")), mapOf(1 to "a"))
        assertTrue(r.allCorrect)
        assertEquals(4, r.rating)
    }

    @Test
    fun `空挖空列表视为非全对`() {
        val r = ClozeGrading.grade(emptyList(), emptyMap())
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
        assertTrue(r.perBlankCorrect.isEmpty())
    }
}
