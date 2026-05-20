package com.example.formulamaster.domain

import com.example.formulamaster.domain.ReviewRouter.DictationState
import com.example.formulamaster.domain.ReviewRouter.Event
import com.example.formulamaster.domain.ReviewRouter.Input
import com.example.formulamaster.domain.ReviewRouter.NextAction
import com.example.formulamaster.domain.ReviewRouter.PhaseStatus
import com.example.formulamaster.domain.ReviewRouter.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 2 Task 2.1：复习路由器纯状态机单测。
 *
 * 覆盖：
 * - start / 单公式单卡基本流
 * - 跨公式轮转
 * - 粘卡（评 1 cursor 不动）
 * - 评 ≥ 3 推进
 * - 加强卡（round_lapses ≥ 3）自动入集合 + cursor 跳过
 * - 加强卡回考（该公式 due 卡过完后回考）
 * - 回考评 1 → ReinforcementUpgraded
 * - 回考评 ≥ 3 → ReinforcementCleared
 * - 默写 hint 升级（0/1/2 → 错 3 次 Blocked）
 * - 默写通过 → FormulaGraduated
 * - 多公式终态混合的会话结束
 * - 空 dueCards 直接进默写
 */
class ReviewRouterTest {

    // ── 测试夹具 ──────────────────────────────────────────────────────────────

    private val f1 = "F1"
    private val f2 = "F2"
    private val f3 = "F3"

    private fun startSingleFormula(cards: List<CardType> = listOf(CardType.C1_Recognition)): Step =
        ReviewRouter.start(listOf(f1), mapOf(f1 to cards))

    private fun startThree(): Step = ReviewRouter.start(
        listOf(f1, f2, f3),
        mapOf(
            f1 to listOf(CardType.C1_Recognition),
            f2 to listOf(CardType.C2_Cloze),
            f3 to listOf(CardType.C3_Precondition)
        )
    )

    // ── 单元用例 ──────────────────────────────────────────────────────────────

    @Test
    fun `start 单公式单卡 输出 ShowCard 指向首张 due 卡`() {
        val step = startSingleFormula()
        val action = step.nextAction
        assertTrue("nextAction 应为 ShowCard", action is NextAction.ShowCard)
        action as NextAction.ShowCard
        assertEquals(f1, action.formulaId)
        assertEquals(CardType.C1_Recognition, action.cardType)
        assertFalse("初次推送不应是回考", action.isReinforcementRetest)
    }

    @Test
    fun `跨公式轮转 三公式各自考一张后回到首位`() {
        var step = startThree()
        // 1st: F1
        assertEquals(f1, (step.nextAction as NextAction.ShowCard).formulaId)
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        // 2nd: F2
        assertEquals(f2, (step.nextAction as NextAction.ShowCard).formulaId)
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        // 3rd: F3
        assertEquals(f3, (step.nextAction as NextAction.ShowCard).formulaId)
    }

    @Test
    fun `评 1 粘卡 cursor 不动 同公式下一轮继续考同一张`() {
        var step = ReviewRouter.start(
            listOf(f1, f2),
            mapOf(
                f1 to listOf(CardType.C1_Recognition, CardType.C2_Cloze),
                f2 to listOf(CardType.C2_Cloze)
            )
        )
        // F1 第一张评 1 → cursor 不动
        step = ReviewRouter.onInput(step.newState, Input.Rate(1))
        // 轮转到 F2
        assertEquals(f2, (step.nextAction as NextAction.ShowCard).formulaId)
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        // 回到 F1 应该还是 C1（粘卡）
        val ctx = step.newState.formulas[0]
        assertEquals(0, ctx.cursor)
        val action = step.nextAction as NextAction.ShowCard
        assertEquals(f1, action.formulaId)
        assertEquals(CardType.C1_Recognition, action.cardType)
    }

    @Test
    fun `评 1 累计 3 次 自动加入加强卡集合 且 cursor 跳到下一张`() {
        var step = ReviewRouter.start(
            listOf(f1),
            mapOf(f1 to listOf(CardType.C1_Recognition, CardType.C2_Cloze))
        )
        // 单公式：第 1 次评 1（粘卡）
        step = ReviewRouter.onInput(step.newState, Input.Rate(1))
        // 单公式没别人可轮转 → 仍是 F1 C1（粘卡）
        assertEquals(CardType.C1_Recognition, (step.nextAction as NextAction.ShowCard).cardType)
        // 第 2 次评 1
        step = ReviewRouter.onInput(step.newState, Input.Rate(1))
        assertEquals(CardType.C1_Recognition, (step.nextAction as NextAction.ShowCard).cardType)
        // 第 3 次评 1 → 加强卡入集合 + cursor 跳到 C2
        step = ReviewRouter.onInput(step.newState, Input.Rate(1))
        val ctx = step.newState.formulas[0]
        assertTrue("C1 应被打加强标记", CardType.C1_Recognition in ctx.reinforcementCards)
        assertEquals(1, ctx.cursor)
        // 下一张应推 C2（cursor 推进的结果）
        assertEquals(CardType.C2_Cloze, (step.nextAction as NextAction.ShowCard).cardType)
    }

