package com.example.formulamaster.data.repository

import android.content.Context
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.FormulaSubjectMapDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.FormulaSubjectMapEntity
import com.example.formulamaster.domain.KaoyanSubject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FormulaRepository(
    private val context: Context,
    private val formulaDao: FormulaDao,
    private val formulaSubjectMapDao: FormulaSubjectMapDao
) {

    fun getAll(): Flow<List<FormulaEntity>> = formulaDao.getAll()

    suspend fun getById(id: String): FormulaEntity? = formulaDao.getById(id)

    /**
     * 学习流程重构 Sprint 1 Task 1.3 — 按考研数学子科目过滤公式列表。
     *
     * 通过 [FormulaDao.observeByKaoyanSubject] 的 JOIN 实现，map 表为 source of truth。
     * 切换 [subject] 后 Flow 立即推送新结果（DataStore Flow → flatMapLatest 的常规 UDF 链路）。
     */
    fun observeFormulasFor(subject: KaoyanSubject): Flow<List<FormulaEntity>> =
        formulaDao.observeByKaoyanSubject(subject.code)

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (formulaDao.count() > 0) return@withContext
        val json = context.assets.open("formulas.json").bufferedReader().readText()
        val seedType = object : TypeToken<List<FormulaSeed>>() {}.type
        val seeds: List<FormulaSeed> = Gson().fromJson(json, seedType)

        formulaDao.insertAll(seeds.map { it.toEntity() })
        // Sprint 1 Task 1.3：同时写入子科目映射。
        // appliesTo 为空时默认数一二三都适用，避免漏标导致用户看不到公式。
        // 严格标注由 Task 1.7（30 公式 MVP）完成。
        formulaSubjectMapDao.insertAll(seeds.flatMap { it.toMapRows() })
    }

    /**
     * 仅用于解析 `formulas.json`：包含 [FormulaEntity] 全部字段 + 多对多映射 [appliesTo]。
     *
     * 单独类型避免把 `appliesTo` 加入 FormulaEntity 造成冗余列 / 与 map 表数据源不一致。
     * Gson 反序列化使用字段默认值兜底 JSON 漏字段。
     */
    private data class FormulaSeed(
        val formulaId: String,
        val subject: String,
        val chapter: String,
        val title: String,
        val latexCode: String,
        val clozeData: String,
        val derivationSteps: String,
        val tags: String,
        val difficultyLevel: Int,
        val purpose: String = "",
        val preconditions: String = "[]",
        val parents: String = "[]",
        val siblings: String = "[]",
        val confusableWith: String = "[]",
        val typicalProblems: String = "[]",
        val commonErrors: String = "[]",
        val mnemonic: String? = null,
        val examWeight: Int = 3,
        val scene: String = "KaoyanMath",
        /**
         * 该公式适用的数学子科目 code 列表（与 [KaoyanSubject.code] 对齐）。
         * 空列表 / 缺字段 → 默认数一二三都适用（保守兜底）。
         */
        val appliesTo: List<String> = emptyList()
    ) {
        fun toEntity() = FormulaEntity(
            formulaId = formulaId,
            subject = subject,
            chapter = chapter,
            title = title,
            latexCode = latexCode,
            clozeData = clozeData,
            derivationSteps = derivationSteps,
            tags = tags,
            difficultyLevel = difficultyLevel,
            purpose = purpose,
            preconditions = preconditions,
            parents = parents,
            siblings = siblings,
            confusableWith = confusableWith,
            typicalProblems = typicalProblems,
            commonErrors = commonErrors,
            mnemonic = mnemonic,
            examWeight = examWeight,
            scene = scene
        )

        fun toMapRows(): List<FormulaSubjectMapEntity> {
            val codes = appliesTo.ifEmpty { KaoyanSubject.entries.map { it.code } }
            return codes.map { FormulaSubjectMapEntity(formulaId, it) }
        }
    }
}
