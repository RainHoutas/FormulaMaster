package com.example.formulamaster.domain

/**
 * 复习路由器（学习流程重构 Sprint 2 Task 2.1 / RFC §9.3 D-S2-2 补充）
 *
 * **职责**：纯状态机，描述「轮转 + 粘卡 + 加强卡 + 默写收尾 + blocked」复习节奏；
 * 不依赖 Room / Coroutines / FSRS 计算，全部 in-memory pure logic，便于单测。
 *
 * **不负责的事**：
 * - FSRS S/D 更新 → 由 [ReviewScheduler.calculate] 处理
 * - SubCardStateEntity 落库 → 由 ReviewViewModel 在每次 [rate] 后写
 * - 强标记 (`isReinforced`) 写回 → 由 ViewModel 监听 [RouterEvent.ReinforcementUpgraded] 触发
 * - 跨会话恢复 → 由 ReviewSessionProgressEntity + ViewModel 在 [start] 前 hydrate state
 *
 * **核心规则**（RFC §9.3 D-S2-2 + 补充五条）：
 * 1. 跨公式轮转：每个公式持有 [FormulaContext]，路由器按 `currentFormulaIndex` 在公式间轮询，每个公式各考 1 张。
 * 2. 粘卡：评 ≥ 3 → cursor 推进；评 = 1 → cursor 不动，下一轮回到同公式时继续考同卡。
 * 3. 加强卡：单卡本会话内 round_lapses ≥ 3 → 加入 reinforcementCards，cursor 跳到下一张 due 卡。
 * 4. 加强卡回考：**该公式所有 due 卡过完后**，先回考加强卡 → 才进默写。
 *    - 回考评 ≥ 3 → 加强卡标记清除（仅会话内）
 *    - 回考评 = 1 → 升级强标记（事件外发，ViewModel 写 `isReinforced = true` + stability ×0.5）
 * 5. 默写：错 1 hint1 / 错 2 hint2 / 错 3 → Blocked。
 * 6. 会话结束：所有公式 Graduated 或 Blocked。
 *
 * **未实装卡型 fallback**：Sprint 2 期间 C4/C5/C6 UI 未做时，调用方在构造 [FormulaContext.dueCards] 时直接剔除这些 cardType。
 */
object ReviewRouter {

    // ── 输入 / 输出 / 状态 ────────────────────────────────────────────────────

    /** 单个公式的复习上下文。 */
    data class FormulaContext(
        val formulaId: String,
        /**
         * 当前公式本会话的应考子卡列表。
         * 调用方负责：剔除未 due / 未实装 cardType；
         * **强标记卡**应排在普通卡前面（路由器同 due 优先体现在此处）。
         */
        val dueCards: List<CardType>,
        val cursor: Int = 0,
        /** 本会话内每张卡评 1 的累计次数（粘卡 + 加强卡判定都靠它）。 */
        val roundLapses: Map<CardType, Int> = emptyMap(),
        /** 加强卡集合（round_lapses ≥ 3 后加入；进默写前回考过会移除）。 */
        val reinforcementCards: Set<CardType> = emptySet(),
        /** 是否已经历过回考阶段（避免重复回考）。 */
        val reinforcementRetestDone: Boolean = false,
        val phaseStatus: PhaseStatus = PhaseStatus.Reviewing,
        val dictation: DictationState = DictationState.NotStarted,
        /**
         * 上一次会话该公式默写被 Blocked（由调用方在 [ReviewRouter.start] 时根据
         * 持久化的 ReviewSessionProgressEntity 填入）。
         *
         * 路由器**不影响轮转节奏**——blocked 公式在新会话仍走正常 due 卡复习；
         * 进入默写阶段时通过 [NextAction.StartDictation.wasPreviouslyBlocked]
         * 透传给 UI，由 UI 在默写界面顶部显示红色强提醒条。
         *
         * 默写通过（Graduated）或再次 Blocked 后，调用方应在写库时清除该标志。
         */
        val wasPreviouslyBlocked: Boolean = false
    ) {
        /** 该公式所有 due 卡都过了，且加强卡已回考完。 */
        val isReviewComplete: Boolean
            get() = cursor >= dueCards.size && (reinforcementCards.isEmpty() || reinforcementRetestDone)

        val isTerminal: Boolean
            get() = phaseStatus == PhaseStatus.Graduated || phaseStatus == PhaseStatus.Blocked
    }

