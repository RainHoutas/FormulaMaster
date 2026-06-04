package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.SubCardAggregator
import com.example.formulamaster.domain.model.FormulaWithState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UiState ───────────────────────────────────────────────────────────────────

data class MemoryUiState(
    val formulas: List<FormulaWithState> = emptyList(),
    val isLoading: Boolean = true
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Sprint 2 Task 2.6（2026-05-29）：状态来源由母卡 study_states 切到**子卡聚合**。
 * 整体进度（learningState / lapses 等）由 [SubCardAggregator] 从 sub_card_states 派生，
 * 未激活的公式（无子卡）→ [FormulaWithState.derived] 为 null。
 * 激活（创建 6 子卡）由七步学习仪式结业负责，本 VM 不再写库。
 */
class MemoryViewModel(
    private val repository: FormulaRepository,
    private val subCardStateDao: SubCardStateDao,
    private val appPreference: AppPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Sprint 1 Task 1.3：按用户 kaoyanSubject 过滤公式列表，切换立即生效。
            // flatMapLatest 在 subject 变化时取消旧的上游订阅，重订到新 subject 的 Flow。
            @OptIn(ExperimentalCoroutinesApi::class)
            appPreference.settings
                .map { it.kaoyanSubject }
                .distinctUntilChanged()
                .flatMapLatest { subject ->
                    combine(
                        repository.observeFormulasFor(subject),
                        subCardStateDao.getAllStates()
                    ) { formulas, subCards ->
                        val derivedMap = SubCardAggregator.deriveAll(subCards)
                        formulas.map { formula ->
                            FormulaWithState(formula, derivedMap[formula.formulaId])
                        }
                    }
                }
                .collect { list ->
                    _uiState.update { it.copy(formulas = list, isLoading = false) }
                }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return MemoryViewModel(
                    FormulaRepository(app, db.formulaDao(), db.formulaSubjectMapDao()),
                    db.subCardStateDao(),
                    AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
