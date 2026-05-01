package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.ReviewScheduler
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.domain.model.FormulaWithState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ── UiState ───────────────────────────────────────────────────────────────────

data class ReviewUiState(
    val queue: List<FormulaWithState> = emptyList(),
    val completedCount: Int = 0,
    val isLoading: Boolean = true
) {
    /** 所有卡片均已评分 */
    val isSessionComplete: Boolean
        get() = !isLoading && queue.isNotEmpty() && completedCount >= queue.size
}

// ── Task 5.5：热力图状态 ──────────────────────────────────────────────────────
//
// 过去 365 天每日复习次数聚合。ReviewLogDao.getLogsByDateRange 是 Flow，
// 新日志写入会自动刷新此 StateFlow，UI 层无需手动重载。

data class HeatmapState(
    val dayCounts: Map<LocalDate, Int> = emptyMap(),
    val today: LocalDate = LocalDate.now()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ReviewViewModel(
    private val repository: FormulaRepository,
    private val studyStateDao: StudyStateDao,
    private val reviewLogDao: ReviewLogDao,
    private val appPreference: AppPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

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

    // 会话开始时间：队列以此为截止基准
    private val sessionStartMs = System.currentTimeMillis()

    // 会话队列快照：一旦加载完毕就冻结，防止评分落库后卡片消失导致 Pager 跳位
    private var sessionItems: List<FormulaWithState>? = null

    // ── Task 5.5：365 天日志 → 按日聚合 → StateFlow ──────────────────────────
    val heatmap: StateFlow<HeatmapState> = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val endMs = sessionStartMs
        val startMs = endMs - 365L * 86_400_000L
        reviewLogDao.getLogsByDateRange(startMs, endMs)
            .map { logs ->
                val counts = logs.groupingBy { log ->
                    Instant.ofEpochMilli(log.reviewTime).atZone(zone).toLocalDate()
                }.eachCount()
                HeatmapState(dayCounts = counts, today = today)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = HeatmapState(today = today)
            )
    }

    init {
        viewModelScope.launch {
            combine(
                repository.getAll(),
                studyStateDao.getTodayReviewQueue(sessionStartMs)
            ) { formulas, states ->
                val formulaMap = formulas.associateBy { it.formulaId }
                states.mapNotNull { state ->
                    formulaMap[state.formulaId]?.let { formula ->
                        FormulaWithState(formula, state)
                    }
                }
            }.collect { queue ->
                // 首次加载时冻结快照（后续 DB 变动不影响本次会话列表）
                if (sessionItems == null) {
                    sessionItems = queue
                }
                _uiState.update {
                    it.copy(queue = sessionItems!!, isLoading = false)
                }
            }
        }
    }

    // ── Task 4.5 + 4.6：FSRS 落库 + 状态迁移检查 ────────────────────────────────

    fun submitReview(
        item: FormulaWithState,
        rating: Int,
        costTimeMs: Long
    ) {
        val studyState = item.studyState ?: return
        val nowMs = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            // 1. FSRS 调度计算（基础稳定性 / 难度 / Mastered 判定）
            val appSettings = appPreference.settings.value
            val result = ReviewScheduler.calculate(
                current       = studyState,
                rating        = rating,
                isTestMode    = false,
                currentTimeMs = nowMs,
                hourOfDay     = appSettings.dailyRefreshHourOfDay,
                minute        = appSettings.dailyRefreshMinuteOfHour
            )

            // 2. Task 4.6：状态机迁移检查（在 Scheduler 结果基础上叠加）
            //    规则 A（上升）：Learning(1) 连续 3 次 R≥3 → Reviewing(2)
            //    规则 B（下降）：Reviewing(2) 且 R=1       → Learning(1)
            val (finalState, newConsecutive) = when {
                // Scheduler 已判定 Mastered(S>30) → 优先尊重，重置连续计数
                result.newLearningState == 3 ->
                    3 to 0

                // 当前 Learning(1)：累积或重置连续好评计数
                studyState.learningState == 1 && rating >= 3 -> {
                    val c = studyState.consecutiveGoodReviews + 1
                    if (c >= 3) 2 to 0   // 升入 Reviewing
                    else        1 to c    // 继续 Learning，计数 +1
                }
                studyState.learningState == 1 ->
                    1 to 0   // R<3：重置连续计数，留在 Learning

                // 当前 Reviewing(2) 且 R=1 → 降回 Learning
                studyState.learningState == 2 && rating == 1 ->
                    1 to 0

                // 其余情况保持 Scheduler 结果
                else -> result.newLearningState to studyState.consecutiveGoodReviews
            }

            // 3. 更新 StudyStateEntity
            studyStateDao.update(
                studyState.copy(
                    learningState          = finalState,
                    difficulty             = result.newDifficulty,
                    stability              = result.newStability,
                    lastReviewTime         = nowMs,
                    nextReviewTime         = result.nextReviewTime,
                    lapses                 = result.newLapses,
                    totalReviews           = studyState.totalReviews + 1,
                    consecutiveGoodReviews = newConsecutive
                )
            )

            // 3. 插入日志（interactionType = 2：日常复习）
            reviewLogDao.insert(
                ReviewLogEntity(
                    formulaId       = item.formula.formulaId,
                    reviewTime      = nowMs,
                    interactionType = 2,
                    userRating      = rating,
                    costTimeMs      = costTimeMs
                )
            )

            // 4. 更新已完成计数（用于显示会话结束空状态）
            _uiState.update { it.copy(completedCount = it.completedCount + 1) }
        }
    }

    // ── Task 5.4：顽固难点延后一周 ────────────────────────────────────────────
    //
    // 冲刺期对 lapses≥4 的 Leech 再次遗忘时，允许用户"跳过本周"暂避锋芒。
    // 仅推后 nextReviewTime，不改 stability/difficulty/lapses（已由 submitReview 正常落库）。
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
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return ReviewViewModel(
                    FormulaRepository(app, db.formulaDao()),
                    db.studyStateDao(),
                    db.reviewLogDao(),
                    AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
