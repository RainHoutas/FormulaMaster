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
 * Sprint 1 Task 1.8：FormulaRepository 集成行为单测。
 *
 * 关注点：
 *  - seedIfEmpty 幂等：多次调用不会重复插入或重复写 map 行
 *  - observeFormulasFor 在切换 [KaoyanSubject] 时正确收敛
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FormulaRepositoryTest {

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
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seedIfEmpty 二次调用不重复插入 formulas`() = runTest {
        repo.seedIfEmpty()
        val firstCount = db.formulaDao().count()
        assertEquals(30, firstCount)

        // 二次调用应直接 return（count > 0）
        repo.seedIfEmpty()
        val secondCount = db.formulaDao().count()
        assertEquals("seedIfEmpty 应幂等，二次调用 count 不变",
            firstCount, secondCount)
    }

    @Test
    fun `seedIfEmpty 二次调用不重复写 exam 标签映射`() = runTest {
        repo.seedIfEmpty()
        val firstMapCount = db.entryTagDao().countByNamespace("exam")
        // 21×3 + 5×2 + 4×1 = 77
        assertEquals(77, firstMapCount)

        repo.seedIfEmpty()
        val secondMapCount = db.entryTagDao().countByNamespace("exam")
        assertEquals("seedIfEmpty 应幂等，二次调用 exam 标签行不变",
            firstMapCount, secondMapCount)
    }

    @Test
    fun `observeFormulasFor Type1 vs Type2 数量差异符合预期（30 vs 21）`() = runTest {
        repo.seedIfEmpty()
        val type1 = repo.observeFormulasFor(KaoyanSubject.Type1).first()
        val type2 = repo.observeFormulasFor(KaoyanSubject.Type2).first()
        val type3 = repo.observeFormulasFor(KaoyanSubject.Type3).first()

        assertEquals(30, type1.size)
        assertEquals(21, type2.size)
        assertEquals(26, type3.size)

        // 数二是数一的真子集
        val type1Ids = type1.map { it.formulaId }.toSet()
        val type2Ids = type2.map { it.formulaId }.toSet()
        assertTrue("数二应为数一子集", type2Ids.all { it in type1Ids })
    }
}
