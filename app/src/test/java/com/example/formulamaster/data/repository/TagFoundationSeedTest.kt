package com.example.formulamaster.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.domain.EntryRelationType
import com.example.formulamaster.domain.TagNamespace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 4 Task 4.1/4.3：标签/关系地基的种子拆分校验。
 *
 * 用真实 `formulas.json` 跑一遍 [FormulaRepository.seedIfEmpty] 后，断言拆分出的
 * 原子标签 + 关系边符合地基不变式：主标签唯一、namespace 完整、防悬空、无向规范化去重。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TagFoundationSeedTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FormulaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FormulaRepository(context, db.formulaDao(), db.tagDao(), db.entryTagDao(), db.entryRelationDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `每条公式恰好一个主学科 + 一个主章节标签`() = runTest {
        repo.seedIfEmpty()
        val formulaIds = db.formulaDao().getAll().first().map { it.formulaId }
        val tagsById = db.tagDao().getAll().associateBy { it.tagId }
        formulaIds.forEach { id ->
            val rows = db.entryTagDao().getByEntry(id)
            val primarySubjects = rows.filter { it.isPrimary && tagsById[it.tagId]?.namespace == TagNamespace.SUBJECT }
            val primaryChapters = rows.filter { it.isPrimary && tagsById[it.tagId]?.namespace == TagNamespace.CHAPTER }
            assertEquals("[$id] 应恰好 1 个主学科标签", 1, primarySubjects.size)
            assertEquals("[$id] 应恰好 1 个主章节标签", 1, primaryChapters.size)
        }
    }

    @Test
    fun `主标签值与 formulas 表显示缓存列一致`() = runTest {
        repo.seedIfEmpty()
        val formulas = db.formulaDao().getAll().first()
        val tagsById = db.tagDao().getAll().associateBy { it.tagId }
        formulas.forEach { f ->
            val rows = db.entryTagDao().getByEntry(f.formulaId)
            val subjTag = rows.first { it.isPrimary && tagsById[it.tagId]?.namespace == TagNamespace.SUBJECT }
            val chapTag = rows.first { it.isPrimary && tagsById[it.tagId]?.namespace == TagNamespace.CHAPTER }
            assertEquals("[${f.formulaId}] 主学科标签应与缓存列一致", f.subject, tagsById[subjTag.tagId]?.value)
            assertEquals("[${f.formulaId}] 主章节标签应与缓存列一致", f.chapter, tagsById[chapTag.tagId]?.value)
        }
    }

    @Test
    fun `所有 tagId 遵循 namespace冒号value 约定且 namespace 属已知集`() = runTest {
        repo.seedIfEmpty()
        val known = setOf(TagNamespace.SUBJECT, TagNamespace.CHAPTER, TagNamespace.EXAM, TagNamespace.KEYWORD)
        db.tagDao().getAll().forEach { t ->
            assertEquals("[${t.tagId}] tagId 应为 namespace:value", TagNamespace.tagId(t.namespace, t.value), t.tagId)
            assertTrue("[${t.tagId}] namespace 应属已知集: ${t.namespace}", t.namespace in known)
        }
    }

    @Test
    fun `关系边无悬空 id 无自环`() = runTest {
        repo.seedIfEmpty()
        val ids = db.formulaDao().getAll().first().map { it.formulaId }.toSet()
        db.entryRelationDao().getAll().forEach { e ->
            assertTrue("[${e.fromId}→${e.toId}] fromId 悬空", e.fromId in ids)
            assertTrue("[${e.fromId}→${e.toId}] toId 悬空", e.toId in ids)
            assertTrue("[${e.fromId}→${e.toId}] 不应有自环", e.fromId != e.toId)
        }
    }

    @Test
    fun `无向关系已按字典序规范化且无重复`() = runTest {
        repo.seedIfEmpty()
        val undirected = db.entryRelationDao().getAll()
            .filter { EntryRelationType.fromCode(it.type)?.directed == false }
        undirected.forEach { e ->
            assertTrue("[${e.fromId}~${e.toId}] 无向边应 fromId<=toId（规范化）", e.fromId <= e.toId)
        }
        // 主键 (fromId,toId,type) 天然去重，这里再断言无 (无序对+type) 重复
        val seen = undirected.map { Triple(it.fromId, it.toId, it.type) }
        assertEquals("无向边不应有重复", seen.size, seen.toSet().size)
    }

    @Test
    fun `推导边为有向 且方向为 子到父（fromId 由 toId 推导）`() = runTest {
        repo.seedIfEmpty()
        val derivations = db.entryRelationDao().getByType(EntryRelationType.DERIVATION.code)
        assertTrue("应存在推导边", derivations.isNotEmpty())
        // 已知种子：拉格朗日(lagrange_thm) 由 罗尔(rolle_thm) 推导
        assertTrue(
            "应含 lagrange_thm → rolle_thm 有向推导边",
            derivations.any { it.fromId == "lagrange_thm" && it.toId == "rolle_thm" }
        )
    }
}