    enum class PhaseStatus { Reviewing, Dictating, Graduated, Blocked }

    sealed class DictationState {
        object NotStarted : DictationState()
        /** [errorCount] 范围 0..3；0=刚开始；1=hint1；2=hint2；3 时立即升 Blocked 不在此处停留 */
        data class InProgress(val errorCount: Int) : DictationState()
    }

    /** 会话总状态。 */
    data class RouterState(
        val formulas: List<FormulaContext>,
        val currentFormulaIndex: Int = 0
    ) {
        val isSessionEnd: Boolean
            get() = formulas.all { it.isTerminal }
    }

    /** 路由器吐给 UI 的下一步动作。 */
    sealed class NextAction {
        data class ShowCard(
            val formulaId: String,
            val cardType: CardType,
            /** true = 这是加强卡回考（UI 可加 ⚠️ 标识"决战时刻"） */
            val isReinforcementRetest: Boolean
        ) : NextAction()

        data class StartDictation(
            val formulaId: String,
            /** 0 = 首发；1 = hint1（露第一块）；2 = hint2（露推导前两步） */
            val hintLevel: Int,
            /**
             * 上一次会话该公式默写被 Blocked（透传自 [FormulaContext.wasPreviouslyBlocked]）。
             * UI 应在默写界面顶部展示红色强提醒条：「上次默写被阻断，本次格外仔细」。
             */
            val wasPreviouslyBlocked: Boolean = false
        ) : NextAction()

        object SessionEnd : NextAction()
    }

    /** UI 喂回路由器的反馈事件。 */
    sealed class Input {
        data class Rate(val rating: Int) : Input() {
            init { require(rating in 1..4) { "rating 必须在 1..4，实际值：$rating" } }
        }
        data class DictationResult(val passed: Boolean) : Input()
    }

    /** 路由器在状态转换中外发的副作用事件（ViewModel 据此写库 / 触发 FSRS）。 */
    sealed class Event {
        /** 一张子卡刚被评分，需要写 sub_card_states + 走 FSRS。 */
        data class CardRated(
            val formulaId: String,
            val cardType: CardType,
            val rating: Int,
            val isReinforcementRetest: Boolean
        ) : Event()

        /** 加强卡回考再失败 → 升级强标记，ViewModel 写 isReinforced=true + stability ×0.5。 */
        data class ReinforcementUpgraded(val formulaId: String, val cardType: CardType) : Event()

        /** 加强卡回考通过 → 清会话内的加强标记（不影响 isReinforced）。 */
        data class ReinforcementCleared(val formulaId: String, val cardType: CardType) : Event()

        /** 该公式进入默写阶段。 */
        data class EnterDictation(val formulaId: String) : Event()

        /** 公式默写通过 → Graduated。 */
        data class FormulaGraduated(val formulaId: String) : Event()

        /** 公式默写连错 3 次 → Blocked。 */
        data class FormulaBlocked(val formulaId: String) : Event()
    }

    /** [advance] / [onInput] 返回的复合结果：新状态 + 副作用事件列表 + 下一步动作。 */
    data class Step(
        val newState: RouterState,
        val events: List<Event>,
        val nextAction: NextAction
    )

    // ── 构造 / 启动 ───────────────────────────────────────────────────────────

