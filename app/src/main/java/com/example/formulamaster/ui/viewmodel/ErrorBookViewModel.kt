package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.ErrorReportDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.ErrorDeletePolicy
import com.example.formulamaster.domain.ErrorReportInput
import com.example.formulamaster.domain.ErrorReportProcessor
import com.example.formulamaster.domain.FormulaIndex
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

/** 错题本列表行（展示用派生模型）。 */
data class ErrorReportRow(
    val report: ErrorReportEntity,
    val formulaCount: Int,
    /** 所选公式标题（未匹配到的回落 formulaId）。 */
    val formulaTitles: List<String>
)

/** 新增错题表单的可编辑状态（全 chip / 数字键盘，零自由文字）。 */
data class ErrorFormState(
    val subject: String? = null,
    val chapter: String? = null,
    val sourceType: String? = null,
    val sourceTag: String = "",
    val selectedFormulaIds: Set<String> = emptySet(),
    /** 公式池「显示全部科目」开关；false = 只显示所选 subject。 */
    val showAllSubjects: Boolean = false
) {
    val canSubmit: Boolean
        get() = !subject.isNullOrBlank() && !chapter.isNullOrBlank() &&
            !sourceType.isNullOrBlank() && sourceTag.isNotBlank() &&
            selectedFormulaIds.isNotEmpty()
}