    @Test
    fun `加强卡回考时机 该公式 due 卡过完后立即回考`() {
        var step = ReviewRouter.start(
            listOf(f1),
            mapOf(f1 to listOf(CardType.C1_Recognition, CardType.C2_Cloze))
        )
        // C1 累计 3 次评 1 → 进加强卡 + cursor 跳到 C2
        repeat(3) { step = ReviewRouter.onInput(step.newState, Input.Rate(1)) }
        // 此时应推 C2
        assertEquals(CardType.C2_Cloze, (step.nextAction as NextAction.ShowCard).cardType)
        // C2 评 3 推进 → due 卡用完 → 应回考 C1（加强卡）
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        val action = step.nextAction as NextAction.ShowCard
        assertEquals(CardType.C1_Recognition, action.cardType)
        assertTrue("应标记为加强卡回考", action.isReinforcementRetest)
    }

    @Test
    fun `回考评 1 → 升级强标记事件 + 立即进入默写`() {
        var step = ReviewRouter.start(
            listOf(f1),
            mapOf(f1 to listOf(CardType.C1_Recognition))
        )
        // C1 评 1 三次 → 进加强卡 + cursor=1（dueCards.size=1，已用完）
        repeat(3) { step = ReviewRouter.onInput(step.newState, Input.Rate(1)) }
        // 应推 C1 回考
        val retest = step.nextAction as NextAction.ShowCard
        assertTrue(retest.isReinforcementRetest)
        // 回考评 1 → 升级强标记 + 进默写
        step = ReviewRouter.onInput(step.newState, Input.Rate(1))
        assertTrue(
            "应外发 ReinforcementUpgraded",
            step.events.any { it is Event.ReinforcementUpgraded }
        )
        assertTrue(
            "应外发 EnterDictation",
            step.events.any { it is Event.EnterDictation }
        )
        assertTrue("应出 StartDictation 动作", step.nextAction is NextAction.StartDictation)
    }

    @Test
    fun `回考评 3 → 清会话标记 + 进默写`() {
        var step = ReviewRouter.start(
            listOf(f1),
            mapOf(f1 to listOf(CardType.C1_Recognition))
        )
        repeat(3) { step = ReviewRouter.onInput(step.newState, Input.Rate(1)) }
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        assertTrue(step.events.any { it is Event.ReinforcementCleared })
        assertFalse(step.events.any { it is Event.ReinforcementUpgraded })
        assertTrue(step.nextAction is NextAction.StartDictation)
    }

