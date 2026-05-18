package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.FormulaSubjectMapDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.FormulaSubjectMapEntity
import com.example.formulamaster.domain.KaoyanSubject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 学习流程重构 Sprint 1 Task 1.3 — FormulaSubjectMap + FormulaDao.observeByKaoyanSubject 验证。
 *
 * 使用 Robolectric 提供 Android Context，跑 `Room.inMemoryDatabaseBuilder` 真实 SQLite，
 * 验证 JOIN 过滤逻辑 + 三种 KaoyanSubject 各自返回正确公式集。
 *
 * Seed 集与 `assets/formulas.json` 同构（无需读 asset 文件）：
 * - 4 个公共公式（高数 3 + 线代 1）：appliesTo = ["1","2","3"]
 * - 2 个概率论公式：appliesTo = ["1","3"]（数二不考）
 *
 * 预期：Type1 → 6 / Type2 → 4 / Type3 → 6。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FormulaSubjectMapDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var formulaDao: FormulaDao
    private lateinit var mapDao: FormulaSubjectMapDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        formulaDao = db.formulaDao()
        mapDao = db.formulaSubjectMapDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        formulaDao.insertAll(
            listOf(
                fakeFormula("calc_taylor_01",          "高数"),
                fakeFormula("calc_lhopital_01",        "高数"),
                fakeFormula("calc_newton_leibniz_01",  "高数"),
                fakeFormula("la_det_expansion_01",     "线代"),
                fakeFormula("prob_total_prob_01",      "概率论"),
                fakeFormula("prob_expectation_01",     "概率论"),
            )
        )
        // 4 个高数 + 线代公共题（三科都考）
        mapDao.insertAll(
            listOf("calc_taylor_01", "calc_lhopital_01", "calc_newton_leibniz_01", "la_det_expansion_01")
                .flatMap { id -> listOf("1", "2", "3").map { FormulaSubjectMapEntity(id, it) } }
        )
        // 2 个概率论（数一 + 数三，数二不考）
        mapDao.insertAll(
            listOf("prob_total_prob_01", "prob_expectation_01")
                .flatMap { id -> listOf("1", "3").map { FormulaSubjectMapEntity(id, it) } }
        )
    }

    @Test
    fun `数一应返回全部 6 个公式`() = runTest {
        seed()
        val list = formulaDao.observeByKaoyanSubject(KaoyanSubject.Type1.code).first()
        assertEquals(6, list.size)
        val ids = list.map { it.formulaId }.toSet()
        assertEquals(
            setOf(
                "calc_taylor_01", "calc_lhopital_01", "calc_newton_leibniz_01",
                "la_det_expansion_01", "prob_total_prob_01", "prob_expectation_01"
            ),
            ids
        )
    }

    @Test
    fun `数二应过滤掉概率论 仅返回 4 个公式`() = runTest {
        seed()
        val list = formulaDao.observeByKaoyanSubject(KaoyanSubject.Type2.code).first()
        assertEquals(4, list.size)
        val ids = list.map { it.formulaId }.toSet()
        assertEquals(
            setOf(
                "calc_taylor_01", "calc_lhopital_01", "calc_newton_leibniz_01",
                "la_det_expansion_01"
            ),
            ids
        )
    }

    @Test
    fun `数三应返回全部 6 个公式`() = runTest {
        seed()
        val list = formulaDao.observeByKaoyanSubject(KaoyanSubject.Type3.code).first()
        assertEquals(6, list.size)
    }

    @Test
    fun `空映射表时查询应返回空列表`() = runTest {
        // 只插公式不插映射
        formulaDao.insertAll(listOf(fakeFormula("orphan", "高数")))
        val list = formulaDao.observeByKaoyanSubject(KaoyanSubject.Type1.code).first()
        assertEquals(0, list.size)
    }

    @Test
    fun `公式属于多 subject 时 JOIN 不重复（DISTINCT 验证）`() = runTest {
        formulaDao.insertAll(listOf(fakeFormula("multi", "高数")))
        mapDao.insertAll(
            listOf(
                FormulaSubjectMapEntity("multi", "1"),
                FormulaSubjectMapEntity("multi", "2"),
                FormulaSubjectMapEntity("multi", "3"),
            )
        )
        val list = formulaDao.observeByKaoyanSubject("1").first()
        assertEquals(1, list.size)
        assertEquals("multi", list.first().formulaId)
    }

    @Test
    fun `级联删除：删 FormulaEntity 时映射行同步消失`() = runTest {
        seed()
        val before = mapDao.getByFormulaId("calc_taylor_01")
        assertEquals(3, before.size) // 数一二三都有
        // 通过 DROP 模拟外键 ON DELETE CASCADE（Room 默认 PRAGMA foreign_keys = ON）
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM formulas WHERE formulaId = 'calc_taylor_01'"
        )
        val after = mapDao.getByFormulaId("calc_taylor_01")
        assertEquals(0, after.size)
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private fun fakeFormula(id: String, subject: String) = FormulaEntity(
        formulaId = id,
        subject = subject,
        chapter = "test",
        title = "test-$id",
        latexCode = "x",
        clozeData = "[]",
        derivationSteps = "[]",
        tags = "",
        difficultyLevel = 1
    )
}