data class ErrorBookUiState(
    val isLoading: Boolean = true,
    val mode: Mode = Mode.List,
    val rows: List<ErrorReportRow> = emptyList(),
    val formulaIndex: FormulaIndex = FormulaIndex(emptyList()),
    val sourceTypeOptions: List<String> = DEFAULT_SOURCE_TYPES,
    val form: ErrorFormState = ErrorFormState(),
    val deletePolicy: ErrorDeletePolicy = ErrorDeletePolicy.Default,
    /** [ErrorDeletePolicy.Ask] 时点删除 → 挂起待确认的记录（触发对话框）。 */
    val pendingDelete: ErrorReportEntity? = null
) {
    enum class Mode { List, Form }

    companion object {
        val DEFAULT_SOURCE_TYPES = listOf("历年真题", "模拟卷", "习题集", "其他")
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * 学习流程重构 Sprint 3 Task 3.3：错题本入口 ViewModel。
 *
 * 承载「历史列表 ↔ 新增表单」两态 + 公式多选池（[FormulaIndex] 分组 + 未学灰显）+
 * 删除策略（每次询问 / 仅删记录 / 恢复计划）。写库统一经 [ErrorReportProcessor]
 * （插入时快照子卡，删除时按策略 best-effort 还原）。
 *
 * 表单草稿随本 VM 生命周期存活：跳七步仪式学未学公式再返回时（本页仍在返回栈），
 * 表单选择不丢。跨进程死亡的草稿持久化留作后续精修。
 */
class ErrorBookViewModel(
    private val repository: FormulaRepository,
    private val subCardStateDao: SubCardStateDao,
    private val errorReportDao: ErrorReportDao,
    private val appPreference: AppPreference,
    private val processor: ErrorReportProcessor,
    private val gson: Gson = Gson()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ErrorBookUiState())
    val uiState: StateFlow<ErrorBookUiState> = _uiState.asStateFlow()

    private val stringListType = object : TypeToken<List<String>>() {}.type

    init {
        // 列表 + 公式池索引：随 kaoyanSubject / 错题记录 / 子卡（已学判定）联动。
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            appPreference.settings
                .map { it.kaoyanSubject }
                .distinctUntilChanged()
                .flatMapLatest { subject ->
                    combine(
                        errorReportDao.observeAll(),
                        repository.observeFormulasFor(subject),
                        subCardStateDao.getAllStates()
                    ) { reports, poolFormulas, subCards ->
                        val titleById = poolFormulas.associate { it.formulaId to it.title }
                        val learnedIds = subCards.map { it.formulaId }.toSet()
                        val index = FormulaIndex.build(poolFormulas, learnedIds)
                        val rows = reports.map { report ->
                            val ids: List<String> = runCatching {
                                gson.fromJson<List<String>>(report.wrongFormulaIdsJson, stringListType)
                            }.getOrNull() ?: emptyList()
                            ErrorReportRow(
                                report = report,
                                formulaCount = ids.size,
                                formulaTitles = ids.map { titleById[it] ?: it }
                            )
                        }
                        Pair(rows, index)
                    }
                }
                .collect { (rows, index) ->
                    _uiState.update {
                        it.copy(rows = rows, formulaIndex = index, isLoading = false)
                    }
                }
        }
        // 删除策略独立订阅（不牵动公式池重订）。
        viewModelScope.launch {
            appPreference.settings
                .map { it.errorDeletePolicy }
                .distinctUntilChanged()
                .collect { policy -> _uiState.update { it.copy(deletePolicy = policy) } }
        }
    }

    // ── 表单导航 ──────────────────────────────────────────────────────────────

    /** FAB 点击：进新增表单（清空为空白草稿）。 */
    fun openForm() {
        _uiState.update { it.copy(mode = ErrorBookUiState.Mode.Form, form = ErrorFormState()) }
    }

    /** 取消 / 返回列表（丢弃当前草稿）。 */
    fun closeForm() {
        _uiState.update { it.copy(mode = ErrorBookUiState.Mode.List, form = ErrorFormState()) }
    }

    // ── 表单字段编辑 ────────────────────────────────────────────────────────────

    fun setSubject(subject: String) {
        _uiState.update {
            // 切 subject → 章节 chip 是 subject 专属，重置 chapter；保留已选公式。
            it.copy(form = it.form.copy(subject = subject, chapter = null))
        }
    }

    fun setChapter(chapter: String) {
        _uiState.update { it.copy(form = it.form.copy(chapter = chapter)) }
    }

    fun setSourceType(type: String) {
        _uiState.update { it.copy(form = it.form.copy(sourceType = type)) }
    }

    /** 数字键盘输入：只保留数字与连字符（受限编码如 2024-18）。 */
    fun setSourceTag(raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '-' }.take(20)
        _uiState.update { it.copy(form = it.form.copy(sourceTag = filtered)) }
    }

    fun toggleFormula(formulaId: String) {
        _uiState.update {
            val cur = it.form.selectedFormulaIds
            val next = if (formulaId in cur) cur - formulaId else cur + formulaId
            it.copy(form = it.form.copy(selectedFormulaIds = next))
        }
    }

    fun toggleShowAllSubjects() {
        _uiState.update { it.copy(form = it.form.copy(showAllSubjects = !it.form.showAllSubjects)) }
    }

    /** 提交错题：写库 + 施加惩罚，成功后回列表并清空草稿。 */
    fun submit() {
        val form = _uiState.value.form
        if (!form.canSubmit) return
        val settings = appPreference.settings.value
        viewModelScope.launch {
            processor.process(
                ErrorReportInput(
                    subject = form.subject!!,
                    chapter = form.chapter!!,
                    sourceType = form.sourceType!!,
                    sourceTag = form.sourceTag,
                    wrongFormulaIds = form.selectedFormulaIds.toList()
                ),
                hourOfDay = settings.dailyRefreshHourOfDay,
                minute = settings.dailyRefreshMinuteOfHour
            )
            _uiState.update {
                it.copy(mode = ErrorBookUiState.Mode.List, form = ErrorFormState())
            }
        }
    }

    // ── 删除 ────────────────────────────────────────────────────────────────

    /** 点删除：Ask → 弹窗；否则按已记忆策略直接执行。 */
    fun requestDelete(report: ErrorReportEntity) {
        when (_uiState.value.deletePolicy) {
            ErrorDeletePolicy.Ask -> _uiState.update { it.copy(pendingDelete = report) }
            ErrorDeletePolicy.DeleteOnly -> runDelete(report, restore = false)
            ErrorDeletePolicy.Restore -> runDelete(report, restore = true)
        }
    }

    /**
     * 对话框确认删除。
     * @param restore    true=恢复计划（best-effort 还原）/ false=仅删记录
     * @param rememberChoice 勾选「以后都这样」→ 持久化为对应策略
     */
    fun confirmDelete(report: ErrorReportEntity, restore: Boolean, rememberChoice: Boolean) {
        if (rememberChoice) {
            viewModelScope.launch {
                appPreference.setErrorDeletePolicy(
                    if (restore) ErrorDeletePolicy.Restore else ErrorDeletePolicy.DeleteOnly
                )
            }
        }
        runDelete(report, restore)
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    private fun runDelete(report: ErrorReportEntity, restore: Boolean) {
        viewModelScope.launch { processor.deleteReport(report, restore) }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                val processor = ErrorReportProcessor(db.errorReportDao(), db.subCardStateDao())
                return ErrorBookViewModel(
                    repository = FormulaRepository(app, db.formulaDao(), db.tagDao(), db.entryTagDao(), db.entryRelationDao()),
                    subCardStateDao = db.subCardStateDao(),
                    errorReportDao = db.errorReportDao(),
                    appPreference = AppContainer.appPreference(app),
                    processor = processor
                ) as T
            }
        }
    }
}
