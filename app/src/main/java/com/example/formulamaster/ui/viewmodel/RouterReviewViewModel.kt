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
import com.example.formulamaster.domain.ClozeParser
import com.example.formulamaster.domain.DerivationStepParser
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.model.ClozeItem
import com.example.formulamaster.domain.model.DerivationStep
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
    /**
     * C2 加权 cloze 卡本轮抽中的挖空（按 index 升序）。仅当 [pendingAction] 是 C2 ShowCard 时非空。
     * 在 VM 内用 [ClozeParser.weightedSample] 抽样（不在 Composable 解析/采样）；每张卡稳定一次。
     */
    val currentClozeBlanks: List<ClozeItem> = emptyList(),
    /**
     * C4 推导卡的推导链（按 derivationSteps 顺序）。仅当 [pendingAction] 是 C4 ShowCard 时非空。
     * 在 VM 内用 [DerivationStepParser] 解析（不在 Composable 解析 JSON）；空时面板回落通用骨架。
     */
    val currentDerivationSteps: List<DerivationStep> = emptyList(),
    /** C6 题型反查卡本轮抽中的题面（纯文本）。仅当 [pendingAction] 是 C6 ShowCard 且数据齐时非空。 */
    val currentC6Problem: String = "",
    /** C6 候选公式池（同章节，已按用户 subject 过滤 + 稳定乱序）；KaTeX 渲染。 */
    val currentC6Options: List<C6Option> = emptyList(),
    /** C6 当前题面的正确公式 id 集合（当前恒为单条 = 题面所属公式）。 */
    val currentC6CorrectIds: Set<String> = emptySet(),
    val isSessionEnd: Boolean = false,
    /** "Fresh" / "Resumed" / "FallbackToFresh"；UI 可在调试模式 toast 提示，生产可忽略 */
    val initType: String? = null
)

/**
 * C6 题型反查卡的一个候选选项（Sprint 3 Task 3.2）。
 * [latex] 供 KaTeX 渲染选项；[formulaId] 用于判分（选中集 vs 正确集）。
 */
data class C6Option(
    val formulaId: String,
    val title: String,
    val latex: String
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

        // 未实装 / 无法出真卡的卡型剔除，避免回落成通用"看答案"（2026-07-01 真机验收）：
        //   - C5 易混辨析：延后 Sprint 4（缺 diffExplanation 内容 + 无专属面板）
        //   - C6 题型反查：需同章 ≥2 公式才能凑候选池，否则 buildC6Card 回落
        val subjectFormulas = formulaRepository.observeFormulasFor(settings.kaoyanSubject).first()
        val chapterCounts = subjectFormulas.groupingBy { it.chapter }.eachCount()
        val chapterOf = subjectFormulas.associate { it.formulaId to it.chapter }

        // 2. 每个公式内部排序：isReinforced=true 优先 + nextReviewTime 升序；并过滤未实装卡型
        val dueCardsByFormula: Map<String, List<CardType>> = grouped.mapValues { (formulaId, cards) ->
            val chapterCount = chapterOf[formulaId]?.let { chapterCounts[it] } ?: 0
            cards.sortedWith(
                compareByDescending<SubCardStateEntity> { it.isReinforced }
                    .thenBy { it.nextReviewTime }
            ).mapNotNull { CardType.fromCode(it.cardType) }
                .filter { ct ->
                    when (ct) {
                        CardType.C5_Discrimination -> false
                        CardType.C6_TypicalProblem -> chapterCount >= 2
                        else -> true
                    }
                }
        }.filterValues { it.isNotEmpty() }   // 过滤后无卡可考的公式剔除

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

        // C2 加权 cloze：抽 min(3, 总挖空数) 个空，mustBlank 优先（weightedSample 内部保证）
        val clozeBlanks: List<ClozeItem> = if (
            action is ReviewRouter.NextAction.ShowCard &&
            action.cardType == CardType.C2_Cloze &&
            formula != null
        ) {
            val items = ClozeParser.parse(formula.clozeData)
            ClozeParser.weightedSample(items, n = minOf(CLOZE_BLANKS_TARGET, items.size))
        } else {
            emptyList()
        }

        // C4 推导卡：解析 derivationSteps 链（一次全露，UI 在倒计时门后展示）
        val derivationSteps: List<DerivationStep> = if (
            action is ReviewRouter.NextAction.ShowCard &&
            action.cardType == CardType.C4_Derivation &&
            formula != null
        ) {
            DerivationStepParser.parse(formula.derivationSteps)
        } else {
            emptyList()
        }

        // C6 题型反查卡：抽一道题面 + 同章节候选池（按用户 subject 过滤，稳定乱序）
        val c6 = if (
            action is ReviewRouter.NextAction.ShowCard &&
            action.cardType == CardType.C6_TypicalProblem &&
            formula != null
        ) {
            buildC6Card(formula)
        } else {
            null
        }

        _uiState.update {
            it.copy(
                isLoading           = false,
                pendingAction       = action,
                currentFormula      = formula,
                currentSubCard      = subCard,
                currentPreconditions = preconditions,
                currentClozeBlanks  = clozeBlanks,
                currentDerivationSteps = derivationSteps,
                currentC6Problem    = c6?.problem.orEmpty(),
                currentC6Options    = c6?.options.orEmpty(),
                currentC6CorrectIds = c6?.correctIds.orEmpty(),
                isSessionEnd        = action is ReviewRouter.NextAction.SessionEnd,
                initType            = initType ?: it.initType
            )
        }
    }

    /**
     * 组装一张 C6 题型反查卡：随机抽一道 [FormulaEntity.typicalProblems] 题面，
     * 候选池 = 用户 subject 下与该公式**同章节**的全部公式（含本身），稳定乱序后 KaTeX 渲染。
     *
     * 数据不足（无题面 / 同章节候选 < 2 条 = 没有干扰项）时返回 null，由调用方回落通用骨架。
     * 题面纯文本（教辅改编），用 Text 渲染；选项才是公式 KaTeX。
     */
    private suspend fun buildC6Card(formula: FormulaEntity): C6CardData? {
        val problems = runCatching {
            Gson().fromJson<List<String>>(formula.typicalProblems, stringListType) ?: emptyList()
        }.getOrDefault(emptyList()).filter { it.isNotBlank() }
        if (problems.isEmpty()) return null

        val subject = appPreference.settings.value.kaoyanSubject
        val sameChapter = formulaRepository.observeFormulasFor(subject).first()
            .filter { it.chapter == formula.chapter }
        if (sameChapter.size < 2) return null

        val options = sameChapter
            .map { C6Option(formulaId = it.formulaId, title = it.title, latex = it.latexCode) }
            .shuffled()

        return C6CardData(
            problem = problems.random(),
            options = options,
            correctIds = setOf(formula.formulaId)
        )
    }

    private data class C6CardData(
        val problem: String,
        val options: List<C6Option>,
        val correctIds: Set<String>
    )

    companion object {
        /** C2 每张卡目标挖空数（自适应 min(此值, 公式总挖空数)，用户拍板 2026-05-28）。 */
        private const val CLOZE_BLANKS_TARGET = 3

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
