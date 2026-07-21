package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.DerivationStepParser
import com.example.formulamaster.domain.UseScene
import com.example.formulamaster.domain.ReviewScheduler
import com.example.formulamaster.domain.model.ClozeItem
import com.example.formulamaster.domain.model.DerivationStep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sprint 2 Task 2.5：七步学习仪式 ViewModel。
 *
 * 状态：
 *  - [FormulaLearnRitualUiState.formula] 加载完成后非空
 *  - [FormulaLearnRitualUiState.preconditions] / [derivationSteps] / [purpose] / [minimalClozeItem]
 *    都是从 [FormulaEntity] 派生出的 UI 直接消费的结构（避免 Composable 内部重复 JSON 解析）
 *  - [step7] 维护巩固迷你卡序列的状态机（mini-card 三选 + 重做队列）
 *
 * 结业时 [completeRitual] 在 IO 线程初始化 6 张 [SubCardStateEntity]
 * （stability=1.0, nextReviewTime=次日刷新整点）。
 * Task 2.6（2026-05-29）：母卡 study_states 已退役，结业不再双写，子卡为唯一真相源。
 */
data class FormulaLearnRitualUiState(
    val formula: FormulaEntity? = null,
    val purpose: String = "",
    val preconditions: List<String> = emptyList(),
    val derivationSteps: List<DerivationStep> = emptyList(),
    val chunks: List<com.example.formulamaster.domain.model.FormulaChunk> = emptyList(),
    val clozeItems: List<ClozeItem> = emptyList(),
    val minimalClozeItem: ClozeItem? = null,
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
    /** Sprint 5：今日新卡达上限 / 当前阶段关新卡 → 拦截学习入口。 */
    val capBlocked: Boolean = false,
    val capMessage: String = "",
    val step7: Step7State = Step7State()
)

/**
 * 第 7 步「巩固迷你卡序列」状态：
 *  - [pendingDeck]：本轮待答队列；初始 [c1, c2, c3]
 *  - [retryDeck]：本轮答错入队的迷你卡；本轮结束后整体合并回 [pendingDeck]
 *  - [currentIndex]：当前正在答的 mini-card 在 [pendingDeck] 中的下标
 *  - [passed]：已答对的 cardType（去重）。`passed.size >= 3` 时第 7 步通过
 *  - [isFinished]：第 7 步通过（用户点"结业"触发 [completeRitual]）
 */
data class Step7State(
    val pendingDeck: List<CardType> = listOf(
        CardType.C1_Recognition, CardType.C2_Cloze, CardType.C3_Precondition
    ),
    val retryDeck: List<CardType> = emptyList(),
    val currentIndex: Int = 0,
    val passed: Set<CardType> = emptySet(),
    val isFinished: Boolean = false,
    /**
     * Bug 修复：mini-card 的 remember 状态（C1 revealed / C2 selected & submitted / C3 倒计时）
     * 需要在每次"切换到新一张卡（含重做）"时重置。仅靠 currentCard / currentIndex 做 key 不够，
     * 因为重做轮次进来时 currentIndex 重回 0、pendingDeck 又是同一张卡的引用，equals 命中
     * 旧 remember slot 导致状态残留 → 按钮卡死（最典型是 C2 submitted=true 后再也点不动）。
     *
     * 每次 [step7Pass] / [step7Fail] 都 +1，UI 用 `key(attemptCount)` 包 mini-card 强制重组。
     */
    val attemptCount: Int = 0
) {
    val currentCard: CardType? = pendingDeck.getOrNull(currentIndex)
    val isRoundEnd: Boolean = currentIndex >= pendingDeck.size
}

