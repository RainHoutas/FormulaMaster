package com.example.formulamaster.data.repository

import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.ReviewRouter.DictationState
import com.example.formulamaster.domain.ReviewRouter.FormulaContext
import com.example.formulamaster.domain.ReviewRouter.PhaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 2 Task 2.1b：[ReviewSessionProgressCodec] round-trip + 边界单测。
 *
 * 重点覆盖：
 * - 状态机里所有 sealed / enum 类型完整还原（DictationState.NotStarted 不会变 InProgress(0)）
 * - 未知 CardType code 静默剔除（前向兼容）
 * - JSON 损坏 / null / 空字符串 → decode 返回 null（不闪退）
 * - validate 越界检测
 */
class ReviewSessionProgressCodecTest {

    private fun sampleContext(
        cursor: Int = 0,
        dictation: DictationState = DictationState.NotStarted,
        phase: PhaseStatus = PhaseStatus.Reviewing,
        reinforcement: Set<CardType> = emptySet(),
        roundLapses: Map<CardType, Int> = emptyMap(),
        retestDone: Boolean = false,
        wasBlocked: Boolean = false
    ) = FormulaContext(
        formulaId = "f1",
        dueCards = listOf(CardType.C1_Recognition, CardType.C2_Cloze),
        cursor = cursor,
        roundLapses = roundLapses,
        reinforcementCards = reinforcement,
        reinforcementRetestDone = retestDone,
        phaseStatus = phase,
        dictation = dictation,
        wasPreviouslyBlocked = wasBlocked
    )

    @Test
    fun `空 List 编解码 round-trip`() {
        val json = ReviewSessionProgressCodec.encode(emptyList())
        val decoded = ReviewSessionProgressCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(0, decoded!!.size)
    }

    @Test
    fun `基础 FormulaContext round-trip`() {
        val original = sampleContext()
        val json = ReviewSessionProgressCodec.encode(listOf(original))
        val decoded = ReviewSessionProgressCodec.decode(json)!!.single()
        assertEquals(original, decoded)
    }

    @Test
    fun `DictationState_NotStarted round-trip 不会变 InProgress(0)`() {
        val original = sampleContext(dictation = DictationState.NotStarted)
        val decoded = ReviewSessionProgressCodec.decode(
            ReviewSessionProgressCodec.encode(listOf(original))
        )!!.single()
        assertTrue(decoded.dictation === DictationState.NotStarted || decoded.dictation == DictationState.NotStarted)
        assertFalse("绝不能变成 InProgress(0)", decoded.dictation is DictationState.InProgress)
    }

    @Test
    fun `DictationState_InProgress(0) round-trip 不会被当成 NotStarted`() {
        val original = sampleContext(dictation = DictationState.InProgress(errorCount = 0))
        val decoded = ReviewSessionProgressCodec.decode(
            ReviewSessionProgressCodec.encode(listOf(original))
        )!!.single()
        assertTrue("InProgress(0) 必须区别于 NotStarted", decoded.dictation is DictationState.InProgress)
        assertEquals(0, (decoded.dictation as DictationState.InProgress).errorCount)
    }

    @Test
    fun `DictationState_InProgress 完整 errorCount round-trip`() {
        listOf(0, 1, 2).forEach { n ->
            val original = sampleContext(dictation = DictationState.InProgress(n))
            val decoded = ReviewSessionProgressCodec.decode(
                ReviewSessionProgressCodec.encode(listOf(original))
            )!!.single()
            val state = decoded.dictation as DictationState.InProgress
            assertEquals("errorCount=$n round-trip", n, state.errorCount)
        }
    }

    @Test
    fun `PhaseStatus 四种枚举完整 round-trip`() {
        PhaseStatus.entries.forEach { phase ->
            val original = sampleContext(phase = phase)
            val decoded = ReviewSessionProgressCodec.decode(
                ReviewSessionProgressCodec.encode(listOf(original))
            )!!.single()
            assertEquals(phase, decoded.phaseStatus)
        }
    }

    @Test
    fun `reinforcementCards Set round-trip 保留所有元素`() {
        val original = sampleContext(
            reinforcement = setOf(CardType.C1_Recognition, CardType.C3_Precondition)
        )
        val decoded = ReviewSessionProgressCodec.decode(
            ReviewSessionProgressCodec.encode(listOf(original))
        )!!.single()
        assertEquals(
            setOf(CardType.C1_Recognition, CardType.C3_Precondition),
            decoded.reinforcementCards
        )
    }

    @Test
    fun `roundLapses Map 用 enum key round-trip`() {
        val original = sampleContext(
            roundLapses = mapOf(
                CardType.C1_Recognition to 2,
                CardType.C2_Cloze to 1
            )
        )
        val decoded = ReviewSessionProgressCodec.decode(
            ReviewSessionProgressCodec.encode(listOf(original))
        )!!.single()
        assertEquals(2, decoded.roundLapses[CardType.C1_Recognition])
        assertEquals(1, decoded.roundLapses[CardType.C2_Cloze])
        assertNull(decoded.roundLapses[CardType.C3_Precondition])
    }

