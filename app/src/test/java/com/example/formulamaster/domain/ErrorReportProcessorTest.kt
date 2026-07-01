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

    // ── Sprint 1 Task 1.8：边界补强 ────────────────────────────────────────────

    @Test
    fun `空 wrongFormulaIds 仍写入 error_reports 但不动子卡`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 8.0, initialLapses = 0)

        val id = processor.process(
            ErrorReportInput(
                subject = "高数", chapter = "极限", sourceType = "其他",
                sourceTag = "empty-case",
                wrongFormulaIds = emptyList(),
                note = "用户标记但没选公式"
            ),
            currentTimeMs = fixedNow, zoneId = utc
        )

        // 错题本仍写入（含 note）
        assertEquals("用户标记但没选公式", db.errorReportDao().getById(id)?.note)
        // 没指定公式 → f1 的子卡保持不变
        db.subCardStateDao().getByFormulaId("f1").forEach {
            assertEquals(8.0, it.stability, 1e-9)
            assertEquals(0, it.lapses)
        }
    }

    @Test
    fun `混合 ids 已学习公式被砍 未学习公式不报错`() = runTest {
        seedFormulaAndSubCards("studied", initialStability = 6.0, initialLapses = 1)
        // not_studied 公式存在但无 SubCardState
        db.formulaDao().insertAll(listOf(fakeFormula("not_studied")))

        processor.process(
            ErrorReportInput(
                subject = "高数", chapter = "微积分", sourceType = "习题集",
                sourceTag = "mix",
                wrongFormulaIds = listOf("studied", "not_studied", "completely_missing")
            ),
            currentTimeMs = fixedNow, zoneId = utc
        )

        // studied：6 张子卡均被砍半（6 × 0.5 = 3.0），lapses +1
        db.subCardStateDao().getByFormulaId("studied").forEach {
            assertEquals(3.0, it.stability, 1e-9)
            assertEquals(2, it.lapses)
        }
        // not_studied / completely_missing：依旧没有 SubCardState 行（不创建占位）
        assertTrue(db.subCardStateDao().getByFormulaId("not_studied").isEmpty())
        assertTrue(db.subCardStateDao().getByFormulaId("completely_missing").isEmpty())
    }

    @Test
    fun `已是 minStability 的公式连续两次错题仍守住 0_5 下限`() = runTest {
        seedFormulaAndSubCards("rock_bottom", initialStability = 0.5, initialLapses = 0)

        repeat(3) {
            processor.process(
                ErrorReportInput(
                    subject = "高数", chapter = "极限", sourceType = "其他",
                    sourceTag = "round-$it",
                    wrongFormulaIds = listOf("rock_bottom")
                ),
                currentTimeMs = fixedNow, zoneId = utc
            )
        }

        // 三次错题后 stability 仍为 0.5（下限保护持续生效），lapses 累计到 3
        db.subCardStateDao().getByFormulaId("rock_bottom").forEach {
            assertEquals(0.5, it.stability, 1e-9)
            assertEquals(3, it.lapses)
        }
    }

    @Test
    fun `nextReviewTime 精确落到次日 hourOfDay-minute（自定义时间字段）`() = runTest {
        seedFormulaAndSubCards("ft", initialStability = 4.0, initialLapses = 0)

        processor.process(
            ErrorReportInput(
                subject = "高数", chapter = "极限", sourceType = "其他",
                sourceTag = "time-precise",
                wrongFormulaIds = listOf("ft")
            ),
            hourOfDay = 21, minute = 30,
            currentTimeMs = fixedNow, zoneId = utc
        )

        val expectedNextReview = ZonedDateTime
            .of(2026, 5, 20, 21, 30, 0, 0, utc)
            .toInstant().toEpochMilli()
        db.subCardStateDao().getByFormulaId("ft").forEach {
            assertEquals(expectedNextReview, it.nextReviewTime)
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

    // ── Sprint 3 Task 3.3：快照捕获 + 删除还原语义 ──────────────────────────────

    @Test
    fun `process 快照记录施加惩罚前的子卡原值`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 10.0, initialLapses = 2)

        val id = processor.process(
            ErrorReportInput("高数", "极限", "历年真题", "2024-1", listOf("f1")),
            currentTimeMs = fixedNow, zoneId = utc
        )

        val snaps: List<SubCardPenaltySnapshot> = Gson().fromJson(
            db.errorReportDao().getById(id)!!.penaltySnapshotJson,
            object : TypeToken<List<SubCardPenaltySnapshot>>() {}.type
        )
        assertEquals(6, snaps.size)
        // 快照是**惩罚前**原值（S=10 / lapses=2），不是砍半后的 5 / 3
        snaps.forEach {
            assertEquals(10.0, it.stability, 1e-9)
            assertEquals(2, it.lapses)
        }
        // 当前子卡确实已被砍半
        db.subCardStateDao().getByFormulaId("f1").forEach {
            assertEquals(5.0, it.stability, 1e-9)
            assertEquals(3, it.lapses)
        }
    }

    @Test
    fun `未学公式录入错题时快照为 null`() = runTest {
        db.formulaDao().insertAll(listOf(fakeFormula("never")))
        val id = processor.process(
            ErrorReportInput("高数", "极限", "其他", "0", listOf("never")),
            currentTimeMs = fixedNow, zoneId = utc
        )
        assertNull(db.errorReportDao().getById(id)!!.penaltySnapshotJson)
    }

    @Test
    fun `deleteReport 仅删记录不还原子卡`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 10.0, initialLapses = 0)
        val id = processor.process(
            ErrorReportInput("高数", "极限", "真题", "1", listOf("f1")),
            currentTimeMs = fixedNow, zoneId = utc
        )
        val record = db.errorReportDao().getById(id)!!

        processor.deleteReport(record, restore = false)

        assertNull(db.errorReportDao().getById(id))
        // 子卡保持惩罚后状态（未还原）
        db.subCardStateDao().getByFormulaId("f1").forEach {
            assertEquals(5.0, it.stability, 1e-9)
            assertEquals(1, it.lapses)
        }
    }

    @Test
    fun `deleteReport 恢复计划还原未复习过的子卡`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 10.0, initialLapses = 2)
        val id = processor.process(
            ErrorReportInput("高数", "极限", "真题", "1", listOf("f1")),
            currentTimeMs = fixedNow, zoneId = utc
        )
        val record = db.errorReportDao().getById(id)!!

        processor.deleteReport(record, restore = true)

        assertNull(db.errorReportDao().getById(id))
        // 6 张子卡全部还原到录入前（S=10 / lapses=2 / nextReview=0，seed 原值）
        db.subCardStateDao().getByFormulaId("f1").forEach {
            assertEquals(10.0, it.stability, 1e-9)
            assertEquals(2, it.lapses)
            assertEquals(0L, it.nextReviewTime)
        }
    }

    @Test
    fun `deleteReport 恢复时逐子卡保留录入后复习过的进度`() = runTest {
        seedFormulaAndSubCards("f1", initialStability = 10.0, initialLapses = 0)
        val id = processor.process(
            ErrorReportInput("高数", "极限", "真题", "1", listOf("f1")),
            currentTimeMs = fixedNow, zoneId = utc
        )
        val record = db.errorReportDao().getById(id)!!

        // 模拟录入后 c1 被真实复习：lastReviewTime > createdAt，进度前进
        val c1 = db.subCardStateDao().get("f1", "c1")!!
        db.subCardStateDao().update(
            c1.copy(
                stability = 20.0, lapses = 5,
                lastReviewTime = fixedNow + 3_600_000L,      // 录入后 1 小时复习
                nextReviewTime = fixedNow + 100_000_000L
            )
        )

        processor.deleteReport(record, restore = true)

        // c1：录入后复习过 → 保留新进度，不还原
        val c1After = db.subCardStateDao().get("f1", "c1")!!
        assertEquals(20.0, c1After.stability, 1e-9)
        assertEquals(5, c1After.lapses)
        assertEquals(fixedNow + 100_000_000L, c1After.nextReviewTime)
        // c2~c6：未复习 → 还原到录入前（S=10 / lapses=0 / nextReview=0）
        listOf("c2", "c3", "c4", "c5", "c6").forEach { code ->
            val sc = db.subCardStateDao().get("f1", code)!!
            assertEquals(10.0, sc.stability, 1e-9)
            assertEquals(0, sc.lapses)
            assertEquals(0L, sc.nextReviewTime)
        }
    }

    @Test
    fun `deleteReport 恢复计划遇 null 快照静默跳过仍删记录`() = runTest {
        db.formulaDao().insertAll(listOf(fakeFormula("never")))
        val id = processor.process(
            ErrorReportInput("高数", "极限", "其他", "0", listOf("never")),
            currentTimeMs = fixedNow, zoneId = utc
        )
        val record = db.errorReportDao().getById(id)!!
        assertNull(record.penaltySnapshotJson)

        // 不应抛异常
        processor.deleteReport(record, restore = true)
        assertNull(db.errorReportDao().getById(id))
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
