package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.AppSettings
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.data.repository.ReviewEventProcessor
import com.example.formulamaster.data.repository.ReviewSessionRepository
import com.example.formulamaster.data.repository.SessionInit
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UiState ───────────────────────────────────────────────────────────────────

/**
 * 路由驱动的复习 UI 状态（Sprint 2 Task 2.1c）。
 *
 * UI 接入步骤：
 * - 启动时 `viewModel.startSession()`，监听 [pendingAction] 渲染对应卡型
 * - 评分调 `viewModel.rate(rating)` / 默写结果调 `viewModel.submitDictation(passed)`
 * - 当 [pendingAction] 是 [ReviewRouter.NextAction.SessionEnd] 时显示"今日完成"页
 *
 * 红色 banner 渲染逻辑：
 * - 默写界面：观察 [pendingAction] is [ReviewRouter.NextAction.StartDictation] && `wasPreviouslyBlocked`
 * - FormulaDetail 信息展示页：直接订阅 BlockedFormulaDao.observeByFormulaId（独立通路）
 */
data class RouterReviewUiState(
    val isLoading: Boolean = true,
    val pendingAction: ReviewRouter.NextAction = ReviewRouter.NextAction.SessionEnd,
    val currentFormula: FormulaEntity? = null,
    val currentSubCard: SubCardStateEntity? = null,
    /** [currentFormula].preconditions 解析后的条件列表（C1/C3 露出展示用，避免 Composable 内解析 JSON）。 */
    val currentPreconditions: List<String> = emptyList(),
    val isSessionEnd: Boolean = false,
    /** "Fresh" / "Resumed" / "FallbackToFresh"；UI 可在调试模式 toast 提示，生产可忽略 */
    val initType: String? = null
)

/**
 * Sprint 2 Task 2.1c：路由驱动的复习 ViewModel。
 *
 * 与旧 [ReviewViewModel] 的差异：
 * - 旧 VM 每个公式作为一张"母卡"评分；写 study_states
 * - 本 VM 由 [ReviewRouter] 状态机驱动，每张卡是 (formulaId, cardType) 对；写 sub_card_states
 * - 旧 VM 没有默写阶段；本 VM 含完整 7 步收尾流程的"复习+默写"闭环
 *
 * **侧效统一走 [ReviewEventProcessor]**：本 VM 自己不写库，仅做状态翻译与持久化锚定。
 */
