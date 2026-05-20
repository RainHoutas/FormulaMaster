package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.StudyStateEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.DerivationStepParser
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
 * 结业时 [completeRitual] 在 IO 线程：
 *  1. 初始化 6 张 [SubCardStateEntity]（stability=1.0, nextReviewTime=次日刷新整点）
 *  2. **Plan I 双写过渡**：同时初始化 [StudyStateEntity]，保证 Memory Tab 在 Task 2.6 切换前可见
 *     新激活的公式（Task 2.6 时仅去掉 study_states 写入即可）
 */
data class FormulaLearnRitualUiState(
    val formula: FormulaEntity? = null,
    val purpose: String = "",
    val preconditions: List<String> = emptyList(),
    val derivationSteps: List<DerivationStep> = emptyList(),
    val clozeItems: List<ClozeItem> = emptyList(),
    val minimalClozeItem: ClozeItem? = null,
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
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
    val isFinished: Boolean = false
) {
    val currentCard: CardType? = pendingDeck.getOrNull(currentIndex)
    val isRoundEnd: Boolean = currentIndex >= pendingDeck.size
}

class FormulaLearnRitualViewModel(
    private val repository: FormulaRepository,
    private val studyStateDao: StudyStateDao,
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
            val clozeItems = ClozeParser.parse(formula.clozeData)
            val minimalClozeItem = ClozeParser.minimalSample(clozeItems, preconditions)

            _uiState.update {
                it.copy(
                    formula = formula,
                    purpose = formula.purpose,
                    preconditions = preconditions,
                    derivationSteps = derivationSteps,
                    clozeItems = clozeItems,
                    minimalClozeItem = minimalClozeItem,
                    isLoading = false
                )
            }
        }
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
                passed = newPassed
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
                retryDeck = s.step7.retryDeck + cur
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

            // 2. Plan I 双写：母卡 Task 2.6 切换前继续保持
            if (studyStateDao.getByFormulaId(formula.formulaId) == null) {
                studyStateDao.insert(
                    StudyStateEntity(
                        formulaId = formula.formulaId,
                        learningState = 1,
                        difficulty = initialDifficulty,
                        stability = 1.0,
                        lastReviewTime = nowMs,
                        nextReviewTime = nextTime,
                        lapses = 0,
                        totalReviews = 0,
                        consecutiveGoodReviews = 0
                    )
                )
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
                    repository = FormulaRepository(app, db.formulaDao(), db.formulaSubjectMapDao()),
                    studyStateDao = db.studyStateDao(),
                    subCardStateDao = db.subCardStateDao(),
                    appPreference = AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