    @Test
    fun `wasPreviouslyBlocked 标志 round-trip`() {
        val blocked = sampleContext(wasBlocked = true)
        val decoded = ReviewSessionProgressCodec.decode(
            ReviewSessionProgressCodec.encode(listOf(blocked))
        )!!.single()
        assertTrue(decoded.wasPreviouslyBlocked)
    }

    @Test
    fun `多公式 List 顺序保留`() {
        val a = sampleContext().copy(formulaId = "fA", cursor = 0)
        val b = sampleContext().copy(formulaId = "fB", cursor = 1, phaseStatus = PhaseStatus.Dictating)
        val c = sampleContext().copy(formulaId = "fC", phaseStatus = PhaseStatus.Graduated)
        val json = ReviewSessionProgressCodec.encode(listOf(a, b, c))
        val decoded = ReviewSessionProgressCodec.decode(json)!!
        assertEquals(listOf("fA", "fB", "fC"), decoded.map { it.formulaId })
        assertEquals(PhaseStatus.Dictating, decoded[1].phaseStatus)
        assertEquals(PhaseStatus.Graduated, decoded[2].phaseStatus)
    }

    @Test
    fun `RouterState 整体 encode 等价于 formulas List encode`() {
        val ctx = sampleContext()
        val state = ReviewRouter.RouterState(formulas = listOf(ctx), currentFormulaIndex = 0)
        assertEquals(
            ReviewSessionProgressCodec.encode(listOf(ctx)),
            ReviewSessionProgressCodec.encode(state)
        )
    }

    // ── 前向兼容 / 容错 ────────────────────────────────────────────────────

    @Test
    fun `未知 CardType code 静默剔除`() {
        // 模拟未来扩展 c7 的 JSON，反序列化时应剔除而非抛
        val malicious = """[{
            "formulaId":"f1",
            "dueCardCodes":["c1","c99","c2"],
            "cursor":0,
            "roundLapsesMap":{"c99":5,"c1":1},
            "reinforcementCardCodes":["c99","c1"],
            "reinforcementRetestDone":false,
            "phaseStatusName":"Reviewing",
            "dictationErrorCount":null,
            "wasPreviouslyBlocked":false
        }]"""
        val decoded = ReviewSessionProgressCodec.decode(malicious)!!.single()
        assertEquals(listOf(CardType.C1_Recognition, CardType.C2_Cloze), decoded.dueCards)
        assertEquals(setOf(CardType.C1_Recognition), decoded.reinforcementCards)
        assertEquals(mapOf(CardType.C1_Recognition to 1), decoded.roundLapses)
    }

    @Test
    fun `未知 PhaseStatus name 兜底为 Reviewing`() {
        val malicious = """[{
            "formulaId":"f1",
            "dueCardCodes":["c1"],
            "cursor":0,
            "roundLapsesMap":{},
            "reinforcementCardCodes":[],
            "reinforcementRetestDone":false,
            "phaseStatusName":"AlienPhase",
            "dictationErrorCount":null,
            "wasPreviouslyBlocked":false
        }]"""
        val decoded = ReviewSessionProgressCodec.decode(malicious)!!.single()
        assertEquals(PhaseStatus.Reviewing, decoded.phaseStatus)
    }

    @Test
    fun `JSON 损坏 decode 返回 null 不抛`() {
        assertNull(ReviewSessionProgressCodec.decode("{not json"))
        assertNull(ReviewSessionProgressCodec.decode("[malformed"))
    }

    @Test
    fun `null 与空字符串 decode 返回 null`() {
        assertNull(ReviewSessionProgressCodec.decode(null))
        assertNull(ReviewSessionProgressCodec.decode(""))
        assertNull(ReviewSessionProgressCodec.decode("   "))
    }

    @Test
    fun `负 cursor 被矫正为 0`() {
        val malicious = """[{
            "formulaId":"f1",
            "dueCardCodes":["c1","c2"],
            "cursor":-3,
            "roundLapsesMap":{},
            "reinforcementCardCodes":[],
            "reinforcementRetestDone":false,
            "phaseStatusName":"Reviewing",
            "dictationErrorCount":null,
            "wasPreviouslyBlocked":false
        }]"""
        val decoded = ReviewSessionProgressCodec.decode(malicious)!!.single()
        assertEquals(0, decoded.cursor)
    }

    // ── validate ────────────────────────────────────────────────────────

    @Test
    fun `validate cursor 在合法范围内通过`() {
        assertTrue(ReviewSessionProgressCodec.validate(sampleContext(cursor = 0)))
        assertTrue(ReviewSessionProgressCodec.validate(sampleContext(cursor = 1)))
        assertTrue(ReviewSessionProgressCodec.validate(sampleContext(cursor = 2)))  // = dueCards.size 也合法
    }

    @Test
    fun `validate cursor 越界检测`() {
        assertFalse(ReviewSessionProgressCodec.validate(sampleContext(cursor = 3)))
        assertFalse(ReviewSessionProgressCodec.validate(sampleContext(cursor = 99)))
    }

    @Test
    fun `validate 多公式列表 任一越界即 false`() {
        val ok = sampleContext(cursor = 0)
        val bad = sampleContext(cursor = 99)
        assertTrue(ReviewSessionProgressCodec.validate(listOf(ok, ok)))
        assertFalse(ReviewSessionProgressCodec.validate(listOf(ok, bad)))
    }
}
