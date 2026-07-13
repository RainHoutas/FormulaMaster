package com.example.formulamaster.domain

import com.example.formulamaster.domain.LearningItemVisibility.LearningItem
import com.example.formulamaster.domain.LearningItemVisibility.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningItemVisibilityTest {

    @Test
    fun `毙掉 → 隐藏（即便有内容也隐藏）`() {
        assertEquals(Visibility.HIDDEN, LearningItemVisibility.decide(isExcluded = true, hasContent = true))
        assertEquals(Visibility.HIDDEN, LearningItemVisibility.decide(isExcluded = true, hasContent = false))
    }

    @Test
    fun `未毙 + 有内容 → 显示`() {
        assertEquals(Visibility.SHOWN, LearningItemVisibility.decide(isExcluded = false, hasContent = true))
    }

    @Test
    fun `未毙 + 空 → 占位（不误伤为隐藏）`() {
        assertEquals(Visibility.PLACEHOLDER, LearningItemVisibility.decide(isExcluded = false, hasContent = false))
    }

    @Test
    fun `parseExcluded 容错：null 空白 非法都归空集`() {
        assertTrue(LearningItemVisibility.parseExcluded(null).isEmpty())
        assertTrue(LearningItemVisibility.parseExcluded("   ").isEmpty())
        assertTrue(LearningItemVisibility.parseExcluded("不是数组").isEmpty())
    }

    @Test
    fun `parseExcluded 正常解析 + 过滤空串`() {
        assertEquals(setOf("derivation", "mnemonic"),
            LearningItemVisibility.parseExcluded("[\"derivation\",\"\",\"mnemonic\"]"))
    }

    @Test
    fun `decideAll 综合场景：毙推导 + 口诀空未填 + 用途有内容`() {
        val excluded = LearningItemVisibility.parseExcluded("[\"derivation\"]")
        val result = LearningItemVisibility.decideAll(
            excludedKeys = excluded,
            contentByItem = mapOf(
                LearningItem.PURPOSE to true,       // 有内容
                LearningItem.MNEMONIC to false      // 空、未毙
                // DERIVATION 不给内容也无所谓，已毙
            )
        )
        assertEquals(Visibility.HIDDEN, result[LearningItem.DERIVATION])       // 毙掉
        assertEquals(Visibility.SHOWN, result[LearningItem.PURPOSE])           // 有内容
        assertEquals(Visibility.PLACEHOLDER, result[LearningItem.MNEMONIC])    // 空未毙 → 占位
        // 缺项默认无内容 → 占位（保守，不误伤成隐藏）
        assertEquals(Visibility.PLACEHOLDER, result[LearningItem.TYPICAL_PROBLEM])
    }

    @Test
    fun `decideAll 覆盖全部板块`() {
        val result = LearningItemVisibility.decideAll(emptySet(), emptyMap())
        assertEquals(LearningItem.entries.size, result.size)
    }

    @Test
    fun `fromKey 往返一致`() {
        LearningItem.entries.forEach {
            assertEquals(it, LearningItem.fromKey(it.key))
        }
        assertEquals(null, LearningItem.fromKey("不存在"))
    }
}
