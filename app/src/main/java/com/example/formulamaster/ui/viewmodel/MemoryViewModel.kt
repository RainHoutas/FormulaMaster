package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.StudyStateEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.ReviewScheduler
import com.example.formulamaster.domain.model.FormulaWithState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UiState ───────────────────────────────────────────────────────────────────

data class MemoryUiState(
    val formulas: List<FormulaWithState> = emptyList(),
    val isLoading: Boolean = true
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MemoryViewModel(
    private val repository: FormulaRepository,
    private val studyStateDao: StudyStateDao,
    private val appPreference: AppPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAll(),
                studyStateDao.getAllStates()
            ) { formulas, states ->
                val stateMap = states.associateBy { it.formulaId }
                formulas.map { formula ->
                    FormulaWithState(formula, stateMap[formula.formulaId])
                }
            }.collect { list ->
                _uiState.update { it.copy(formulas = list, isLoading = false) }
            }
        }
    }

    /**
     * Task 3.4：首次激活公式。若已有 StudyStateEntity 则幂等跳过。
     */
    fun activateFormula(formulaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (studyStateDao.getByFormulaId(formulaId) != null) return@launch
            val formula = repository.getById(formulaId) ?: return@launch
            val nowMs = System.currentTimeMillis()
            // Sprint 2 Task 2.3：首次激活也截断到当日刷新整点（修复绕过 ReviewScheduler 的 bug）
            val appSettings = appPreference.settings.value
            val nextTime = ReviewScheduler.adjustToRefreshHour(
                rawTimeMs = nowMs + DAY_MS,
                currentTimeMs = nowMs,
                hourOfDay = appSettings.dailyRefreshHourOfDay,
                minute = appSettings.dailyRefreshMinuteOfHour
            )
            studyStateDao.insert(
                StudyStateEntity(
                    formulaId = formulaId,
                    learningState = 1,
                    difficulty = formula.difficultyLevel.toDouble().coerceIn(1.0, 5.0),
                    stability = 1.0,
                    lastReviewTime = nowMs,
                    nextReviewTime = nextTime,
                    lapses = 0,
                    totalReviews = 0,
                    consecutiveGoodReviews = 0
                )
            )
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return MemoryViewModel(
                    FormulaRepository(app, db.formulaDao()),
                    db.studyStateDao(),
                    AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
