package com.example.formulamaster.data.repository

import android.content.Context
import com.example.formulamaster.data.local.dao.EntryRelationDao
import com.example.formulamaster.data.local.dao.EntryTagDao
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.TagDao
import com.example.formulamaster.data.local.entity.EntryRelationEntity
import com.example.formulamaster.data.local.entity.EntryTagCrossRef
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.TagEntity
import com.example.formulamaster.domain.EntryRelationType
import com.example.formulamaster.domain.KaoyanSubject
import com.example.formulamaster.domain.TagNamespace
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 公式静态库仓库。
 *
 * Sprint 4 Task 4.1（RFC §9.4 D16）：种子加载改为把每条公式**拆成原子标签行 + 关系边**，
 * 写入统一标签体系（`tags` / `entry_tag_map`）与关系表（`entry_relations`）。
 * `formula_subject_map` 已退休，数一二三并入 namespace=exam 标签。
 */
class FormulaRepository(
    private val context: Context,
    private val formulaDao: FormulaDao,
    private val tagDao: TagDao,
    private val entryTagDao: EntryTagDao,
    private val entryRelationDao: EntryRelationDao
) {

    fun getAll(): Flow<List<FormulaEntity>> = formulaDao.getAll()

    suspend fun getById(id: String): FormulaEntity? = formulaDao.getById(id)

    /**
     * 与某公式相连的**易混（CONFUSABLE）邻居公式**（无向边，取对端 id 再查实体）。
     * 供 C5 易混辨析卡凑干扰项池。无易混边时返回空列表。
     */
    suspend fun confusableNeighbors(formulaId: String): List<FormulaEntity> = withContext(Dispatchers.IO) {
        val neighborIds = entryRelationDao.getTouching(formulaId)
            .filter { it.type == EntryRelationType.CONFUSABLE.code }
            .map { if (it.fromId == formulaId) it.toId else it.fromId }
            .filter { it != formulaId }
            .distinct()
        neighborIds.mapNotNull { formulaDao.getById(it) }
    }

    /** 全部含易混边的公式 id 集合（供 C5 gate：无易混邻居的公式不出 C5）。 */
    suspend fun formulaIdsWithConfusable(): Set<String> = withContext(Dispatchers.IO) {
        entryRelationDao.getByType(EntryRelationType.CONFUSABLE.code)
            .flatMap { listOf(it.fromId, it.toId) }
            .toSet()
    }

    /**
     * 按考研数学子科目过滤公式列表（Sprint 4 起走 namespace=exam 标签 JOIN）。
     * 切换 [subject] 后 Flow 立即推送新结果。
     */
    fun observeFormulasFor(subject: KaoyanSubject): Flow<List<FormulaEntity>> =
        formulaDao.observeByKaoyanSubject(subject.code)

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (formulaDao.count() > 0) return@withContext
        val json = context.assets.open("formulas.json").bufferedReader().readText()
        val seedType = object : TypeToken<List<FormulaSeed>>() {}.type
        val seeds: List<FormulaSeed> = gson.fromJson(json, seedType)

        // 1) 公式本体（关系边的外键目标，必须先写）
        formulaDao.insertAll(seeds.map { it.toEntity() })

        // 2) 标签（entry_tag_map 的外键目标，去重后写）——分类唯一真相源
        val allTags = seeds.flatMap { it.toTagRows() }.distinctBy { it.tagId }
        tagDao.insertAll(allTags)

        // 3) 词条 ↔ 标签映射
        entryTagDao.insertAll(seeds.flatMap { it.toEntryTagRows() })

        // 4) 关系边——防悬空：两端 id 必须都在种子集合内；自环剔除；无向已在拆分时规范化
        val idSet = seeds.mapTo(HashSet()) { it.formulaId }
        val relations = seeds.flatMap { it.toRelationRows(::parseIdList) }
            .filter { it.fromId != it.toId && it.fromId in idSet && it.toId in idSet }
            .distinctBy { Triple(it.fromId, it.toId, it.type) }
        entryRelationDao.insertAll(relations)
    }

    private fun parseIdList(jsonArray: String): List<String> =
        runCatching {
            gson.fromJson<List<String>>(jsonArray, object : TypeToken<List<String>>() {}.type)
        }.getOrNull() ?: emptyList()

    /**
     * 仅用于解析 `formulas.json`：含 [FormulaEntity] 内容字段 + 分类/关系的**录入格式**字段
     * （[appliesTo] / [parents] / [siblings] / [confusableWith] / [tags]）。
     * 这些录入字段在加载时被拆成原子标签行 + 关系边，不再落到 formulas 表列上。
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
        val typicalProblems: String = "[]",
        val commonErrors: String = "[]",
        val mnemonic: String? = null,
        val chunks: String = "[]",
        val examWeight: Int = 3,
        val scene: String = "KaoyanMath",
        // ── 录入格式：分类 / 关系（加载时拆原子，不落 formulas 列）────────
        val parents: String = "[]",
        val siblings: String = "[]",
        val confusableWith: String = "[]",
        /** 适用数学子科目 code 列表（与 [KaoyanSubject.code] 对齐）。空 → 默认三科都适用。 */
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
            typicalProblems = typicalProblems,
            commonErrors = commonErrors,
            mnemonic = mnemonic,
            chunks = chunks,
            examWeight = examWeight,
            scene = scene
        )

        private fun examCodes(): List<String> =
            appliesTo.ifEmpty { KaoyanSubject.entries.map { it.code } }

        private fun keywords(): List<String> =
            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        /** 本词条产生的所有标签定义（跨词条去重由调用方按 tagId 完成）。 */
        fun toTagRows(): List<TagEntity> = buildList {
            add(TagEntity(TagNamespace.tagId(TagNamespace.SUBJECT, subject), TagNamespace.SUBJECT, subject, subject))
            add(TagEntity(TagNamespace.tagId(TagNamespace.CHAPTER, chapter), TagNamespace.CHAPTER, chapter, chapter))
            examCodes().forEach { code ->
                val name = KaoyanSubject.fromCode(code)?.displayName ?: code
                add(TagEntity(TagNamespace.tagId(TagNamespace.EXAM, code), TagNamespace.EXAM, code, name))
            }
            keywords().forEach { kw ->
                add(TagEntity(TagNamespace.tagId(TagNamespace.KEYWORD, kw), TagNamespace.KEYWORD, kw, kw))
            }
        }

        /** 本词条的 ↔ 标签映射；subject/chapter 主标签 isPrimary=true。 */
        fun toEntryTagRows(): List<EntryTagCrossRef> = buildList {
            add(EntryTagCrossRef(formulaId, TagNamespace.tagId(TagNamespace.SUBJECT, subject), isPrimary = true))
            add(EntryTagCrossRef(formulaId, TagNamespace.tagId(TagNamespace.CHAPTER, chapter), isPrimary = true))
            examCodes().forEach {
                add(EntryTagCrossRef(formulaId, TagNamespace.tagId(TagNamespace.EXAM, it), isPrimary = false))
            }
            keywords().forEach {
                add(EntryTagCrossRef(formulaId, TagNamespace.tagId(TagNamespace.KEYWORD, it), isPrimary = false))
            }
        }

        /** 本词条产生的关系边（有向推导按 from→to；无向易混/同族按字典序规范化去重）。 */
        fun toRelationRows(parse: (String) -> List<String>): List<EntryRelationEntity> = buildList {
            parse(parents).forEach { parentId ->
                // 推导有向：本词条(from) 由 上游(to) 推导得到
                add(EntryRelationEntity(formulaId, parentId, EntryRelationType.DERIVATION.code))
            }
            parse(confusableWith).forEach { other ->
                add(undirected(formulaId, other, EntryRelationType.CONFUSABLE.code))
            }
            parse(siblings).forEach { other ->
                add(undirected(formulaId, other, EntryRelationType.SIBLING.code))
            }
        }

        private fun undirected(a: String, b: String, type: String): EntryRelationEntity {
            val (lo, hi) = if (a <= b) a to b else b to a
            return EntryRelationEntity(lo, hi, type)
        }
    }

    companion object {
        private val gson = Gson()
    }
}
