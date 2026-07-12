package com.example.formulamaster.data.repository

import com.example.formulamaster.data.AppSettings
import com.example.formulamaster.domain.UseScene
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.ReviewScheduler

/**
 * Sprint 2 Task 2.1c：把 [ReviewRouter.Event] sealed 子类翻译成 DAO 写操作。
 *
 * **职责**：每次 [ReviewRouter.onInput] 后吐出一组事件，本类负责按事件类型分发：
 *
 * | Event 类型                  | 副作用 |
 * |-----------------------------|--------|
 * | [Event.CardRated]           | 走 [ReviewScheduler.calculate] 算 FSRS → 更新 sub_card_states + 写 review_log |
 * | [Event.ReinforcementUpgraded] | sub_card.isReinforced=true + stability×0.5 + 连续好评清零 |
 * | [Event.ReinforcementCleared]| 无副作用（会话内标记仅在路由器状态机里活，不落库） |
 * | [Event.EnterDictation]      | 无副作用（UI 端 nextAction 驱动渲染） |
 * | [Event.FormulaBlocked]      | [ReviewSessionRepository.markFormulaBlocked] |
 * | [Event.FormulaGraduated]    | [ReviewSessionRepository.clearFormulaBlocked]（清 banner 标志） |
 *
 * **强标记自动清除规则**（RFC §9.3 D-S2-2 补充第 5 条）：
 * 当 isReinforced=true 的子卡连续 3 次评 ≥ 3 → 自动清除 isReinforced。
 * 实现在 [handleCardRated] 内部，与 FSRS 更新同事务（同一次 DAO update）。
 *
 * **拆分动机**：VM 直接 inline 处理会变成 200+ 行的"什么都管"类，
 * 测试时还要拽进 ViewModelScope / Flow / StateFlow 全套，不利于聚焦核心逻辑。
 * 拆出来后单测可以纯 suspend 函数 + fake DAO 直跑。
 *
 * @param costTimeMsProvider 单次评分耗时（ms），UI 层可注入计时器；默认 0 表示未追踪
 */
class ReviewEventProcessor(
    private val subCardDao: SubCardStateDao,
    private val reviewLogDao: ReviewLogDao,
    private val sessionRepo: ReviewSessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val costTimeMsProvider: () -> Long = { 0L }
) {

    /** 按顺序处理一组事件。 */
    suspend fun processAll(events: List<ReviewRouter.Event>, settings: AppSettings) {
        for (event in events) {
            process(event, settings)
        }
    }

    suspend fun process(event: ReviewRouter.Event, settings: AppSettings) {
        when (event) {
            is ReviewRouter.Event.CardRated              -> handleCardRated(event, settings)
            is ReviewRouter.Event.ReinforcementUpgraded  -> handleReinforcementUpgraded(event)
            is ReviewRouter.Event.ReinforcementCleared   -> Unit  // 会话内标记，不落库
            is ReviewRouter.Event.EnterDictation         -> Unit  // UI 由 nextAction 驱动
            is ReviewRouter.Event.FormulaBlocked         -> sessionRepo.markFormulaBlocked(event.formulaId, clock())
            is ReviewRouter.Event.FormulaGraduated       -> sessionRepo.clearFormulaBlocked(event.formulaId)
        }
    }

    // ── 事件处理细节 ─────────────────────────────────────────────────────────

    private suspend fun handleCardRated(event: ReviewRouter.Event.CardRated, settings: AppSettings) {
        val card = subCardDao.get(event.formulaId, event.cardType.code) ?: return
        val now = clock()

        // 1. FSRS 计算（Sprint 5：仅 KaoyanMath 下按学习阶段调 retention 间隔；其它 Scene 用基线 1.0）
        val intervalFactor = if (settings.useScene == UseScene.KaoyanMath) settings.studyPhase.intervalFactor else 1.0
        val result = ReviewScheduler.calculate(
            current        = card,
            rating         = event.rating,
            isTestMode     = false,
            currentTimeMs  = now,
            hourOfDay      = settings.dailyRefreshHourOfDay,
            minute         = settings.dailyRefreshMinuteOfHour,
            intervalFactor = intervalFactor
        )

        // 2. 连续好评计数 + 强标记自动清除
        val newConsecutive = if (event.rating >= 3) card.consecutiveGoodReviews + 1 else 0
        val newIsReinforced = if (card.isReinforced && newConsecutive >= 3) false else card.isReinforced

        // 3. 更新 sub_card_states（一次性写完 FSRS + 强标记 + 计数）
        subCardDao.update(
            card.copy(
                stability              = result.newStability,
                difficulty             = result.newDifficulty,
                lastReviewTime         = now,
                nextReviewTime         = result.nextReviewTime,
                totalReviews           = card.totalReviews + 1,
                lapses                 = result.newLapses,
                consecutiveGoodReviews = newConsecutive,
                isReinforced           = newIsReinforced
            )
        )

        // 4. 写日志（interactionType=2 复习；回考也算复习）
        reviewLogDao.insert(
            ReviewLogEntity(
                formulaId       = event.formulaId,
                reviewTime      = now,
                interactionType = 2,
                userRating      = event.rating,
                costTimeMs      = costTimeMsProvider()
            )
        )
    }

    /**
     * 加强卡回考再失败 → 升级强标记（持久化）。
     *
     * - `isReinforced = true`：跨会话保留
     * - `stability *= 0.5`：FSRS 层降权，下次 due 提前
     * - `consecutiveGoodReviews = 0`：自动清除规则的计数器归零
     *
     * 不写 review_log：本事件由路由器在 [Event.CardRated] 之后立刻外发（同一次评分内），
     * CardRated 已经写过日志，避免一次评分两条日志。
     */
    private suspend fun handleReinforcementUpgraded(event: ReviewRouter.Event.ReinforcementUpgraded) {
        val card = subCardDao.get(event.formulaId, event.cardType.code) ?: return
        subCardDao.update(
            card.copy(
                isReinforced           = true,
                stability              = card.stability * 0.5,
                consecutiveGoodReviews = 0
            )
        )
    }
}
