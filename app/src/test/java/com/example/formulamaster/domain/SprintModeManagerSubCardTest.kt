package com.example.formulamaster.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 2 Task 2.6：[SprintModeManager.applyIfNeededSubCards] 子卡版冲刺模式验证。
 *
 * 覆盖：
 * - 非冲刺窗口（剩余 > 30 天）→ 完全 no-op
 * - 冲刺窗口内：stability > 15 砍半 + mastered 公式 nextReviewTime 重置
 * - **顺序语义**：mastered 用砍半前快照判定（砍半后 AVG 跌破 30 仍被重置）
 * - learning 公式（含 MIN<1 弱卡）不被重置，但其高 stability 卡仍砍半
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SprintModeManagerSubCardTest {

    private lateinit var db: AppDatabase
    private lateinit var formulaDao: FormulaDao
    private lateinit var dao: SubCardStateDao

    private val DAY_MS = 86_400_000L
    private val now = 1_000_000_000L

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
    fun tearDown() = db.close()

    @Test
    fun `非冲刺窗口完全 no-op`() = runTest {
        seedFormula("f1")
        dao.insertAll(allSix("f1", stability = 40.0, nextReviewTime = 1_000L))

        // 距考 60 天 > 30 → 不触发
        SprintModeManager.applyIfNeededSubCards(dao, targetExamDate = now + 60 * DAY_MS, currentTimeMs = now)

        dao.getByFormulaId("f1").forEach {
            assertEquals(40.0, it.stability, 1e-9)     // 未砍
            assertEquals(1_000L, it.nextReviewTime)    // 未重置
        }
    }

    @Test
    fun `冲刺窗口内 mastered 公式砍半并重置 nextReviewTime`() = runTest {
        seedFormula("f1")
        // 全 40：MIN=40>=1 且 AVG=40>30 → mastered
        dao.insertAll(allSix("f1", stability = 40.0, nextReviewTime = 1_000L))

        SprintModeManager.applyIfNeededSubCards(dao, targetExamDate = now + 10 * DAY_MS, currentTimeMs = now)

        dao.getByFormulaId("f1").forEach {
            assertEquals(20.0, it.stability, 1e-9)  // 40 > 15 → 砍半
            assertEquals(now, it.nextReviewTime)    // mastered → 重置到 now
        }
    }

    @Test
    fun `mastered 用砍半前快照判定（砍半后 AVG 跌破 30 仍被重置）`() = runTest {
        seedFormula("f1")
        // 全 31：AVG=31>30 → 砍半前 mastered。砍半后 15.5（AVG<30），但应已被纳入重置集合
        dao.insertAll(allSix("f1", stability = 31.0, nextReviewTime = 1_000L))

        SprintModeManager.applyIfNeededSubCards(dao, targetExamDate = now + 5 * DAY_MS, currentTimeMs = now)

        dao.getByFormulaId("f1").forEach {
            assertEquals(15.5, it.stability, 1e-9)
            assertEquals(now, it.nextReviewTime)    // 证明 mastered 在砍半前就算好了
        }
    }

    @Test
    fun `learning 公式不重置但高 stability 卡仍砍半`() = runTest {
        seedFormula("f1")
        // c1 弱卡 0.5（MIN<1 → learning，非 mastered）；其余 40
        dao.insert(fakeSubCard("f1", "c1", stability = 0.5, nextReviewTime = 1_000L))
        listOf("c2", "c3", "c4", "c5", "c6").forEach {
            dao.insert(fakeSubCard("f1", it, stability = 40.0, nextReviewTime = 1_000L))
        }

        SprintModeManager.applyIfNeededSubCards(dao, targetExamDate = now + 10 * DAY_MS, currentTimeMs = now)

        val after = dao.getByFormulaId("f1").associateBy { it.cardType }
        // 全部不重置（learning 不在 mastered 集合）
        after.values.forEach { assertEquals(1_000L, it.nextReviewTime) }
        // 弱卡 0.5 < 15 不砍；高卡 40 > 15 砍半
        assertEquals(0.5, after["c1"]!!.stability, 1e-9)
        assertEquals(20.0, after["c2"]!!.stability, 1e-9)
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private suspend fun seedFormula(id: String) =
        formulaDao.insertAll(listOf(
            FormulaEntity(
                formulaId = id, subject = "高数", chapter = "t", title = "t-$id",
                latexCode = "x", clozeData = "[]", derivationSteps = "[]", tags = "",
                difficultyLevel = 1
            )
        ))

    private fun allSix(formulaId: String, stability: Double, nextReviewTime: Long) =
        listOf("c1", "c2", "c3", "c4", "c5", "c6").map {
            fakeSubCard(formulaId, it, stability, nextReviewTime)
        }

    private fun fakeSubCard(
        formulaId: String,
        cardTypeCode: String,
        stability: Double = 1.0,
        nextReviewTime: Long = 0L
    ) = SubCardStateEntity(
        formulaId = formulaId,
        cardType = cardTypeCode,
        stability = stability,
        difficulty = 2.5,
        lastReviewTime = 0L,
        nextReviewTime = nextReviewTime,
        totalReviews = 0,
        lapses = 0
    )
}