    /**
     * 从公式 → due cards 映射构造初始会话状态。
     *
     * @param formulasInOrder 公式 ID 列表，按调用方期望的轮转顺序（如按 examWeight 降序）
     * @param dueCardsByFormula key=formulaId；value=该公式当前会话应考的 cardType 列表
     *                          （调用方需提前剔除未 due / 未实装 cardType，
     *                          并把 isReinforced=true 的卡排到前面）
     * @param previouslyBlockedFormulas 上一次会话默写被 Blocked 的公式 ID 集合
     *                                  （调用方从 ReviewSessionProgressEntity 读取）。
     *                                  这些公式在本会话**正常走轮转**——只有进入默写阶段时
     *                                  UI 才展示红色强提醒。状态机不做任何特殊跳队。
     */
    fun start(
        formulasInOrder: List<String>,
        dueCardsByFormula: Map<String, List<CardType>>,
        previouslyBlockedFormulas: Set<String> = emptySet()
    ): Step {
        val ctxs = formulasInOrder.map { fid ->
            val due = dueCardsByFormula[fid].orEmpty()
            val initialPhase = if (due.isEmpty()) PhaseStatus.Dictating else PhaseStatus.Reviewing
            FormulaContext(
                formulaId = fid,
                dueCards = due,
                phaseStatus = initialPhase,
                wasPreviouslyBlocked = fid in previouslyBlockedFormulas
            )
        }
        val state = RouterState(formulas = ctxs)
        return advance(state)
    }

    // ── 主推进 ────────────────────────────────────────────────────────────────

    /**
     * 推进到下一个应考目标（不消化 input；调用方在新会话 / hydrate 之后调一次，
     * 后续每次评分由 [onInput] 内部级联调 advance）。
     *
     * 副作用事件：
     * - 当一个公式所有 due 卡 + 加强卡回考完毕，phaseStatus Reviewing → Dictating，
     *   外发 [Event.EnterDictation]。
     */
    fun advance(state: RouterState): Step {
        if (state.isSessionEnd) {
            return Step(state, emptyList(), NextAction.SessionEnd)
        }

        // 把当前公式可能的 Reviewing → Dictating 跃迁先处理掉
        val (rotatedState, transitionEvents) = transitionReviewingToDictating(state)
        if (rotatedState.isSessionEnd) {
            return Step(rotatedState, transitionEvents, NextAction.SessionEnd)
        }

        // 从 currentFormulaIndex 起，找下一个非 terminal 的公式
        val nextIdx = findNextActiveIndex(rotatedState, rotatedState.currentFormulaIndex)
            ?: return Step(rotatedState, transitionEvents, NextAction.SessionEnd)

        val movedState = rotatedState.copy(currentFormulaIndex = nextIdx)
        val ctx = movedState.formulas[nextIdx]

        val action: NextAction = when (ctx.phaseStatus) {
            PhaseStatus.Reviewing -> {
                if (ctx.cursor < ctx.dueCards.size) {
                    NextAction.ShowCard(
                        formulaId = ctx.formulaId,
                        cardType = ctx.dueCards[ctx.cursor],
                        isReinforcementRetest = false
                    )
                } else {
                    // due 卡用完但还没进默写：必然有加强卡待回考（否则在 transitionReviewingToDictating 已切走）
                    val retestCard = ctx.reinforcementCards.first()
                    NextAction.ShowCard(
                        formulaId = ctx.formulaId,
                        cardType = retestCard,
                        isReinforcementRetest = true
                    )
                }
            }
            PhaseStatus.Dictating -> {
                val hintLevel = when (val d = ctx.dictation) {
                    DictationState.NotStarted -> 0
                    is DictationState.InProgress -> d.errorCount
                }
                NextAction.StartDictation(
                    formulaId = ctx.formulaId,
                    hintLevel = hintLevel,
                    wasPreviouslyBlocked = ctx.wasPreviouslyBlocked
                )
            }
            PhaseStatus.Graduated, PhaseStatus.Blocked ->
                // 不可能进入此分支（findNextActiveIndex 已过滤），但保险起见走 SessionEnd 兜底
                NextAction.SessionEnd
        }

        return Step(movedState, transitionEvents, action)
    }

