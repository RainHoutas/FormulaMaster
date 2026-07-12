package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.local.dao.EntryRelationDao
import com.example.formulamaster.data.local.dao.ErrorReportDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.domain.EntryRelationType
import com.example.formulamaster.domain.ErrorMarkTally
import com.example.formulamaster.domain.SubCardAggregator
import com.example.formulamaster.domain.graph.ClusterOverviewLayout
import com.example.formulamaster.domain.graph.GraphEdge
import com.example.formulamaster.domain.graph.GraphModel
import com.example.formulamaster.domain.graph.GraphModelBuilder
import com.example.formulamaster.domain.graph.GraphNodeInput
import com.example.formulamaster.domain.graph.NodeState
import com.example.formulamaster.domain.graph.OverviewLayout
import com.example.formulamaster.domain.model.FormulaWithState
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

/**
 * 公式族图谱 · 渲染层数据源（Sprint 4 Task 4.2，RFC §9.4 D17）。
 *
 * 复用 [MemoryViewModel] 的 combine（公式按 kaoyanSubject 过滤 + 子卡聚合状态 + 错题 leech），
 * 再加读静态 `entry_relations`，经 [GraphModelBuilder] 装配成 [GraphModel]，母层布局由
 * [ClusterOverviewLayout] 派生。关系边随公式一起按当前 subject 过滤（剔除不可见公式的边）。
 */
data class GraphUiState(
    val model: GraphModel? = null,
    val overview: OverviewLayout? = null,
    val isLoading: Boolean = true
)

class GraphViewModel(
    private val repository: FormulaRepository,
    private val subCardStateDao: SubCardStateDao,
    private val errorReportDao: ErrorReportDao,
    private val entryRelationDao: EntryRelationDao,
    private val appPreference: AppPreference,
    private val gson: Gson = Gson()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private val stringListType = object : TypeToken<List<String>>() {}.type

    init {
        viewModelScope.launch {
            // 关系边是静态种子数据（运行期不变），一次读取即可
            val allEdges: List<GraphEdge> = entryRelationDao.getAll().mapNotNull { r ->
                EntryRelationType.fromCode(r.type)?.let { GraphEdge(r.fromId, r.toId, it) }
            }

            @OptIn(ExperimentalCoroutinesApi::class)
            appPreference.settings
                .map { it.kaoyanSubject }
                .distinctUntilChanged()
                .flatMapLatest { subject ->
                    combine(
                        repository.observeFormulasFor(subject),
                        subCardStateDao.getAllStates(),
                        errorReportDao.observeAll()
                    ) { formulas, subCards, reports ->
                        val derivedMap = SubCardAggregator.deriveAll(subCards)
                        val parsed = reports.map { it.createdAt to parseIds(it.wrongFormulaIdsJson) }
                        val markMap = ErrorMarkTally.countRecent(parsed, System.currentTimeMillis())
                        formulas.map { f ->
                            FormulaWithState(f, derivedMap[f.formulaId], markMap[f.formulaId] ?: 0)
                        }
                    }
                }
                .collect { list -> rebuild(list, allEdges) }
        }
    }

    private fun rebuild(list: List<FormulaWithState>, allEdges: List<GraphEdge>) {
        val nodes = list.map {
            GraphNodeInput(it.formula.formulaId, it.formula.title, it.formula.subject, it.formula.chapter)
        }
        val idSet = nodes.mapTo(HashSet()) { it.id }
        val edges = allEdges.filter { it.fromId in idSet && it.toId in idSet }   // 随 subject 过滤
        val stateById = list.associate {
            it.formula.formulaId to NodeState.of(it.isActivated, it.derived?.learningState)
        }
        val leech = list.filter { it.isLeech }.mapTo(HashSet()) { it.formula.formulaId }
        val model = GraphModelBuilder.build(nodes, edges, stateById, leech)
        val overview = ClusterOverviewLayout.layout(model.clusters, SUBJECT_ORDER)
        _uiState.update { GraphUiState(model, overview, isLoading = false) }
    }

    private fun parseIds(json: String): List<String> =
        runCatching { gson.fromJson<List<String>>(json, stringListType) }.getOrNull() ?: emptyList()

    companion object {
        private val SUBJECT_ORDER = listOf("高数", "线代", "概率论")

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val db = AppContainer.appDatabase(app)
                return GraphViewModel(
                    FormulaRepository(app, db.formulaDao(), db.tagDao(), db.entryTagDao(), db.entryRelationDao()),
                    db.subCardStateDao(),
                    db.errorReportDao(),
                    db.entryRelationDao(),
                    AppContainer.appPreference(app)
                ) as T
            }
        }
    }
}
