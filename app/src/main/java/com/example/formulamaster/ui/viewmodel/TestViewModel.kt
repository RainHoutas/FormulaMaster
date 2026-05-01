package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.OcrFeedbackDao
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.OcrFeedbackEntity
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.RecognitionMode
import com.example.formulamaster.domain.ReviewScheduler
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.domain.model.FormulaWithState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UiState ───────────────────────────────────────────────────────────────────

data class TestUiState(
    val queue: List<FormulaWithState> = emptyList(),
    val currentIndex: Int = 0,
    val completedCount: Int = 0,
    val isLoading: Boolean = true,
    /** 当前题答题区：从 TestCanvas 候选拼接而来的 LaTeX 片段 */
    val answerPieces: List<String> = emptyList()
) {
    val currentItem: FormulaWithState?
        get() = queue.getOrNull(currentIndex)

    val isSessionComplete: Boolean
        get() = !isLoading && queue.isNotEmpty() && completedCount >= queue.size

    val canSubmit: Boolean
        get() = answerPieces.isNotEmpty()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────
//
// 测试模块（严测）核心状态机：
//   - 数据源：已掌握 (learningState == 3) 的公式
//   - 会话队列首次加载后冻结，防止评分落库后列表跳动
//   - 答题区状态（answerPieces）上升到 VM，输入组件切换（手写→键盘→结构化）时
//     下层组件只需向 VM 回传 LaTeX 片段，VM 自身无关输入方式

class TestViewModel(
    private val repository: FormulaRepository,
    private val studyStateDao: StudyStateDao,
    private val reviewLogDao: ReviewLogDao,
    private val ocrFeedbackDao: OcrFeedbackDao,
    private val appPreference: AppPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    /**
     * Sprint 2 Task 2.4：是否处于考前冲刺期。
     * 跟随 [AppPreference.settings] 变化（用户改考试日期立即重算）。
     */
    val isSprintActive: StateFlow<Boolean> = appPreference.settings
        .map { SprintModeManager.isActive(it.effectiveTargetExamDate) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    // 会话队列快照：首次加载后冻结
    private var sessionItems: List<FormulaWithState>? = null

    init {
        viewModelScope.launch {
            combine(
                repository.getAll(),
                studyStateDao.getMasteredFormulas()
            ) { formulas, states ->
                val formulaMap = formulas.associateBy { it.formulaId }
                states.mapNotNull { state ->
                    formulaMap[state.formulaId]?.let { formula ->
                        FormulaWithState(formula, state)
                    }
                }
            }.collect { queue ->
                if (sessionItems == null) {
                    sessionItems = queue
                }
                _uiState.update {
                    it.copy(queue = sessionItems!!, isLoading = false)
                }
            }
        }
    }

    // ── 答题区操作 ────────────────────────────────────────────────────────────

    fun appendPiece(latex: String) {
        _uiState.update { it.copy(answerPieces = it.answerPieces + latex) }
    }

    fun popLastPiece() {
        _uiState.update {
            if (it.answerPieces.isEmpty()) it
            else it.copy(answerPieces = it.answerPieces.dropLast(1))
        }
    }

    fun clearAnswer() {
        _uiState.update { it.copy(answerPieces = emptyList()) }
    }

    // ── Task 5.3：严厉裁决 ────────────────────────────────────────────────────
    //
    // 完全正确 → rating=4 + isTestMode=true（S 额外 ×1.5 奖励）
    // 出现错误 → rating=1 + isTestMode=true（强制 learningState=1，S 清零重置，lapses++）
    //
    // 写入 ReviewLog（interactionType=3），自动推进到下一题。
    fun submitJudgment(
        item: FormulaWithState,
        isCorrect: Boolean,
        costTimeMs: Long
    ) {
        val studyState = item.studyState ?: return
        val rating = if (isCorrect) 4 else 1
        val nowMs = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            // 1. FSRS 调度（测试模式）
            val appSettings = appPreference.settings.value
            val result = ReviewScheduler.calculate(
                current       = studyState,
                rating        = rating,
                isTestMode    = true,
                currentTimeMs = nowMs,
                hourOfDay     = appSettings.dailyRefreshHourOfDay,
                minute        = appSettings.dailyRefreshMinuteOfHour
            )

            // 2. 连续好评计数：出错强制降级时清零，正确时保留
            val newConsecutive = if (rating == 1) 0 else studyState.consecutiveGoodReviews

            // 3. 更新 StudyStateEntity
            studyStateDao.update(
                studyState.copy(
                    learningState          = result.newLearningState,
                    difficulty             = result.newDifficulty,
                    stability              = result.newStability,
                    lastReviewTime         = nowMs,
                    nextReviewTime         = result.nextReviewTime,
                    lapses                 = result.newLapses,
                    totalReviews           = studyState.totalReviews + 1,
                    consecutiveGoodReviews = newConsecutive
                )
            )

            // 4. 写入测试流水日志（interactionType = 3）
            reviewLogDao.insert(
                ReviewLogEntity(
                    formulaId       = item.formula.formulaId,
                    reviewTime      = nowMs,
                    interactionType = 3,
                    userRating      = rating,
                    costTimeMs      = costTimeMs
                )
            )

            // 5. 推进到下一题 + 清空答题区
            _uiState.update {
                val next = it.currentIndex + 1
                it.copy(
                    currentIndex   = next.coerceAtMost(it.queue.size),
                    completedCount = it.completedCount + 1,
                    answerPieces   = emptyList()
                )
            }
        }
    }

    // ── Sprint 1 Task 1.9：识别失败反馈入库 ──────────────────────────────────
    //
    // formulaId 为可选；TestScreen 调用时传当前题目 id，其他场景调用方可传 null。
    // strokesJson / candidatesJson 一律 Gson 序列化；recognizerType 由调用方查 settings.lightRecognizerId / deepRecognizerId 提供（无绑定时传 "none"）。
    fun submitOcrFeedback(
        formulaId: String?,
        recognizerType: String,
        mode: RecognitionMode,
        strokes: List<List<Pair<Float, Float>>>,
        candidates: List<String>,
        correctLatex: String
    ) {
        val gson = Gson()
        val entity = OcrFeedbackEntity(
            createdAt = System.currentTimeMillis(),
            formulaId = formulaId,
            recognizerType = recognizerType,
            mode = mode.name,
            strokesJson = gson.toJson(strokes),
            candidatesJson = gson.toJson(candidates),
            correctLatex = correctLatex
        )
        viewModelScope.launch(Dispatchers.IO) {
            ocrFeedbackDao.insert(entity)
        }
    }

    // ── Task 5.4：顽固难点延后一周 ────────────────────────────────────────────
    //
    // 冲刺期对 lapses≥4 的 Leech 再次遗忘时，允许用户"跳过本周"暂避锋芒。
    // 仅推后 nextReviewTime，不改 stability/difficulty/lapses（已由 submitJudgment 正常落库）。
    fun postponeByWeek(formulaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = studyStateDao.getByFormulaId(formulaId) ?: return@launch
            studyStateDao.setNextReviewTime(formulaId, s.nextReviewTime + 7 * 86_400_000L)
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Sprint 2 Task 2.1 修复 D：从 AppContainer 取数据库单例
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return TestViewModel(
                    FormulaRepository(app, db.formulaDao()),
                    db.studyStateDao(),
                    db.reviewLogDao(),
                    db.ocrFeedbackDao(),
                    AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
