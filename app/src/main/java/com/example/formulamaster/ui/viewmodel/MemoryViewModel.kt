package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.StudyStateEntity
import com.example.formulamaster.data.repository.FormulaRepository
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
    private val studyStateDao: StudyStateDao
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
            studyStateDao.insert(
                StudyStateEntity(
                    formulaId = formulaId,
                    learningState = 1,
                    difficulty = formula.difficultyLevel.toDouble().coerceIn(1.0, 5.0),
                    stability = 1.0,
                    lastReviewTime = System.currentTimeMillis(),
                    nextReviewTime = System.currentTimeMillis() + DAY_MS,
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
                val db = AppDatabase.getInstance(context.applicationContext)
                return MemoryViewModel(
                    FormulaRepository(context.applicationContext, db.formulaDao()),
                    db.studyStateDao()
                ) as T
            }
        }
    }
}
