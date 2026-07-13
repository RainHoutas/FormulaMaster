package com.example.formulamaster.domain

import com.example.formulamaster.domain.DiscriminationCardBuilder.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiscriminationCardBuilderTest {

    private fun opt(id: String) = Option(formulaId = id, title = "T-$id", latex = "L-$id")
    private val target = opt("target")

    @Test
    fun `无易混干扰项返回 null`() {
        assertNull(DiscriminationCardBuilder.build(target, "用途", confusables = emptyList()))
    }

    @Test
    fun `干扰项仅含目标自身也返回 null`() {
        assertNull(DiscriminationCardBuilder.build(target, "用途", confusables = listOf(opt("target"))))
    }

    @Test
    fun `正常出卡：目标在选项内且为正确项`() {
        val card = DiscriminationCardBuilder.build(
            target, "用途", confusables = listOf(opt("a"), opt("b")), random = Random(0)
        )!!
        assertEquals("target", card.correctId)
        assertTrue(card.options.any { it.formulaId == "target" })
        assertEquals(3, card.options.size) // 目标 + 2 干扰
    }

    @Test
    fun `干扰项去重 + 剔除目标自身`() {
        val card = DiscriminationCardBuilder.build(
            target, "用途",
            confusables = listOf(opt("a"), opt("a"), opt("target"), opt("b")),
            random = Random(0)
        )!!
        // a 去重为 1、target 剔除 → 目标 + {a,b} = 3
        assertEquals(3, card.options.size)
        assertEquals(1, card.options.count { it.formulaId == "a" })
        assertEquals(1, card.options.count { it.formulaId == "target" })
    }

    @Test
    fun `选项数超上限时截断但始终含目标`() {
        val many = (1..10).map { opt("d$it") }
        val card = DiscriminationCardBuilder.build(
            target, "用途", confusables = many, maxOptions = 4, random = Random(42)
        )!!
        assertEquals(4, card.options.size)
        assertTrue(card.options.any { it.formulaId == "target" })
    }

    @Test
    fun `diffExplanation 空白归一为 null`() {
        val card = DiscriminationCardBuilder.build(
            target, "用途", confusables = listOf(opt("a")), diffExplanation = "   "
        )!!
        assertNull(card.diffExplanation)
    }

    @Test
    fun `diffExplanation 有内容时保留`() {
        val card = DiscriminationCardBuilder.build(
            target, "用途", confusables = listOf(opt("a")), diffExplanation = "前者含条件 x>0"
        )!!
        assertEquals("前者含条件 x>0", card.diffExplanation)
    }

    @Test
    fun `判分：命中正确项为对，其余为错`() {
        val card = DiscriminationCardBuilder.build(
            target, "用途", confusables = listOf(opt("a")), random = Random(0)
        )!!
        assertTrue(DiscriminationCardBuilder.isCorrect(card, "target"))
        assertFalse(DiscriminationCardBuilder.isCorrect(card, "a"))
        assertFalse(DiscriminationCardBuilder.isCorrect(card, null))
    }
}