    /** 把当前公式（如果它的 Reviewing 阶段已经全过）跃迁到 Dictating，外发 EnterDictation 事件。 */
    private fun transitionReviewingToDictating(state: RouterState): Pair<RouterState, List<Event>> {
        val idx = state.currentFormulaIndex
        if (idx !in state.formulas.indices) return state to emptyList()
        val ctx = state.formulas[idx]
        return when {
            ctx.phaseStatus == PhaseStatus.Reviewing && ctx.isReviewComplete -> {
                val updated = ctx.copy(phaseStatus = PhaseStatus.Dictating)
                val newFormulas = state.formulas.toMutableList().also { it[idx] = updated }
                state.copy(formulas = newFormulas) to listOf(Event.EnterDictation(ctx.formulaId))
            }
            ctx.phaseStatus == PhaseStatus.Reviewing && ctx.dueCards.isEmpty() && ctx.reinforcementCards.isEmpty() -> {
                // 边界：start() 时已置 Dictating；此分支防守冗余转换
                val updated = ctx.copy(phaseStatus = PhaseStatus.Dictating)
                val newFormulas = state.formulas.toMutableList().also { it[idx] = updated }
                state.copy(formulas = newFormulas) to listOf(Event.EnterDictation(ctx.formulaId))
            }
            else -> state to emptyList()
        }
    }

    /** 从 fromIdx 开始（含），按"下一个公式"轮转方向，找第一个非 terminal 的索引；找不到返回 null。 */
    private fun findNextActiveIndex(state: RouterState, fromIdx: Int): Int? {
        val n = state.formulas.size
        if (n == 0) return null
        var start = fromIdx.coerceIn(0, n - 1)
        // 当前公式如果还在 Reviewing/Dictating，先停在当前（不要立刻轮转走，否则会跳过它本轮）
        if (!state.formulas[start].isTerminal) return start
        for (offset in 1..n) {
            val i = (start + offset) % n
            if (!state.formulas[i].isTerminal) return i
        }
        return null
    }

    // ── 评分输入 ──────────────────────────────────────────────────────────────

    /**
     * UI 评分后调用：转换状态 + 副作用事件 + 计算下一步。
     */
    fun onInput(state: RouterState, input: Input): Step = when (input) {
        is Input.Rate            -> handleRate(state, input.rating)
        is Input.DictationResult -> handleDictation(state, input.passed)
    }

