package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClozeParserTest {

    // ── 旧 JSON 兼容 ────────────────────────────────────────────────────────

    private val legacyJson = """
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
    fun `parse legacy returns correct item count`() {
        val result = ClozeParser.parse(legacyJson)
        assertEquals(2, result.size)
    }

    @Test
    fun `parse legacy fills weight default to 1`() {
        val result = ClozeParser.parse(legacyJson)
        result.forEach { assertEquals(1, it.weight) }
    }

    @Test
    fun `parse legacy fills mustBlank default to false`() {
        val result = ClozeParser.parse(legacyJson)
        result.forEach { assertFalse(it.mustBlank) }
    }

    @Test
    fun `parse legacy preserves placeholder and options`() {
        val result = ClozeParser.parse(legacyJson)
        assertEquals("\\frac{1}{n!}", result[0].placeholder)
        assertEquals(3, result[0].options.size)
        assertTrue(result[0].options.contains("\\frac{1}{(n+1)!}"))
    }

    @Test
    fun `parse blank returns empty`() {
        assertTrue(ClozeParser.parse("").isEmpty())
        assertTrue(ClozeParser.parse("   ").isEmpty())
    }

    @Test
    fun `parse invalid json returns empty`() {
        assertTrue(ClozeParser.parse("not json").isEmpty())
    }

    // ── 新 JSON（带 weight/mustBlank）─────────────────────────────────────

    private val newJson = """
        [
          {
            "index": 1,
            "placeholder": "\\frac{f^{(n)}(0)}{n!}",
            "options": [],
            "weight": 5,
            "mustBlank": true
          },
          {
            "index": 2,
            "placeholder": "x^n",
            "options": [],
            "weight": 2
          },
          {
            "index": 3,
            "placeholder": "dx",
            "options": [],
            "mustBlank": false
          }
        ]
    """.trimIndent()

    @Test
    fun `parse new format reads weight`() {
        val result = ClozeParser.parse(newJson)
        assertEquals(5, result[0].weight)
        assertEquals(2, result[1].weight)
        assertEquals(1, result[2].weight) // 缺 weight → 默认 1
    }

    @Test
    fun `parse new format reads mustBlank`() {
        val result = ClozeParser.parse(newJson)
        assertTrue(result[0].mustBlank)
        assertFalse(result[1].mustBlank) // 缺 mustBlank → 默认 false
        assertFalse(result[2].mustBlank)
    }

    // ── weightedSample ──────────────────────────────────────────────────

    private fun item(index: Int, weight: Int = 1, mustBlank: Boolean = false): ClozeItem =
        ClozeItem(index = index, placeholder = "p$index", options = emptyList(), weight = weight, mustBlank = mustBlank)

    @Test
    fun `weightedSample empty returns empty`() {
        assertTrue(ClozeParser.weightedSample(emptyList(), 3).isEmpty())
    }

    @Test
    fun `weightedSample n zero returns empty`() {
        assertTrue(ClozeParser.weightedSample(listOf(item(1)), 0).isEmpty())
    }

    @Test
    fun `weightedSample n exceeding size returns all`() {
        val items = listOf(item(1), item(2), item(3))
        val result = ClozeParser.weightedSample(items, 10, random = Random(42))
        assertEquals(3, result.size)
    }

    @Test
    fun `weightedSample result sorted by index`() {
        val items = listOf(item(3), item(1), item(2))
        val result = ClozeParser.weightedSample(items, 3, random = Random(42))
        assertEquals(listOf(1, 2, 3), result.map { it.index })
    }

    @Test
    fun `weightedSample mustBlank always included`() {
        val items = listOf(
            item(1, weight = 1, mustBlank = true),
            item(2, weight = 100),
            item(3, weight = 100)
        )
        // 抽 2 个：mustBlank 强制入选 + 余下 1 个从 #2/#3 随机
        repeat(20) { seed ->
            val result = ClozeParser.weightedSample(items, 2, random = Random(seed.toLong()))
            assertEquals(2, result.size)
            assertTrue("seed=$seed: mustBlank #1 not included", result.any { it.index == 1 })
        }
    }

    @Test
    fun `weightedSample mustBlank exceeds n keeps highest weight`() {
        val items = listOf(
            item(1, weight = 1, mustBlank = true),
            item(2, weight = 5, mustBlank = true),
            item(3, weight = 10, mustBlank = true)
        )
        val result = ClozeParser.weightedSample(items, 2, random = Random(0))
        assertEquals(2, result.size)
        // 应保留权重最高的 #2 和 #3
        assertEquals(setOf(2, 3), result.map { it.index }.toSet())
    }

    @Test
    fun `weightedSample weight zero excluded`() {
        val items = listOf(
            item(1, weight = 0),
            item(2, weight = 0),
            item(3, weight = 5)
        )
        val result = ClozeParser.weightedSample(items, 1, random = Random(0))
        assertEquals(1, result.size)
        assertEquals(3, result[0].index)
    }

    @Test
    fun `weightedSample high weight selected more often`() {
        val items = listOf(
            item(1, weight = 1),
            item(2, weight = 9)
        )
        // 1000 次单抽 1，#2（权重 9）应远多于 #1（权重 1）
        val counts = IntArray(3)
        val random = Random(123)
        repeat(1000) {
            val r = ClozeParser.weightedSample(items, 1, random = random)
            counts[r[0].index]++
        }
        // 大约 9:1。允许 ±20% 抖动 → #2 > 700, #1 < 200
        assertTrue("#2 应远多于 #1: counts=${counts.toList()}", counts[2] > 700)
        assertTrue("#1 应远少于 #2: counts=${counts.toList()}", counts[1] < 200)
    }

    @Test
    fun `weightedSample recentErrors boosts weight`() {
        val items = listOf(
            item(1, weight = 1),
            item(2, weight = 1)
        )
        // recentErrors[1] = 9 → effective weight 1 × (1+9) = 10
        // recentErrors[2] = 0 → effective weight 1
        val counts = IntArray(3)
        val random = Random(456)
        repeat(1000) {
            val r = ClozeParser.weightedSample(items, 1, recentErrors = mapOf(1 to 9), random = random)
            counts[r[0].index]++
        }
        assertTrue("#1（错次加权）应远多于 #2: counts=${counts.toList()}", counts[1] > 700)
    }

    // ── minimalSample ──────────────────────────────────────────────────

    @Test
    fun `minimalSample empty returns null`() {
        assertNull(ClozeParser.minimalSample(emptyList()))
    }

    @Test
    fun `minimalSample picks body when preconditions excludes some`() {
        val items = listOf(
            ClozeItem(1, "f(x) 任意阶可导", emptyList()),
            ClozeItem(2, "\\frac{f^{(n)}(0)}{n!}", emptyList()),
            ClozeItem(3, "x^n", emptyList())
        )
        val preconditions = listOf("f(x) 任意阶可导")
        // 100 次抽样，结果永远不应是 #1
        repeat(100) { seed ->
            val r = ClozeParser.minimalSample(items, preconditions, random = Random(seed.toLong()))
            assertNotNull(r)
            assertTrue("seed=$seed: 不应抽到条件关键词 #1, got=${r?.index}", r!!.index != 1)
        }
    }

    @Test
    fun `minimalSample falls back when all in preconditions`() {
        val items = listOf(
            ClozeItem(1, "cond_a", emptyList()),
            ClozeItem(2, "cond_b", emptyList())
        )
        val preconditions = listOf("cond_a", "cond_b")
        val r = ClozeParser.minimalSample(items, preconditions, random = Random(0))
        assertNotNull(r)
        assertTrue(r!!.index in setOf(1, 2))
    }

    @Test
    fun `minimalSample empty preconditions returns any`() {
        val items = listOf(
            ClozeItem(1, "a", emptyList()),
            ClozeItem(2, "b", emptyList())
        )
        val r = ClozeParser.minimalSample(items, emptyList(), random = Random(0))
        assertNotNull(r)
    }

    // ── Sprint 1 Task 1.8：补强边界 ────────────────────────────────────────────

    @Test
    fun `weightedSample mustBlank with weight 0 still forced in`() {
        // mustBlank 语义优先于 weight=0 排除（即使 weight=0 也强制入选）
        val items = listOf(
            item(1, weight = 0, mustBlank = true),
            item(2, weight = 5),
            item(3, weight = 5)
        )
        val result = ClozeParser.weightedSample(items, 2, random = Random(0))
        assertEquals(2, result.size)
        assertTrue("mustBlank #1（即使 weight=0）也应入选: ${result.map { it.index }}",
            result.any { it.index == 1 })
    }

    @Test
    fun `weightedSample all zero weights and no mustBlank returns empty`() {
        // 全 weight=0 + 没 mustBlank → 总权重 0，repeat 跳过 → 返回空
        val items = listOf(item(1, weight = 0), item(2, weight = 0), item(3, weight = 0))
        val result = ClozeParser.weightedSample(items, 2, random = Random(0))
        assertTrue("全 weight=0 应返回空: ${result.map { it.index }}", result.isEmpty())
    }

    @Test
    fun `weightedSample recentErrors with unknown index is ignored`() {
        // recentErrors 含不存在的 index（如 #99）不应抛或扰动其他项
        val items = listOf(item(1, weight = 5), item(2, weight = 5))
        val result = ClozeParser.weightedSample(
            items, 2,
            recentErrors = mapOf(99 to 100, 200 to 50),
            random = Random(0)
        )
        assertEquals(2, result.size)
        assertEquals(setOf(1, 2), result.map { it.index }.toSet())
    }

    @Test
    fun `weightedSample same seed produces deterministic order`() {
        // 同一种子的两次独立调用应返回完全相同的抽样结果（便于重放调试）
        val items = (1..6).map { item(it, weight = it) }
        val a = ClozeParser.weightedSample(items, 4, random = Random(2026))
        val b = ClozeParser.weightedSample(items, 4, random = Random(2026))
        assertEquals(a.map { it.index }, b.map { it.index })
    }

    @Test
    fun `minimalSample partial precondition overlap excludes only matched`() {
        // 5 项里有 2 项落在 preconditions 集合 → 200 次抽样永远在剩 3 项里
        val items = listOf(
            ClozeItem(1, "cond_a", emptyList()),
            ClozeItem(2, "x^n",    emptyList()),
            ClozeItem(3, "cond_b", emptyList()),
            ClozeItem(4, "n!",     emptyList()),
            ClozeItem(5, "e^x",    emptyList())
        )
        val preconditions = listOf("cond_a", "cond_b")
        repeat(200) { seed ->
            val r = ClozeParser.minimalSample(items, preconditions, random = Random(seed.toLong()))
            assertNotNull(r)
            assertTrue("seed=$seed: 不应抽到 cond_*: got=${r?.index}/${r?.placeholder}",
                r!!.index in setOf(2, 4, 5))
        }
    }
}