class FormulaLearnRitualViewModel(
    private val repository: FormulaRepository,
    private val subCardStateDao: SubCardStateDao,
    private val appPreference: AppPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormulaLearnRitualUiState())
    val uiState: StateFlow<FormulaLearnRitualUiState> = _uiState.asStateFlow()

    private val stringListType = object : TypeToken<List<String>>() {}.type

    fun load(formulaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val formula = repository.getById(formulaId) ?: return@launch
            val preconditions: List<String> = runCatching {
                Gson().fromJson<List<String>>(formula.preconditions, stringListType) ?: emptyList()
            }.getOrDefault(emptyList())
            val derivationSteps = DerivationStepParser.parse(formula.derivationSteps)
            val chunks = com.example.formulamaster.domain.FormulaChunkParser.parse(formula.chunks)
            val clozeItems = ClozeParser.parse(formula.clozeData)
            val minimalClozeItem = ClozeParser.minimalSample(clozeItems, preconditions)
            val (blocked, message) = newCardGate(appPreference.settings.value)

            // Step7 巩固序列空值驱动（Sprint 6.4 + 6.5）：有对应数据的卡型才进 deck。
            //   C1 手写默写公式本体（恒有）/ C2 需挖空 / C3 需条件 / C4 需推导（Sprint 6.4 新加）。
            //   C5 易混 / C6 题型：首次编码偏早（需跨公式知识），有意不进巩固，交给复习（用户拍板 2026-07-21）。
            val miniDeck = buildList {
                add(CardType.C1_Recognition)
                if (clozeItems.isNotEmpty()) add(CardType.C2_Cloze)
                if (preconditions.isNotEmpty()) add(CardType.C3_Precondition)
                if (derivationSteps.isNotEmpty()) add(CardType.C4_Derivation)
            }

            _uiState.update {
                it.copy(
                    formula = formula,
                    purpose = formula.purpose,
                    preconditions = preconditions,
                    derivationSteps = derivationSteps,
                    chunks = chunks,
                    clozeItems = clozeItems,
                    minimalClozeItem = minimalClozeItem,
                    step7 = Step7State(pendingDeck = miniDeck),
                    isLoading = false,
                    capBlocked = blocked,
                    capMessage = message
                )
            }
        }
    }

    /**
     * Sprint 5 新卡上限 gate：仅考研数学 Scene 生效。当前阶段关新卡 或 今日已达上限 → 拦截。
     */
    private fun newCardGate(settings: com.example.formulamaster.data.AppSettings): Pair<Boolean, String> {
        if (settings.useScene != UseScene.KaoyanMath) return false to ""
        val phase = settings.studyPhase
        if (phase.newCardsClosed) return true to "当前「${phase.displayName}」阶段专注复习巩固，不学新公式。"
        val todayKey = ReviewScheduler.truncateToRefreshHour(
            System.currentTimeMillis(), settings.dailyRefreshHourOfDay, settings.dailyRefreshMinuteOfHour
        )
        return if (settings.newCardsUsedOn(todayKey) >= phase.newCardsPerDay)
            true to "今日新公式已达上限（${phase.newCardsPerDay} 个），先复习巩固已学的吧。"
        else false to ""
    }

    // ── 第 7 步 mini-card 推进 ─────────────────────────────────────────────────

    /** 当前 mini-card 答对：推进；同时把它登记到 passed。 */
    fun step7Pass() {
        _uiState.update { s ->
            val cur = s.step7.currentCard ?: return@update s
            val newPassed = s.step7.passed + cur
            val newIndex = s.step7.currentIndex + 1
            s.copy(step7 = s.step7.copy(
                currentIndex = newIndex,
                passed = newPassed,
                attemptCount = s.step7.attemptCount + 1
            ))
        }
        maybeStartNextRound()
    }

    /** 当前 mini-card 答错：入重做队列，本轮继续下一张。 */
    fun step7Fail() {
        _uiState.update { s ->
            val cur = s.step7.currentCard ?: return@update s
            s.copy(step7 = s.step7.copy(
                currentIndex = s.step7.currentIndex + 1,
                retryDeck = s.step7.retryDeck + cur,
                attemptCount = s.step7.attemptCount + 1
            ))
        }
        maybeStartNextRound()
    }

    /** 一轮结束时：把 retryDeck 合并为新一轮 pendingDeck（去重保留顺序）。 */
    private fun maybeStartNextRound() {
        _uiState.update { s ->
            if (!s.step7.isRoundEnd) return@update s
            val nextPending = s.step7.retryDeck
                .filter { it !in s.step7.passed }
                .distinct()
            if (nextPending.isEmpty()) {
                // 3 张全部 passed，第 7 步完成
                s.copy(step7 = s.step7.copy(isFinished = true))
            } else {
                s.copy(step7 = s.step7.copy(
                    pendingDeck = nextPending,
                    retryDeck = emptyList(),
                    currentIndex = 0
                ))
            }
        }
    }

    // ── 结业：初始化 6 子卡 + 母卡（Plan I 双写过渡） ──────────────────────────

    fun completeRitual() {
        val formula = _uiState.value.formula ?: return
        if (_uiState.value.isCompleted) return

        viewModelScope.launch(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val settings = appPreference.settings.value
            val nextTime = ReviewScheduler.adjustToRefreshHour(
                rawTimeMs = nowMs + DAY_MS,
                currentTimeMs = nowMs,
                hourOfDay = settings.dailyRefreshHourOfDay,
                minute = settings.dailyRefreshMinuteOfHour
            )
            val initialDifficulty = formula.difficultyLevel.toDouble().coerceIn(1.0, 5.0)

            // 1. 初始化 6 张子卡
            subCardStateDao.insertAll(CardType.entries.map { ct ->
                SubCardStateEntity(
                    formulaId = formula.formulaId,
                    cardType = ct.code,
                    stability = 1.0,
                    difficulty = initialDifficulty,
                    lastReviewTime = nowMs,
                    nextReviewTime = nextTime,
                    totalReviews = 0,
                    lapses = 0,
                    consecutiveGoodReviews = 0
                )
            })

            // Task 2.6（2026-05-29）：母卡 study_states 已退役，结业仅初始化 6 子卡，不再双写。

            // Sprint 5：仅考研数学 Scene 记一次新公式激活（新卡上限计数，跨日自动重置）
            if (settings.useScene == UseScene.KaoyanMath) {
                val todayKey = ReviewScheduler.truncateToRefreshHour(
                    nowMs, settings.dailyRefreshHourOfDay, settings.dailyRefreshMinuteOfHour
                )
                appPreference.recordNewActivation(todayKey)
            }

            _uiState.update { it.copy(isCompleted = true) }
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return FormulaLearnRitualViewModel(
                    repository = FormulaRepository(app, db.formulaDao(), db.tagDao(), db.entryTagDao(), db.entryRelationDao()),
                    subCardStateDao = db.subCardStateDao(),
                    appPreference = AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