    @Test
    fun `due 卡全过完 无加强卡 直接进默写`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        assertTrue("应进默写", step.nextAction is NextAction.StartDictation)
        assertEquals(0, (step.nextAction as NextAction.StartDictation).hintLevel)
        assertTrue(step.events.any { it is Event.EnterDictation })
    }

    @Test
    fun `默写错 1 次 hint 升到 1`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))           // 进默写
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(false))
        val action = step.nextAction as NextAction.StartDictation
        assertEquals(1, action.hintLevel)
    }

    @Test
    fun `默写错 2 次 hint 升到 2`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(false))
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(false))
        val action = step.nextAction as NextAction.StartDictation
        assertEquals(2, action.hintLevel)
    }

    @Test
    fun `默写错 3 次 → FormulaBlocked + 会话结束`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        repeat(3) { step = ReviewRouter.onInput(step.newState, Input.DictationResult(false)) }
        assertTrue(step.events.any { it is Event.FormulaBlocked })
        assertEquals(PhaseStatus.Blocked, step.newState.formulas[0].phaseStatus)
        assertTrue("单公式 blocked 后会话结束", step.nextAction is NextAction.SessionEnd)
    }

    @Test
    fun `默写通过 → FormulaGraduated + 会话结束`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(true))
        assertTrue(step.events.any { it is Event.FormulaGraduated })
        assertEquals(PhaseStatus.Graduated, step.newState.formulas[0].phaseStatus)
        assertTrue(step.nextAction is NextAction.SessionEnd)
    }

    @Test
    fun `空 dueCards 公式 start 时直接置 Dictating`() {
        val step = ReviewRouter.start(listOf(f1), mapOf(f1 to emptyList()))
        val ctx = step.newState.formulas[0]
        assertEquals(PhaseStatus.Dictating, ctx.phaseStatus)
        assertTrue(step.nextAction is NextAction.StartDictation)
    }

    @Test
    fun `多公式终态混合 任何一个 Blocked 不阻塞其他公式毕业`() {
        var step = ReviewRouter.start(
            listOf(f1, f2),
            mapOf(
                f1 to listOf(CardType.C1_Recognition),
                f2 to listOf(CardType.C1_Recognition)
            )
        )
        // F1 通过评分 → F2 评分 → F1 默写 blocked → F2 默写通过 → 结束
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))     // F1 进默写
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))     // F2 进默写
        // 此时下一步应是某公式默写（轮转选第一个未完成的）
        assertTrue(step.nextAction is NextAction.StartDictation)
        val firstDictateFid = (step.nextAction as NextAction.StartDictation).formulaId
        // 让该公式默写 blocked
        repeat(3) { step = ReviewRouter.onInput(step.newState, Input.DictationResult(false)) }
        assertTrue(step.events.any { it is Event.FormulaBlocked })
        // 还有另一公式待默写
        assertTrue(step.nextAction is NextAction.StartDictation)
        val secondDictateFid = (step.nextAction as NextAction.StartDictation).formulaId
        assertFalse(firstDictateFid == secondDictateFid)
        // 另一公式默写通过 → 会话结束
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(true))
        assertTrue(step.newState.isSessionEnd)
        assertTrue(step.nextAction is NextAction.SessionEnd)
    }

    @Test
    fun `Rate 评分越界抛 IllegalArgumentException`() {
        try {
            Input.Rate(0); error("应抛异常")
        } catch (_: IllegalArgumentException) {}
        try {
            Input.Rate(5); error("应抛异常")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `CardRated 事件携带 formulaId cardType rating 完整三件套`() {
        var step = startSingleFormula(listOf(CardType.C3_Precondition))
        step = ReviewRouter.onInput(step.newState, Input.Rate(2))
        val rated = step.events.filterIsInstance<Event.CardRated>().single()
        assertEquals(f1, rated.formulaId)
        assertEquals(CardType.C3_Precondition, rated.cardType)
        assertEquals(2, rated.rating)
        assertFalse(rated.isReinforcementRetest)
    }

    @Test
    fun `轮转 跳过已 Graduated 公式 直接落到下个 active`() {
        var step = ReviewRouter.start(
            listOf(f1, f2),
            mapOf(
                f1 to listOf(CardType.C1_Recognition),
                f2 to listOf(CardType.C1_Recognition)
            )
        )
        // F1 评 3 → 应轮转到 F2 推卡（不是进 F1 默写）
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        val action = step.nextAction
        // F1 cursor 已满但 F2 还在 Reviewing，应轮到 F2
        assertTrue("应是 ShowCard", action is NextAction.ShowCard)
        action as NextAction.ShowCard
        assertEquals(f2, action.formulaId)
    }

    @Test
    fun `两公式 都默写通过 会话结束`() {
        var step = ReviewRouter.start(
            listOf(f1, f2),
            mapOf(f1 to emptyList(), f2 to emptyList())
        )
        var totalGrads = 0
        // 第一公式默写通过
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(true))
        totalGrads += step.events.filterIsInstance<Event.FormulaGraduated>().size
        // 第二公式默写通过
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(true))
        totalGrads += step.events.filterIsInstance<Event.FormulaGraduated>().size
        assertTrue(step.newState.isSessionEnd)
        assertTrue(step.nextAction is NextAction.SessionEnd)
        assertEquals(2, totalGrads)
    }

    @Test
    fun `Reviewing 阶段调 DictationResult 抛异常`() {
        val step = startSingleFormula()
        try {
            ReviewRouter.onInput(step.newState, Input.DictationResult(true))
            error("应抛 IllegalStateException")
        } catch (_: IllegalStateException) {}
    }

    @Test
    fun `Dictating 阶段调 Rate 抛异常`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))  // 进默写
        try {
            ReviewRouter.onInput(step.newState, Input.Rate(3))
            error("应抛 IllegalStateException")
        } catch (_: IllegalStateException) {}
    }

    @Test
    fun `Dictation hint level 跨 InProgress 状态正确累计`() {
        var step = startSingleFormula()
        step = ReviewRouter.onInput(step.newState, Input.Rate(3))
        // 0 → InProgress(1)
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(false))
        assertTrue(step.newState.formulas[0].dictation is DictationState.InProgress)
        assertEquals(1, (step.newState.formulas[0].dictation as DictationState.InProgress).errorCount)
        // → InProgress(2)
        step = ReviewRouter.onInput(step.newState, Input.DictationResult(false))
        assertEquals(2, (step.newState.formulas[0].dictation as DictationState.InProgress).errorCount)
    }
}
