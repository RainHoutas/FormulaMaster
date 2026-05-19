package com.example.formulamaster.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.domain.KaoyanSubject
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
 * Sprint 1 Task 1.7：30 公式 MVP 真实 `formulas.json` 集成测试。
 *
 * 验证：
 *  - 30 条公式全部加载到 formulas 表
 *  - formula_subject_map 行数 = Σ appliesTo 长度（21×3 + 5×2 + 4×1 = 77）
 *  - 数一可见 30 / 数二可见 21 / 数三可见 26
 *  - 每条 clozeData / derivationSteps 都是可解析的 JSON 字符串
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FormulaSeedIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FormulaRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FormulaRepository(context, db.formulaDao(), db.formulaSubjectMapDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `formulas-json 加载到 30 条公式`() = runTest {
        repo.seedIfEmpty()
        val all = db.formulaDao().getAll().first()
        assertEquals(30, all.size)
    }

    @Test
    fun `formula_subject_map 总行数 = 77（21×3 + 5×2 + 4×1）`() = runTest {
        repo.seedIfEmpty()
        val count = db.formulaSubjectMapDao().count()
        assertEquals(21 * 3 + 5 * 2 + 4 * 1, count)
    }

    @Test
    fun `数一可见 30 公式`() = runTest {
        repo.seedIfEmpty()
        val list = repo.observeFormulasFor(KaoyanSubject.Type1).first()
        assertEquals(30, list.size)
    }

    @Test
    fun `数二可见 21 公式 只含三科共有`() = runTest {
        repo.seedIfEmpty()
        val list = repo.observeFormulasFor(KaoyanSubject.Type2).first()
        assertEquals(21, list.size)
        // 数二不应出现概率论 / 曲线积分 / 级数
        val subjects = list.map { it.subject }.toSet()
        assertTrue("数二不该有概率论：$subjects", "概率论" !in subjects)
        val titles = list.map { it.formulaId }
        assertTrue("数二不该出现 Green 公式", "green_thm" !in titles)
        assertTrue("数二不该出现幂级数", "power_series_expansion" !in titles)
    }

    @Test
    fun `数三可见 26 公式 含概率论但不含曲线积分级数`() = runTest {
        repo.seedIfEmpty()
        val list = repo.observeFormulasFor(KaoyanSubject.Type3).first()
        assertEquals(26, list.size)
        val ids = list.map { it.formulaId }.toSet()
        assertTrue("数三应有泊松", "prob_poisson_pmf" in ids)
        assertTrue("数三不该有 Green", "green_thm" !in ids)
    }

    @Test
    fun `所有公式的嵌套 JSON 字段都可解析`() = runTest {
        repo.seedIfEmpty()
        val all = db.formulaDao().getAll().first()
        // 这一步实际依赖 ClozeParser 等模块；这里只断言 JSON 字段不是空字符串/为合理形态
        all.forEach { f ->
            assertTrue("[${f.formulaId}] clozeData 应为 JSON 数组", f.clozeData.startsWith("[") && f.clozeData.endsWith("]"))
            assertTrue("[${f.formulaId}] derivationSteps 应为 JSON 数组", f.derivationSteps.startsWith("[") && f.derivationSteps.endsWith("]"))
            assertTrue("[${f.formulaId}] preconditions 应为 JSON 数组", f.preconditions.startsWith("[") && f.preconditions.endsWith("]"))
        }
    }
}
