package com.example.formulamaster.data.repository

import com.example.formulamaster.data.local.dao.BlockedFormulaDao
import com.example.formulamaster.data.local.dao.ReviewSessionProgressDao
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.ReviewRouter.NextAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Sprint 2 Task 2.1b：[ReviewSessionRepository] 单测。
 *
 * 用 fake DAO（in-memory）跳过 Robolectric，专注测试仓储层的编排逻辑：
 * - 会话日切（computeSessionDateMs）边界
 * - startOrResume 三态决策（Fresh / Resumed / FallbackToFresh）
 * - blocked_formulas 透传给 ReviewRouter.start
 * - endSession 只清 in-progress 字段，不动 blocked_formulas
 */
class ReviewSessionRepositoryTest {

    private lateinit var blockedDao: FakeBlockedFormulaDao
    private lateinit var progressDao: FakeReviewSessionProgressDao
    private lateinit var repo: ReviewSessionRepository

    private val utc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        blockedDao = FakeBlockedFormulaDao()
        progressDao = FakeReviewSessionProgressDao()
        repo = ReviewSessionRepository(blockedDao, progressDao)
    }

    // ── computeSessionDateMs ────────────────────────────────────────────────

    @Test
    fun `computeSessionDateMs 当前晚于刷新点 锚 = 今日刷新点`() {
        // 2026-01-15 08:00 UTC，刷新点 04:00 → 锚 = 2026-01-15 04:00 UTC
        val now = epochMs("2026-01-15T08:00:00Z")
        val expected = epochMs("2026-01-15T04:00:00Z")
        assertEquals(expected, repo.computeSessionDateMs(now, refreshHourOfDay = 4, zoneId = utc))
    }

    @Test
    fun `computeSessionDateMs 当前早于刷新点 锚 = 昨日刷新点`() {
        // 2026-01-15 02:00 UTC，刷新点 04:00 → 锚 = 2026-01-14 04:00 UTC
        val now = epochMs("2026-01-15T02:00:00Z")
        val expected = epochMs("2026-01-14T04:00:00Z")
        assertEquals(expected, repo.computeSessionDateMs(now, refreshHourOfDay = 4, zoneId = utc))
    }

    @Test
    fun `computeSessionDateMs 恰好等于刷新点 锚 = 今日刷新点`() {
        val now = epochMs("2026-01-15T04:00:00Z")
        assertEquals(now, repo.computeSessionDateMs(now, refreshHourOfDay = 4, zoneId = utc))
    }

    @Test
    fun `computeSessionDateMs 两个同日时刻 锚相同`() {
        val morning = epochMs("2026-01-15T08:00:00Z")
        val evening = epochMs("2026-01-15T22:00:00Z")
        assertEquals(
            repo.computeSessionDateMs(morning, refreshHourOfDay = 4, zoneId = utc),
            repo.computeSessionDateMs(evening, refreshHourOfDay = 4, zoneId = utc)
        )
    }

    @Test
    fun `computeSessionDateMs 跨刷新点的两个时刻 锚不同`() {
        val beforeRefresh = epochMs("2026-01-15T02:00:00Z")
        val afterRefresh  = epochMs("2026-01-15T06:00:00Z")
        val a = repo.computeSessionDateMs(beforeRefresh, refreshHourOfDay = 4, zoneId = utc)
        val b = repo.computeSessionDateMs(afterRefresh, refreshHourOfDay = 4, zoneId = utc)
        assertFalse("跨刷新点应是不同会话", a == b)
    }

    // ── startOrResume：Fresh 路径 ───────────────────────────────────────────

    @Test
    fun `startOrResume 无持久化 → Fresh + 持久化已写入`() = runTest {
        val init = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        assertTrue(init is SessionInit.Fresh)
        assertEquals(1_000L, init.sessionDateMs)
        // 持久化字段已写
        val persisted = progressDao.getCurrent()
        assertNotNull(persisted)
        assertEquals(1_000L, persisted!!.sessionDateMs)
        assertNotNull(persisted.formulaContextsJson)
    }

    @Test
    fun `startOrResume 跨日（sessionDateMs 不同）→ Fresh 覆盖旧进度`() = runTest {
        // 先建立"昨日"进度
        progressDao.upsert(
            ReviewSessionProgressEntity(
                sessionDateMs = 100L,
                formulaContextsJson = "[]",
                currentFormulaIndex = 0
            )
        )
        // 今日启动，sessionDateMs 不同
        val init = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 200L
        )
        assertTrue(init is SessionInit.Fresh)
        assertEquals(200L, progressDao.getCurrent()!!.sessionDateMs)
    }

    // ── startOrResume：Resumed 路径 ─────────────────────────────────────────

    @Test
    fun `startOrResume 同日持久化有效 → Resumed`() = runTest {
        // 先启动一个新会话
        val fresh = repo.startOrResume(
            formulasInOrder = listOf("f1", "f2"),
            dueCardsByFormula = mapOf(
                "f1" to listOf(CardType.C1_Recognition),
                "f2" to listOf(CardType.C2_Cloze)
            ),
            sessionDateMs = 1_000L
        )
        // 模拟用户评了一张卡，保存新 state
        val afterRate = ReviewRouter.onInput(fresh.step.newState, ReviewRouter.Input.Rate(3))
        repo.saveCurrentSession(1_000L, afterRate.newState)

        // 同日再次启动
        val resumed = repo.startOrResume(
            formulasInOrder = listOf("f1", "f2"),
            dueCardsByFormula = mapOf(
                "f1" to listOf(CardType.C1_Recognition),
                "f2" to listOf(CardType.C2_Cloze)
            ),
            sessionDateMs = 1_000L
        )
        assertTrue("应续接而非新建", resumed is SessionInit.Resumed)
        // f1 已评 3 推进到 cursor=1（dueCards.size=1 → 全过 → 进默写）
        val resumedAction = resumed.step.nextAction
        // resumed 时下一步应该不是再考 F1 的 C1（因为 F1 已 graduated 进默写或后续）
        // 简化断言：状态被保留
        val firstCtx = resumed.step.newState.formulas[0]
        assertTrue(
            "F1 应已离开 Reviewing 阶段（cursor 推进或已进默写）",
            firstCtx.cursor == 1 || firstCtx.phaseStatus != ReviewRouter.PhaseStatus.Reviewing
        )
    }

    // ── startOrResume：FallbackToFresh 路径 ─────────────────────────────────

    @Test
    fun `startOrResume 持久化 JSON 损坏 → FallbackToFresh`() = runTest {
        progressDao.upsert(
            ReviewSessionProgressEntity(
                sessionDateMs = 1_000L,
                formulaContextsJson = "{not json",  // 损坏
                currentFormulaIndex = 0
            )
        )
        val init = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        assertTrue(init is SessionInit.FallbackToFresh)
        val fallback = init as SessionInit.FallbackToFresh
        assertTrue("reason 应包含 decode 失败语义", fallback.reason.contains("decode", ignoreCase = true))
    }

    @Test
    fun `startOrResume 持久化 cursor 越界 → FallbackToFresh`() = runTest {
        // 手工塞一个 cursor=99（dueCards.size=1，越界）
        val maliciousJson = """[{
            "formulaId":"f1",
            "dueCardCodes":["c1"],
            "cursor":99,
            "roundLapsesMap":{},
            "reinforcementCardCodes":[],
            "reinforcementRetestDone":false,
            "phaseStatusName":"Reviewing",
            "dictationErrorCount":null,
            "wasPreviouslyBlocked":false
        }]"""
        progressDao.upsert(
            ReviewSessionProgressEntity(
                sessionDateMs = 1_000L,
                formulaContextsJson = maliciousJson,
                currentFormulaIndex = 0
            )
        )
        val init = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        assertTrue(init is SessionInit.FallbackToFresh)
        val fallback = init as SessionInit.FallbackToFresh
        assertTrue("reason 应包含 validation 语义", fallback.reason.contains("validation", ignoreCase = true))
    }

    // ── blocked_formulas 透传 ────────────────────────────────────────────────

    @Test
    fun `startOrResume 将 blocked 集合透传给 ReviewRouter`() = runTest {
        // 标记 f1 为 blocked
        repo.markFormulaBlocked("f1", blockedAt = 500L)
        val init = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        // F1 仍走正常 Reviewing 流程（不跳队）
        val ctx = init.step.newState.formulas[0]
        assertTrue("F1 应有 wasPreviouslyBlocked=true 标志", ctx.wasPreviouslyBlocked)
        assertEquals(ReviewRouter.PhaseStatus.Reviewing, ctx.phaseStatus)
        // 第一张推送应是 ShowCard 而非 StartDictation（因为有 due 卡）
        val action = init.step.nextAction
        assertTrue("blocked 不影响正常轮转", action is NextAction.ShowCard)
    }

    @Test
    fun `loadBlockedFormulaIds + markFormulaBlocked + clearFormulaBlocked CRUD`() = runTest {
        assertTrue(repo.loadBlockedFormulaIds().isEmpty())

        repo.markFormulaBlocked("f1", 100L)
        repo.markFormulaBlocked("f2", 200L)
        assertEquals(setOf("f1", "f2"), repo.loadBlockedFormulaIds())

        repo.clearFormulaBlocked("f1")
        assertEquals(setOf("f2"), repo.loadBlockedFormulaIds())
    }

    @Test
    fun `markFormulaBlocked 同公式刷新 blockedAt`() = runTest {
        repo.markFormulaBlocked("f1", 100L)
        repo.markFormulaBlocked("f1", 999L)
        val all = blockedDao.getAll()
        assertEquals(1, all.size)
        assertEquals(999L, all[0].blockedAt)
    }

    // ── endSession ─────────────────────────────────────────────────────────

    @Test
    fun `endSession 清 in-progress 字段 不动 blocked_formulas`() = runTest {
        // 启动会话 + 标记一个 blocked
        repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        repo.markFormulaBlocked("f1", 500L)
        assertNotNull(progressDao.getCurrent()!!.sessionDateMs)

        repo.endSession()

        val after = progressDao.getCurrent()
        assertNotNull("clearSession 保留行", after)
        assertNull(after!!.sessionDateMs)
        assertNull(after.formulaContextsJson)
        // blocked_formulas 没被动
        assertEquals(setOf("f1"), repo.loadBlockedFormulaIds())
    }

    // ── 端到端 ──────────────────────────────────────────────────────────────

    @Test
    fun `E2E 标记 blocked 后同日续接 wasPreviouslyBlocked 标志保持`() = runTest {
        // 第一次会话：F1 默写 blocked
        var init: SessionInit = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to emptyList()),  // 直接进默写
            sessionDateMs = 1_000L
        )
        // 默写连错 3 次 → blocked
        var step = init.step
        repeat(3) { step = ReviewRouter.onInput(step.newState, ReviewRouter.Input.DictationResult(false)) }
        // ViewModel 接 FormulaBlocked 事件后会调 markFormulaBlocked
        repo.markFormulaBlocked("f1", 1_500L)

        // 同日再启动（模拟用户中途退出又回来）—— 因为 saveCurrentSession 没在此场景调用，
        // 状态只在 blocked_formulas 表里
        val resumed = repo.startOrResume(
            formulasInOrder = listOf("f1"),
            dueCardsByFormula = mapOf("f1" to listOf(CardType.C1_Recognition)),
            sessionDateMs = 1_000L
        )
        // 不同 dueCards 配置（新的 due）→ 即使同日也会被 FallbackToFresh 或 Resumed-with-stale 处理
        // 这里关键是 wasPreviouslyBlocked 标志要传下来
        val ctx = resumed.step.newState.formulas[0]
        assertTrue("F1 的 wasPreviouslyBlocked 标志应来自 blocked_formulas 表", ctx.wasPreviouslyBlocked)
    }

    // ── Fake DAO ───────────────────────────────────────────────────────────

    private fun epochMs(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()

    private class FakeBlockedFormulaDao : BlockedFormulaDao {
        private val store = mutableMapOf<String, BlockedFormulaEntity>()
        override suspend fun upsert(entity: BlockedFormulaEntity) { store[entity.formulaId] = entity }
        override suspend fun deleteById(formulaId: String): Int =
            if (store.remove(formulaId) != null) 1 else 0
        override suspend fun getAllIds(): List<String> = store.keys.toList()
        override suspend fun getAll(): List<BlockedFormulaEntity> =
            store.values.sortedByDescending { it.blockedAt }
        override fun observeByFormulaId(formulaId: String): Flow<BlockedFormulaEntity?> =
            MutableStateFlow(store[formulaId])
        override suspend fun count(): Int = store.size
    }

    private class FakeReviewSessionProgressDao : ReviewSessionProgressDao {
        private var current: ReviewSessionProgressEntity? = null
        override suspend fun upsert(entity: ReviewSessionProgressEntity) {
            current = entity.copy(id = ReviewSessionProgressEntity.SINGLETON_ID)
        }
        override suspend fun getCurrent(): ReviewSessionProgressEntity? = current
        override fun observeCurrent(): Flow<ReviewSessionProgressEntity?> = MutableStateFlow(current)
        override suspend fun clearSession() {
            current = current?.copy(
                sessionDateMs = null,
                formulaContextsJson = null,
                currentFormulaIndex = 0
            )
        }
        override suspend fun deleteAll() { current = null }
    }
}
