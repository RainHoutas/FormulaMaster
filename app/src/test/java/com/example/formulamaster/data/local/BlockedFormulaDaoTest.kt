package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.dao.BlockedFormulaDao
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import com.example.formulamaster.data.local.entity.FormulaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 2 Task 2.1b：BlockedFormulaDao + BlockedFormulaEntity 验证。
 *
 * 覆盖：
 * - upsert / deleteById / getAllIds 基本 CRUD
 * - REPLACE 策略：同一公式重复 upsert 刷新 blockedAt
 * - CASCADE：FormulaEntity 删除时同步清理
 * - observeByFormulaId Flow 实时性
 * - count 计数（潜在 7 日内统计接入点）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BlockedFormulaDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var formulaDao: FormulaDao
    private lateinit var dao: BlockedFormulaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        formulaDao = db.formulaDao()
        dao = db.blockedFormulaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert 单条 + getAllIds 往返`() = runTest {
        seedFormula("f1")
        dao.upsert(BlockedFormulaEntity("f1", blockedAt = 1_000L))
        val ids = dao.getAllIds()
        assertEquals(listOf("f1"), ids)
    }

    @Test
    fun `REPLACE 策略 重复 upsert 刷新 blockedAt`() = runTest {
        seedFormula("f1")
        dao.upsert(BlockedFormulaEntity("f1", blockedAt = 1_000L))
        dao.upsert(BlockedFormulaEntity("f1", blockedAt = 2_500L))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(2_500L, all[0].blockedAt)
        assertEquals(1, dao.count())
    }

    @Test
    fun `deleteById 公式 Graduated 时清除标志`() = runTest {
        seedFormula("f1"); seedFormula("f2")
        dao.upsert(BlockedFormulaEntity("f1", 1_000L))
        dao.upsert(BlockedFormulaEntity("f2", 2_000L))
        assertEquals(2, dao.count())

        val deleted = dao.deleteById("f1")
        assertEquals(1, deleted)
        assertEquals(listOf("f2"), dao.getAllIds())
    }

    @Test
    fun `deleteById 不存在返回 0`() = runTest {
        seedFormula("f1")
        assertEquals(0, dao.deleteById("nonexistent"))
    }

    @Test
    fun `getAll 按 blockedAt DESC 排序`() = runTest {
        seedFormula("f1"); seedFormula("f2"); seedFormula("f3")
        dao.upsert(BlockedFormulaEntity("f1", 1_000L))
        dao.upsert(BlockedFormulaEntity("f2", 3_000L))
        dao.upsert(BlockedFormulaEntity("f3", 2_000L))

        val ordered = dao.getAll().map { it.formulaId }
        assertEquals(listOf("f2", "f3", "f1"), ordered)
    }

    @Test
    fun `CASCADE 删除 FormulaEntity 同步清理 blocked 标志`() = runTest {
        seedFormula("f1")
        dao.upsert(BlockedFormulaEntity("f1", 1_000L))
        assertEquals(1, dao.count())

        db.openHelper.writableDatabase.execSQL("DELETE FROM formulas WHERE formulaId = 'f1'")
        assertEquals(0, dao.count())
    }

    @Test
    fun `observeByFormulaId 实时响应 upsert 和 delete`() = runTest {
        seedFormula("f1")
        // 初始无记录
        assertNull(dao.observeByFormulaId("f1").first())

        // 写入后 Flow 拿到
        dao.upsert(BlockedFormulaEntity("f1", 1_500L))
        val emitted = dao.observeByFormulaId("f1").first()
        assertNotNull(emitted)
        assertEquals(1_500L, emitted!!.blockedAt)

        // 删除后 Flow 拿到 null
        dao.deleteById("f1")
        assertNull(dao.observeByFormulaId("f1").first())
    }

    @Test
    fun `getAllIds 空表返回空 List`() = runTest {
        val ids = dao.getAllIds()
        assertTrue(ids.isEmpty())
        assertEquals(0, dao.count())
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private suspend fun seedFormula(id: String) =
        formulaDao.insertAll(listOf(fakeFormula(id)))

    private fun fakeFormula(id: String) = FormulaEntity(
        formulaId = id,
        subject = "高数",
        chapter = "test",
        title = "test-$id",
        latexCode = "x",
        clozeData = "[]",
        derivationSteps = "[]",
        tags = "",
        difficultyLevel = 1
    )
}
