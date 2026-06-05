package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 3 Task 3.2 — C6 题型反查卡判分单测。
 *
 * 锁定用户 2026-06-05 拍板：选对（选中集 == 正确集）→4，其余→1。
 */
class C6GradingTest {

    @Test
    fun `单条正确集 选中唯一正确公式 判对评 4`() {
        val r = C6Grading.grade(selectedFormulaIds = setOf("f1"), correctFormulaIds = setOf("f1"))
        assertTrue(r.allCorrect)
        assertEquals(4, r.rating)
    }

    @Test
    fun `选错公式 判错评 1`() {
        val r = C6Grading.grade(selectedFormulaIds = setOf("f2"), correctFormulaIds = setOf("f1"))
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }

    @Test
    fun `多选时正确项之外多选了一个 判错`() {
        val r = C6Grading.grade(selectedFormulaIds = setOf("f1", "f2"), correctFormulaIds = setOf("f1"))
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }

    @Test
    fun `空选中 判错评 1`() {
        val r = C6Grading.grade(selectedFormulaIds = emptySet(), correctFormulaIds = setOf("f1"))
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }

    @Test
    fun `正确集为空 永不判对 防御兜底`() {
        val r = C6Grading.grade(selectedFormulaIds = emptySet(), correctFormulaIds = emptySet())
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }

    @Test
    fun `多公式正确集 全选中且不多不少 判对（未来一题多公式）`() {
        val r = C6Grading.grade(
            selectedFormulaIds = setOf("f1", "f3"),
            correctFormulaIds = setOf("f1", "f3")
        )
        assertTrue(r.allCorrect)
        assertEquals(4, r.rating)
    }

    @Test
    fun `多公式正确集 漏选一条 判错`() {
        val r = C6Grading.grade(
            selectedFormulaIds = setOf("f1"),
            correctFormulaIds = setOf("f1", "f3")
        )
        assertFalse(r.allCorrect)
        assertEquals(1, r.rating)
    }
}
