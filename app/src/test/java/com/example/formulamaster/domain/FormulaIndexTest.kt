package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.FormulaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习流程重构 Sprint 3 Task 3.3：[FormulaIndex] 分组索引纯函数单测。
 *
 * 覆盖：两级分组 / 未学标记 / chaptersOf·entriesOf 查询 / 首次出现顺序 /
 * entries 按 formulaId 排序 / 空输入 / allEntries 展平。
 */
class FormulaIndexTest {

    private fun f(id: String, subject: String, chapter: String) = FormulaEntity(
        formulaId = id, subject = subject, chapter = chapter,
        title = id, latexCode = "x", clozeData = "[]", derivationSteps = "[]",
        tags = "", difficultyLevel = 1
    )

    @Test
    fun `两级分组 subject 到 chapter 到 entries`() {
        val index = FormulaIndex.build(
            formulas = listOf(
                f("g1", "高数", "极限"),
                f("g2", "高数", "微分"),
                f("p1", "概率论", "随机变量"),
            ),
            learnedFormulaIds = emptySet()
        )
        assertEquals(listOf("高数", "概率论"), index.subjectNames)
        assertEquals(listOf("极限", "微分"), index.chaptersOf("高数"))
        assertEquals(listOf("随机变量"), index.chaptersOf("概率论"))
    }

    @Test
    fun `未学公式 isLearned 为 false`() {
        val index = FormulaIndex.build(
            formulas = listOf(f("g1", "高数", "极限"), f("g2", "高数", "极限")),
            learnedFormulaIds = setOf("g1")
        )
        val entries = index.entriesOf("高数", "极限")
        assertEquals(2, entries.size)
        assertTrue(entries.first { it.formula.formulaId == "g1" }.isLearned)
        assertFalse(entries.first { it.formula.formulaId == "g2" }.isLearned)
    }

    @Test
    fun `entries 按 formulaId 升序排列`() {
        val index = FormulaIndex.build(
            formulas = listOf(
                f("g3", "高数", "极限"),
                f("g1", "高数", "极限"),
                f("g2", "高数", "极限"),
            ),
            learnedFormulaIds = emptySet()
        )
        assertEquals(
            listOf("g1", "g2", "g3"),
            index.entriesOf("高数", "极限").map { it.formula.formulaId }
        )
    }

    @Test
    fun `subject 与 chapter 保留首次出现顺序`() {
        val index = FormulaIndex.build(
            formulas = listOf(
                f("l1", "线代", "矩阵"),
                f("g1", "高数", "极限"),
                f("l2", "线代", "行列式"),
            ),
            learnedFormulaIds = emptySet()
        )
        // 线代 先于 高数（首次出现顺序），线代内 矩阵 先于 行列式
        assertEquals(listOf("线代", "高数"), index.subjectNames)
        assertEquals(listOf("矩阵", "行列式"), index.chaptersOf("线代"))
    }

    @Test
    fun `不存在的 subject 或 chapter 返回空`() {
        val index = FormulaIndex.build(listOf(f("g1", "高数", "极限")), emptySet())
        assertEquals(emptyList<String>(), index.chaptersOf("线代"))
        assertEquals(emptyList<FormulaIndex.Entry>(), index.entriesOf("高数", "微分"))
        assertEquals(emptyList<FormulaIndex.Entry>(), index.entriesOf("线代", "极限"))
    }

    @Test
    fun `空输入返回空索引`() {
        val index = FormulaIndex.build(emptyList(), emptySet())
        assertTrue(index.subjects.isEmpty())
        assertTrue(index.subjectNames.isEmpty())
        assertTrue(index.allEntries().isEmpty())
    }

    @Test
    fun `allEntries 展平所有子科目章节`() {
        val index = FormulaIndex.build(
            formulas = listOf(
                f("g1", "高数", "极限"),
                f("l1", "线代", "矩阵"),
                f("p1", "概率论", "分布"),
            ),
            learnedFormulaIds = setOf("g1", "p1")
        )
        val all = index.allEntries()
        assertEquals(3, all.size)
        assertEquals(2, all.count { it.isLearned })
    }
}