class RouterReviewViewModel(
    private val sessionRepo: ReviewSessionRepository,
    private val processor: ReviewEventProcessor,
    private val subCardDao: SubCardStateDao,
    private val formulaRepository: FormulaRepository,
    private val appPreference: AppPreference,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouterReviewUiState())
    val uiState: StateFlow<RouterReviewUiState> = _uiState.asStateFlow()

    /** 当前路由器状态；只在 ViewModel 内可变。 */
    private var currentState: ReviewRouter.RouterState? = null
    private var sessionDateMs: Long = 0L

    private val stringListType = object : TypeToken<List<String>>() {}.type

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * 启动会话。同日续接由 [ReviewSessionRepository.startOrResume] 内部决定。
     */
    fun startSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val settings = appPreference.settings.value
            sessionDateMs = sessionRepo.computeSessionDateMs(
                currentTimeMs    = clock(),
                refreshHourOfDay = settings.dailyRefreshHourOfDay,
                refreshMinute    = settings.dailyRefreshMinuteOfHour
            )

            val (formulasInOrder, dueCardsByFormula) = buildSessionInputs(settings)

            val init = sessionRepo.startOrResume(
                formulasInOrder      = formulasInOrder,
                dueCardsByFormula    = dueCardsByFormula,
                sessionDateMs        = sessionDateMs
            )
            currentState = init.step.newState

            val initType = when (init) {
                is SessionInit.Fresh             -> "Fresh"
                is SessionInit.Resumed           -> "Resumed"
                is SessionInit.FallbackToFresh   -> "FallbackToFresh"
            }
            renderUiState(init.step, initType)
        }
    }

    fun rate(rating: Int) {
        val state = currentState ?: return
        viewModelScope.launch {
            val step = ReviewRouter.onInput(state, ReviewRouter.Input.Rate(rating))
            consumeStep(step)
        }
    }

    fun submitDictation(passed: Boolean) {
        val state = currentState ?: return
        viewModelScope.launch {
            val step = ReviewRouter.onInput(state, ReviewRouter.Input.DictationResult(passed))
            consumeStep(step)
        }
    }

    // ── 内部：构造会话输入 ──────────────────────────────────────────────────

    /**
     * 把当前 due 子卡列表组装成路由器需要的两个数据结构：
     * - 公式 ID 列表（按优先级排序：先按是否含强标记，再按 examWeight 降序，再按 formulaId）
     * - 公式 → due cards 列表（每个公式内部按 isReinforced=true 优先，再按 nextReviewTime 升序）
     *
     * 这是 RFC §9.3 D-S2-2 补充第 5 条"强标记路由优先"的实现点。
     */
    private suspend fun buildSessionInputs(
        settings: AppSettings
    ): Pair<List<String>, Map<String, List<CardType>>> {
        val now = clock()
        val dueSubCards = subCardDao.getTodayReviewQueue(now).first()
        if (dueSubCards.isEmpty()) {
            return emptyList<String>() to emptyMap()
        }

        // 1. 按 formulaId 聚合
        val grouped: Map<String, List<SubCardStateEntity>> = dueSubCards.groupBy { it.formulaId }

        // 2. 每个公式内部排序：isReinforced=true 优先 + nextReviewTime 升序
        val dueCardsByFormula: Map<String, List<CardType>> = grouped.mapValues { (_, cards) ->
            cards.sortedWith(
                compareByDescending<SubCardStateEntity> { it.isReinforced }
                    .thenBy { it.nextReviewTime }
            ).mapNotNull { CardType.fromCode(it.cardType) }
        }

        // 3. 公式间排序：先按"是否含强标记卡"降序（让有强标记的公式靠前），
        //    再按 examWeight 降序，最后按 formulaId 稳定排序
        val formulaIds = dueCardsByFormula.keys
        val examWeights: Map<String, Int> = formulaIds
            .mapNotNull { fid -> formulaRepository.getById(fid)?.let { fid to it.examWeight } }
            .toMap()

        val formulasInOrder = formulaIds.sortedWith(
            compareByDescending<String> { fid ->
                grouped[fid].orEmpty().any { it.isReinforced }
            }
                .thenByDescending { examWeights[it] ?: 0 }
                .thenBy { it }
        )

        return formulasInOrder to dueCardsByFormula
    }

    // ── 内部：消化路由器 Step ────────────────────────────────────────────────

    private suspend fun consumeStep(step: ReviewRouter.Step) {
        val settings = appPreference.settings.value
        processor.processAll(step.events, settings)

        if (step.newState.isSessionEnd) {
            sessionRepo.endSession()
        } else {
            sessionRepo.saveCurrentSession(sessionDateMs, step.newState)
        }
        currentState = step.newState
        renderUiState(step, initType = null)
    }

    /**
     * 把 [step.nextAction] 翻译成 UiState：拉公式实体 + 当前子卡状态（如果是 ShowCard）。
     */
    private suspend fun renderUiState(step: ReviewRouter.Step, initType: String?) {
        val action = step.nextAction
        val (formula, subCard) = when (action) {
            is ReviewRouter.NextAction.ShowCard -> {
                val f = formulaRepository.getById(action.formulaId)
                val s = subCardDao.get(action.formulaId, action.cardType.code)
                f to s
            }
            is ReviewRouter.NextAction.StartDictation -> {
                val f = formulaRepository.getById(action.formulaId)
                f to null
            }
            ReviewRouter.NextAction.SessionEnd -> null to null
        }
        val preconditions: List<String> = formula?.let { f ->
            runCatching {
                Gson().fromJson<List<String>>(f.preconditions, stringListType) ?: emptyList()
            }.getOrDefault(emptyList())
        }.orEmpty()
        _uiState.update {
            it.copy(
                isLoading           = false,
                pendingAction       = action,
                currentFormula      = formula,
                currentSubCard      = subCard,
                currentPreconditions = preconditions,
                isSessionEnd        = action is ReviewRouter.NextAction.SessionEnd,
                initType            = initType ?: it.initType
            )
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                val sessionRepo = ReviewSessionRepository(
                    blockedDao  = db.blockedFormulaDao(),
                    progressDao = db.reviewSessionProgressDao()
                )
                val processor = ReviewEventProcessor(
                    subCardDao    = db.subCardStateDao(),
                    reviewLogDao  = db.reviewLogDao(),
                    sessionRepo   = sessionRepo
                )
                return RouterReviewViewModel(
                    sessionRepo       = sessionRepo,
                    processor         = processor,
                    subCardDao        = db.subCardStateDao(),
                    formulaRepository = FormulaRepository(app, db.formulaDao(), db.formulaSubjectMapDao()),
                    appPreference     = AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
