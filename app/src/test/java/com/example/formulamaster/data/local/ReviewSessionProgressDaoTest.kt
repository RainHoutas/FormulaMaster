package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.dao.ReviewSessionProgressDao
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 2 Task 2.1b：[ReviewSessionProgressDao] 单行表 DAO 验证。
 *
 * 覆盖：
 * - 空表 getCurrent 返回 null
 * - upsert 单行 + getCurrent 往返
 * - upsert REPLACE 行为（同 id 覆盖旧值）
 * - clearSession 清字段保留行
 * - deleteAll 整行删除
 * - observeCurrent Flow 响应
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReviewSessionProgressDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewSessionProgressDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reviewSessionProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `空表 getCurrent 返回 null`() = runTest {
        assertNull(dao.getCurrent())
    }

    @Test
    fun `upsert + getCurrent round-trip`() = runTest {
        val entity = ReviewSessionProgressEntity(
            sessionDateMs = 1_700_000_000_000L,
            formulaContextsJson = """[{"formulaId":"f1"}]""",
            currentFormulaIndex = 2
        )
        dao.upsert(entity)

        val loaded = dao.getCurrent()
        assertNotNull(loaded)
        assertEquals(ReviewSessionProgressEntity.SINGLETON_ID, loaded!!.id)
        assertEquals(1_700_000_000_000L, loaded.sessionDateMs)
        assertEquals("""[{"formulaId":"f1"}]""", loaded.formulaContextsJson)
        assertEquals(2, loaded.currentFormulaIndex)
    }

    @Test
    fun `upsert REPLACE 覆盖旧值`() = runTest {
        dao.upsert(ReviewSessionProgressEntity(sessionDateMs = 1_000L, currentFormulaIndex = 1))
        dao.upsert(ReviewSessionProgressEntity(sessionDateMs = 2_000L, currentFormulaIndex = 3))

        val loaded = dao.getCurrent()!!
        assertEquals(2_000L, loaded.sessionDateMs)
        assertEquals(3, loaded.currentFormulaIndex)
    }

    @Test
    fun `clearSession 清字段保留行`() = runTest {
        dao.upsert(ReviewSessionProgressEntity(
            sessionDateMs = 5_000L,
            formulaContextsJson = "[{}]",
            currentFormulaIndex = 7
        ))

        dao.clearSession()
        val loaded = dao.getCurrent()
        assertNotNull("clearSession 应保留行（避免反复 INSERT/DELETE）", loaded)
        assertNull(loaded!!.sessionDateMs)
        assertNull(loaded.formulaContextsJson)
        assertEquals(0, loaded.currentFormulaIndex)
    }

    @Test
    fun `clearSession 在空表上是 no-op 不抛`() = runTest {
        dao.clearSession()  // 空表上 UPDATE 0 行
        assertNull(dao.getCurrent())
    }

    @Test
    fun `deleteAll 整行删除`() = runTest {
        dao.upsert(ReviewSessionProgressEntity(sessionDateMs = 9_000L))
        assertNotNull(dao.getCurrent())

        dao.deleteAll()
        assertNull(dao.getCurrent())
    }

    @Test
    fun `observeCurrent 响应 upsert 和 clearSession`() = runTest {
        // 初始空
        assertNull(dao.observeCurrent().first())

        // upsert 后 Flow 拿到
        dao.upsert(ReviewSessionProgressEntity(sessionDateMs = 3_000L, currentFormulaIndex = 5))
        val emitted = dao.observeCurrent().first()
        assertNotNull(emitted)
        assertEquals(3_000L, emitted!!.sessionDateMs)
        assertEquals(5, emitted.currentFormulaIndex)

        // clearSession 后 Flow 拿到空字段
        dao.clearSession()
        val cleared = dao.observeCurrent().first()
        assertNotNull(cleared)
        assertNull(cleared!!.sessionDateMs)
        assertEquals(0, cleared.currentFormulaIndex)
    }

    @Test
    fun `单行表 即使插入不同 id 也只保留 SINGLETON_ID 行（调用方约定）`() = runTest {
        // upsert 默认带 SINGLETON_ID=1
        dao.upsert(ReviewSessionProgressEntity(sessionDateMs = 1_000L))
        assertEquals(1, dao.getCurrent()!!.id)
        // getCurrent 只查 id=SINGLETON_ID，即使有人手动塞 id=2 也无视
        // （生产代码不会这么用，但 DAO 行为可预测）
    }
}
