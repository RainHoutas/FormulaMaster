package com.example.formulamaster.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Sprint 1 Task 1.6：错题反向链路处理器单测。
 *
 * 覆盖：
 *  - 多 formulaId 的 6×N 子卡批量推到次日 + stability 砍半 + lapses+1
 *  - 强公式（S=10）按 multiplier 走（5.0）；弱公式（S=0.5）触发下限保护（0.5）
 *  - 未学习公式（无 SubCardState）不报错，UPDATE 0 行
 *  - error_reports 表写入正确 JSON
 *  - nextReviewTime 截断到次日 hour:minute（注入 zoneId / currentTimeMs 后确定性）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorReportProcessorTest {

    private lateinit var db: AppDatabase
    private lateinit var processor: ErrorReportProcessor

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        processor = ErrorReportProcessor(
            errorReportDao  = db.errorReportDao(),
            subCardStateDao = db.subCardStateDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `process 写入 error_reports 并返回新 id`() = runTest {
        seedFormulaAndSubCards("calc_taylor_01", initialStability = 10.0, initialLapses = 0)
        seedFormulaAndSubCards("calc_lhopital_01", initialStability = 10.0, initialLapses = 0)

        val id = processor.process(
            ErrorReportInput(
                subject       = "高数",
                chapter       = "极限与连续",
                sourceType    = "历年真题",
                sourceTag     = "2024-18",
                wrongFormulaIds = listOf("calc_taylor_01", "calc_lhopital_01")
            ),
            currentTimeMs = fixedNow,
            zoneId        = utc
        )

        assertTrue("id 应自增 > 0", id > 0L)
        val record = db.errorReportDao().getById(id)
        assertNotNull(record)
        assertEquals("高数", record!!.subject)
        assertEquals("2024-18", record.sourceTag)
        assertNull(record.note)
        // wrongFormulaIdsJson 可往返
        val ids: List<String> = Gson().fromJson(
            record.wrongFormulaIdsJson,
            object : TypeToken<List<String>>() {}.type
        )
        assertEquals(listOf("calc_taylor_01", "calc_lhopital_01"), ids)
    }

    @Test
    fun `2 formulaId × 6 子卡 = 12 张全部被砍半且推到次日`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 10.0, initialLapses = 2)
        seedFormulaAndSubCards("f2", initialStability = 10.0, initialLapses = 0)
        // 干扰项：与本错题无关的公式
        seedFormulaAndSubCards("f3", initialStability = 10.0, initialLapses = 0)

        processor.process(
            ErrorReportInput(
                subject       = "高数",
                chapter       = "极限",
                sourceType    = "模拟卷",
                sourceTag     = "880-186-3",
                wrongFormulaIds = listOf("f1", "f2")
            ),
            hourOfDay     = 8,
            minute        = 0,
            currentTimeMs = fixedNow,
            zoneId        = utc
        )

        val expectedNextReview = ZonedDateTime
            .of(2026, 5, 20, 8, 0, 0, 0, utc)
            .toInstant().toEpochMilli()

        val f1Cards = db.subCardStateDao().getByFormulaId("f1")
        val f2Cards = db.subCardStateDao().getByFormulaId("f2")
        val f3Cards = db.subCardStateDao().getByFormulaId("f3")

        // f1：6 张子卡均被改
        assertEquals(6, f1Cards.size)
        f1Cards.forEach {
            assertEquals(5.0, it.stability, 1e-9)           // 10 × 0.5
            assertEquals(expectedNextReview, it.nextReviewTime)
            assertEquals(3, it.lapses)                        // 2 + 1
        }

        // f2 同理（lapses 从 0 涨到 1）
        f2Cards.forEach {
            assertEquals(5.0, it.stability, 1e-9)
            assertEquals(expectedNextReview, it.nextReviewTime)
            assertEquals(1, it.lapses)
        }

        // f3 不在错题里：stability/lapses 完全不变
        f3Cards.forEach {
            assertEquals(10.0, it.stability, 1e-9)
            assertEquals(0, it.lapses)
        }
    }

    @Test
    fun `弱公式触发 minStability 下限保护`() = runTest {
        seedFormulaAndSubCards("f_weak", initialStability = 0.5, initialLapses = 0)

        processor.process(
            ErrorReportInput(
                subject       = "高数",
                chapter       = "极限",
                sourceType    = "习题集",
                sourceTag     = "1",
                wrongFormulaIds = listOf("f_weak")
            ),
            currentTimeMs = fixedNow,
            zoneId        = utc
        )

        db.subCardStateDao().getByFormulaId("f_weak").forEach {
            // 0.5 × 0.5 = 0.25 < 0.5，触发 minStability
            assertEquals(0.5, it.stability, 1e-9)
            assertEquals(1, it.lapses)
        }
    }

    @Test
    fun `未学习公式不报错（UPDATE 0 行）`() = runTest {
        // 公式存在但**没有** SubCardState 记录
        db.formulaDao().insertAll(listOf(fakeFormula("never_studied")))

        val id = processor.process(
            ErrorReportInput(
                subject       = "高数",
                chapter       = "未知",
                sourceType    = "其他",
                sourceTag     = "0",
                wrongFormulaIds = listOf("never_studied")
            ),
            currentTimeMs = fixedNow,
            zoneId        = utc
        )

        // 错题记录正常写入
        assertNotNull(db.errorReportDao().getById(id))
        // 没有任何 SubCardState 被建出来（避免脏占位）
        assertTrue(db.subCardStateDao().getByFormulaId("never_studied").isEmpty())
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    /** 固定 "now" = 2026-05-19 12:34:56 UTC，便于断言次日 08:00 nextReviewTime */
    private val utc = ZoneId.of("UTC")
    private val fixedNow = ZonedDateTime.of(2026, 5, 19, 12, 34, 56, 0, utc)
        .toInstant().toEpochMilli()

    private fun fakeFormula(id: String) = FormulaEntity(
        formulaId = id, subject = "高数", chapter = "test",
        title = "test-$id", latexCode = "x",
        clozeData = "[]", derivationSteps = "[]",
        tags = "", difficultyLevel = 1
    )

    private suspend fun seedFormulaAndSubCards(
        id: String,
        initialStability: Double,
        initialLapses: Int
    ) {
        db.formulaDao().insertAll(listOf(fakeFormula(id)))
        db.subCardStateDao().insertAll(
            CardType.entries.map {
                SubCardStateEntity(
                    formulaId = id, cardType = it.code,
                    stability = initialStability, difficulty = 2.5,
                    lastReviewTime = 0L, nextReviewTime = 0L,
                    totalReviews = 0, lapses = initialLapses
                )
            }
        )
    }
}
