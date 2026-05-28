package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.domain.CardType
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
 * 学习流程重构 Sprint 1 Task 1.5 — SubCardStateDao + SubCardStateEntity 验证。
 *
 * 用 Robolectric 起 in-memory Room，验证：
 *  - 复合主键 (formulaId, cardType) 唯一性
 *  - 30 公式 × 6 卡型 = 180 条记录可建可查
 *  - 按 formulaId 取 6 张子卡的顺序与完整性
 *  - CASCADE 删除：FormulaEntity 删除时同步清理子卡
 *  - applyErrorReportPenalty：批量改 stability + nextReviewTime + lapses+1（供 Task 1.6 用）
 *  - getTodayReviewQueue：仅返回 nextReviewTime ≤ now 的子卡
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubCardStateDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var formulaDao: FormulaDao
    private lateinit var dao: SubCardStateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        formulaDao = db.formulaDao()
        dao = db.subCardStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `单条子卡 insert get 往返`() = runTest {
        seedFormula("calc_taylor_01")
        val s = fakeSubCard("calc_taylor_01", CardType.C1_Recognition)
        dao.insert(s)

        val loaded = dao.get("calc_taylor_01", CardType.C1_Recognition.code)
        assertNotNull(loaded)
        assertEquals(s.stability, loaded!!.stability, 1e-9)
        assertEquals("c1", loaded.cardType)
    }

    @Test
    fun `30 公式 × 6 卡 = 180 条记录可建可查`() = runTest {
        // 插入 30 公式
        val formulaIds = (1..30).map { "f_%02d".format(it) }
        formulaDao.insertAll(formulaIds.map { fakeFormula(it) })

        // 每条公式 6 张子卡
        val all = formulaIds.flatMap { id ->
            CardType.entries.map { fakeSubCard(id, it) }
        }
        dao.insertAll(all)

        val states = dao.getAllStatesOnce()
        assertEquals(180, states.size)

        // 随抽 1 条公式验证 6 张子卡齐全且按 cardType 升序
        val sub = dao.getByFormulaId("f_07")
        assertEquals(6, sub.size)
        assertEquals(
            listOf("c1", "c2", "c3", "c4", "c5", "c6"),
            sub.map { it.cardType }
        )
    }

    @Test
    fun `复合主键冲突时 IGNORE 不抛`() = runTest {
        seedFormula("calc_taylor_01")
        val a = fakeSubCard("calc_taylor_01", CardType.C1_Recognition, stability = 2.0)
        val b = fakeSubCard("calc_taylor_01", CardType.C1_Recognition, stability = 99.0)
        dao.insert(a)
        dao.insert(b) // 同复合主键，IGNORE

        val loaded = dao.get("calc_taylor_01", CardType.C1_Recognition.code)
        // 保留首次插入的 stability（IGNORE 语义）
        assertEquals(2.0, loaded!!.stability, 1e-9)
    }

    @Test
    fun `不同 cardType 同 formulaId 可共存`() = runTest {
        seedFormula("f1")
        dao.insertAll(CardType.entries.map { fakeSubCard("f1", it) })
        assertEquals(6, dao.getByFormulaId("f1").size)
    }

    @Test
    fun `CASCADE 删除 FormulaEntity 时子卡同步消失`() = runTest {
        seedFormula("f1")
        dao.insertAll(CardType.entries.map { fakeSubCard("f1", it) })
        assertEquals(6, dao.getByFormulaId("f1").size)

        db.openHelper.writableDatabase.execSQL("DELETE FROM formulas WHERE formulaId = 'f1'")
        assertEquals(0, dao.getByFormulaId("f1").size)
    }

    @Test
    fun `applyErrorReportPenalty 砍半 6 张子卡（强公式）`() = runTest {
        seedFormula("f1")
        // 强公式：S = 10
        dao.insertAll(CardType.entries.map { fakeSubCard("f1", it, stability = 10.0, lapses = 2) })

        val penaltyTime = 2_000_000L
        dao.applyErrorReportPenalty("f1",
            stabilityMultiplier = 0.5,
            minStability = 0.5,
            nextReviewTime = penaltyTime)

        val after = dao.getByFormulaId("f1")
        assertEquals(6, after.size)
        after.forEach {
            // 10 × 0.5 = 5（> minStability=0.5，按倍率走）
            assertEquals(5.0, it.stability, 1e-9)
            assertEquals(penaltyTime, it.nextReviewTime)
            assertEquals(3, it.lapses)
        }
    }

    @Test
    fun `applyErrorReportPenalty 下限保护（弱公式）`() = runTest {
        seedFormula("f1")
        // 弱公式：S = 0.5（砍半会到 0.25 < minStability，应保留在 minStability=0.5）
        dao.insertAll(CardType.entries.map { fakeSubCard("f1", it, stability = 0.5, lapses = 0) })

        dao.applyErrorReportPenalty("f1",
            stabilityMultiplier = 0.5,
            minStability = 0.5,
            nextReviewTime = 1_000_000L)

        val after = dao.getByFormulaId("f1")
        after.forEach {
            assertEquals(0.5, it.stability, 1e-9) // 触发下限
            assertEquals(1, it.lapses)
        }
    }

    @Test
    fun `getTodayReviewQueue 只返回 nextReviewTime 已到的子卡`() = runTest {
        seedFormula("f1")
        seedFormula("f2")
        val now = 1_000_000L
        dao.insert(fakeSubCard("f1", CardType.C1_Recognition, nextReviewTime = now - 1000)) // due
        dao.insert(fakeSubCard("f1", CardType.C2_Cloze,        nextReviewTime = now + 1000)) // future
        dao.insert(fakeSubCard("f2", CardType.C3_Precondition, nextReviewTime = now))        // due (==)

        val queue = dao.getTodayReviewQueue(now).first()
        assertEquals(2, queue.size)
        assertTrue(queue.all { it.nextReviewTime <= now })
        // 按 nextReviewTime ASC
        assertTrue(queue.zipWithNext().all { (a, b) -> a.nextReviewTime <= b.nextReviewTime })
    }

    @Test
    fun `get 不存在返回 null`() = runTest {
        seedFormula("f1")
        assertNull(dao.get("f1", "c1"))
    }

    // ── Task 2.6：冲刺/通知用聚合查询 ─────────────────────────────────────────

    @Test
    fun `halveStabilityAbove 仅砍高于阈值的子卡`() = runTest {
        seedFormula("f1")
        dao.insert(fakeSubCard("f1", CardType.C1_Recognition, stability = 20.0)) // > 15 → 砍
        dao.insert(fakeSubCard("f1", CardType.C2_Cloze,        stability = 15.0)) // == 15 → 不砍（严格 >）
        dao.insert(fakeSubCard("f1", CardType.C3_Precondition, stability = 8.0))  // < 15 → 不砍

        dao.halveStabilityAbove(15.0)

        assertEquals(10.0, dao.get("f1", "c1")!!.stability, 1e-9)
        assertEquals(15.0, dao.get("f1", "c2")!!.stability, 1e-9)
        assertEquals(8.0, dao.get("f1", "c3")!!.stability, 1e-9)
    }

    @Test
    fun `resetReviewTimeForFormulas 只重置集合内公式的所有子卡`() = runTest {
        seedFormula("f1")
        seedFormula("f2")
        seedFormula("f3")
        listOf("f1", "f2", "f3").forEach { fid ->
            dao.insertAll(CardType.entries.map { fakeSubCard(fid, it, nextReviewTime = 1_000L) })
        }

        val resetTo = 9_999L
        dao.resetReviewTimeForFormulas(listOf("f1", "f3"), resetTo)

        dao.getByFormulaId("f1").forEach { assertEquals(resetTo, it.nextReviewTime) }
        dao.getByFormulaId("f3").forEach { assertEquals(resetTo, it.nextReviewTime) }
        dao.getByFormulaId("f2").forEach { assertEquals(1_000L, it.nextReviewTime) } // 不在集合 → 不变
    }

    @Test
    fun `resetReviewTimeForFormulas 空集合不动任何记录`() = runTest {
        seedFormula("f1")
        dao.insertAll(CardType.entries.map { fakeSubCard("f1", it, nextReviewTime = 1_000L) })

        dao.resetReviewTimeForFormulas(emptyList(), 9_999L)

        dao.getByFormulaId("f1").forEach { assertEquals(1_000L, it.nextReviewTime) }
    }

    @Test
    fun `getEarliestNextReviewTime 取最小值；空表返回 null`() = runTest {
        assertNull(dao.getEarliestNextReviewTime())

        seedFormula("f1")
        dao.insert(fakeSubCard("f1", CardType.C1_Recognition, nextReviewTime = 5_000L))
        dao.insert(fakeSubCard("f1", CardType.C2_Cloze,        nextReviewTime = 2_000L))
        dao.insert(fakeSubCard("f1", CardType.C3_Precondition, nextReviewTime = 8_000L))

        assertEquals(2_000L, dao.getEarliestNextReviewTime())
    }

    @Test
    fun `countDueFormulas 按公式去重计数`() = runTest {
        seedFormula("f1")
        seedFormula("f2")
        val now = 1_000_000L
        // f1 两张子卡都到期 → 计 1
        dao.insert(fakeSubCard("f1", CardType.C1_Recognition, nextReviewTime = now - 1))
        dao.insert(fakeSubCard("f1", CardType.C2_Cloze,        nextReviewTime = now))
        // f2 一张到期一张未来 → 计 1
        dao.insert(fakeSubCard("f2", CardType.C1_Recognition, nextReviewTime = now - 1))
        dao.insert(fakeSubCard("f2", CardType.C2_Cloze,        nextReviewTime = now + 10_000))

        assertEquals(2, dao.countDueFormulas(now))
        assertEquals(0, dao.countDueFormulas(now - 1_000_000)) // 都没到期
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

    private fun fakeSubCard(
        formulaId: String,
        cardType: CardType,
        stability: Double = 1.0,
        lapses: Int = 0,
        nextReviewTime: Long = 0L
    ) = SubCardStateEntity(
        formulaId = formulaId,
        cardType = cardType.code,
        stability = stability,
        difficulty = 2.5,
        lastReviewTime = 0L,
        nextReviewTime = nextReviewTime,
        totalReviews = 0,
        lapses = lapses
    )
}