    private fun handleRate(state: RouterState, rating: Int): Step {
        val idx = state.currentFormulaIndex
        val ctx = state.formulas[idx]
        check(ctx.phaseStatus == PhaseStatus.Reviewing) {
            "Rate 必须在 Reviewing 阶段；当前 phase = ${ctx.phaseStatus}"
        }

        val isRetest = ctx.cursor >= ctx.dueCards.size  // 已无 due 卡 → 当前必为加强卡回考
        val card: CardType = if (isRetest) ctx.reinforcementCards.first() else ctx.dueCards[ctx.cursor]

        val events = mutableListOf<Event>()
        events += Event.CardRated(ctx.formulaId, card, rating, isRetest)

        // 计算新 roundLapses + reinforcementCards + cursor
        val oldLapses = ctx.roundLapses[card] ?: 0
        val newLapses = if (rating == 1) oldLapses + 1 else oldLapses
        val newRoundLapses = if (rating == 1) ctx.roundLapses + (card to newLapses) else ctx.roundLapses

        val (newReinforcement, newRetestDone, extraEvents) = when {
            // 回考评 1 → 升级强标记，清会话标记
            isRetest && rating == 1 -> Triple(
                ctx.reinforcementCards - card,
                true,
                listOf<Event>(Event.ReinforcementUpgraded(ctx.formulaId, card))
            )
            // 回考评 ≥ 3 → 清会话标记
            isRetest && rating >= 3 -> Triple(
                ctx.reinforcementCards - card,
                true,
                listOf<Event>(Event.ReinforcementCleared(ctx.formulaId, card))
            )
            // 回考评 2 → 仍属"不会"程度的边缘，保留加强标记继续会话内观察，但回考视为已完成（不重复回考）
            isRetest && rating == 2 -> Triple(
                ctx.reinforcementCards - card,
                true,
                listOf<Event>(Event.ReinforcementCleared(ctx.formulaId, card))
            )
            // 普通卡评 1 累计 ≥ 3 → 加入加强卡集合
            !isRetest && rating == 1 && newLapses >= 3 ->
                Triple(ctx.reinforcementCards + card, ctx.reinforcementRetestDone, emptyList<Event>())
            else -> Triple(ctx.reinforcementCards, ctx.reinforcementRetestDone, emptyList<Event>())
        }
        events += extraEvents

        // cursor 推进逻辑
        val newCursor = when {
            isRetest -> ctx.cursor  // 回考不动 cursor（cursor 已 == dueCards.size）
            rating >= 3 -> ctx.cursor + 1  // 推进
            // 评 1 且这张卡刚成为加强卡 → 跳过它继续推进
            rating == 1 && newLapses >= 3 && card !in ctx.reinforcementCards -> ctx.cursor + 1
            rating == 1 -> ctx.cursor  // 粘卡
            rating == 2 -> ctx.cursor + 1  // 评 2 视为通过，推进（避免无限纠缠）
            else -> ctx.cursor + 1
        }

        val updatedCtx = ctx.copy(
            cursor = newCursor,
            roundLapses = newRoundLapses,
            reinforcementCards = newReinforcement,
            reinforcementRetestDone = newRetestDone
        )
        val newFormulas = state.formulas.toMutableList().also { it[idx] = updatedCtx }

        // 轮转到下一个公式（同一公式连考两张违反"每轮各考 1 张"原则）
        val nextIdx = rotateForward(newFormulas, idx)
        val nextState = state.copy(formulas = newFormulas, currentFormulaIndex = nextIdx)

        val nextStep = advance(nextState)
        return nextStep.copy(events = events + nextStep.events)
    }

    private fun handleDictation(state: RouterState, passed: Boolean): Step {
        val idx = state.currentFormulaIndex
        val ctx = state.formulas[idx]
        check(ctx.phaseStatus == PhaseStatus.Dictating) {
            "DictationResult 必须在 Dictating 阶段；当前 phase = ${ctx.phaseStatus}"
        }

        val events = mutableListOf<Event>()
        val updatedCtx: FormulaContext = when {
            passed -> {
                events += Event.FormulaGraduated(ctx.formulaId)
                ctx.copy(phaseStatus = PhaseStatus.Graduated)
            }
            else -> {
                val newErrors = when (val d = ctx.dictation) {
                    DictationState.NotStarted -> 1
                    is DictationState.InProgress -> d.errorCount + 1
                }
                if (newErrors >= 3) {
                    events += Event.FormulaBlocked(ctx.formulaId)
                    ctx.copy(phaseStatus = PhaseStatus.Blocked, dictation = DictationState.InProgress(newErrors))
                } else {
                    ctx.copy(dictation = DictationState.InProgress(newErrors))
                }
            }
        }
        val newFormulas = state.formulas.toMutableList().also { it[idx] = updatedCtx }

        // 默写通过 / blocked → 轮到下个公式；hint 中失败 → 留在原公式继续默写（不轮转）
        val nextIdx = if (updatedCtx.isTerminal) rotateForward(newFormulas, idx) else idx
        val nextState = state.copy(formulas = newFormulas, currentFormulaIndex = nextIdx)

        val nextStep = advance(nextState)
        return nextStep.copy(events = events + nextStep.events)
    }

    /** 找 (fromIdx + 1) 起的下一个非 terminal 公式（回环）；找不到返回 fromIdx（让 isSessionEnd 兜底）。 */
    private fun rotateForward(formulas: List<FormulaContext>, fromIdx: Int): Int {
        val n = formulas.size
        for (offset in 1..n) {
            val i = (fromIdx + offset) % n
            if (!formulas[i].isTerminal) return i
        }
        return fromIdx
    }
}
