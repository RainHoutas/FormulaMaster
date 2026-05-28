package com.example.formulamaster.data.repository

import com.example.formulamaster.data.AppSettings
import com.example.formulamaster.data.local.dao.BlockedFormulaDao
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.ReviewSessionProgressDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
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

/**
 * Sprint 2 Task 2.1c：[ReviewEventProcessor] 单测。
 *
 * 这是 ViewModel 接线最深的一层逻辑，用 fake DAO 全覆盖：
 * - CardRated 触发 FSRS 计算 + 写 sub_card_states + 写 review_log
 * - 连续好评计数 + 强标记自动清除（连续 3 次 ≥ 3 清除 isReinforced）
 * - ReinforcementUpgraded 写 isReinforced=true + stability ×0.5 + 计数归零
 * - ReinforcementCleared / EnterDictation 无副作用
 * - FormulaBlocked / FormulaGraduated 操作 blocked_formulas 表
 * - CardRated 时子卡缺失 → 跳过不抛
 */
class ReviewEventProcessorTest {

    private lateinit var subCardDao: FakeSubCardStateDao
    private lateinit var reviewLogDao: FakeReviewLogDao
    private lateinit var blockedDao: FakeBlockedFormulaDao
    private lateinit var progressDao: FakeReviewSessionProgressDao
    private lateinit var sessionRepo: ReviewSessionRepository
    private lateinit var processor: ReviewEventProcessor

    private val fixedClock = 1_700_000_000_000L
    private val settings = AppSettings(dailyRefreshHourOfDay = 4, dailyRefreshMinuteOfHour = 0)

    @Before
    fun setUp() {
        subCardDao = FakeSubCardStateDao()
        reviewLogDao = FakeReviewLogDao()
        blockedDao = FakeBlockedFormulaDao()
        progressDao = FakeReviewSessionProgressDao()
        sessionRepo = ReviewSessionRepository(blockedDao, progressDao)
        processor = ReviewEventProcessor(
            subCardDao   = subCardDao,
            reviewLogDao = reviewLogDao,
            sessionRepo  = sessionRepo,
            clock        = { fixedClock },
            costTimeMsProvider = { 123L }
        )
    }

    // ── CardRated ────────────────────────────────────────────────────────────

    @Test
    fun `CardRated 评 3 写新 stability + 计数 +1 + 写日志`() = runTest {
        subCardDao.put(fakeCard("f1", CardType.C1_Recognition, stability = 2.0, consecutive = 0))

        processor.process(
            ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 3, isReinforcementRetest = false),
            settings
        )

        val after = subCardDao.getCached("f1", "c1")!!
        assertTrue("评 3 应使 stability 增大", after.stability > 2.0)
        assertEquals(1, after.totalReviews)
        assertEquals(0, after.lapses)
        assertEquals(1, after.consecutiveGoodReviews)
        assertEquals(fixedClock, after.lastReviewTime)

        assertEquals(1, reviewLogDao.inserted.size)
        val log = reviewLogDao.inserted.single()
        assertEquals("f1", log.formulaId)
        assertEquals(3, log.userRating)
        assertEquals(2, log.interactionType)
        assertEquals(123L, log.costTimeMs)
    }

    @Test
    fun `CardRated 评 1 重置 consecutiveGoodReviews + lapses+1`() = runTest {
        subCardDao.put(fakeCard("f1", CardType.C2_Cloze, stability = 5.0, consecutive = 2, lapses = 1))

        processor.process(
            ReviewRouter.Event.CardRated("f1", CardType.C2_Cloze, rating = 1, isReinforcementRetest = false),
            settings
        )

        val after = subCardDao.getCached("f1", "c2")!!
        assertEquals(0, after.consecutiveGoodReviews)
        assertEquals(2, after.lapses)
    }

    @Test
    fun `CardRated 子卡不存在 静默跳过 不写日志`() = runTest {
        processor.process(
            ReviewRouter.Event.CardRated("nope", CardType.C1_Recognition, rating = 3, isReinforcementRetest = false),
            settings
        )
        assertTrue(reviewLogDao.inserted.isEmpty())
    }

    // ── 强标记自动清除 ───────────────────────────────────────────────────────

    @Test
    fun `强标记 连续好评 3 次后 自动清除 isReinforced`() = runTest {
        subCardDao.put(fakeCard(
            "f1", CardType.C1_Recognition,
            stability = 1.0, consecutive = 0, isReinforced = true
        ))

        // 连续 3 次评 3
        repeat(3) {
            processor.process(
                ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 3, isReinforcementRetest = false),
                settings
            )
        }
        val after = subCardDao.getCached("f1", "c1")!!
        assertEquals(3, after.consecutiveGoodReviews)
        assertFalse("第 3 次后强标记应被清除", after.isReinforced)
    }

    @Test
    fun `强标记 连续 2 次好评 不清除`() = runTest {
        subCardDao.put(fakeCard(
            "f1", CardType.C1_Recognition,
            stability = 1.0, consecutive = 0, isReinforced = true
        ))
        repeat(2) {
            processor.process(
                ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 3, isReinforcementRetest = false),
                settings
            )
        }
        val after = subCardDao.getCached("f1", "c1")!!
        assertEquals(2, after.consecutiveGoodReviews)
        assertTrue("仅 2 次好评不足以清除强标记", after.isReinforced)
    }

    @Test
    fun `强标记 评 1 打断连续计数 重新累计`() = runTest {
        subCardDao.put(fakeCard(
            "f1", CardType.C1_Recognition,
            stability = 1.0, consecutive = 2, isReinforced = true
        ))
        // 接评 1 → 计数归零，强标记保留
        processor.process(
            ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 1, isReinforcementRetest = false),
            settings
        )
        val after = subCardDao.getCached("f1", "c1")!!
        assertEquals(0, after.consecutiveGoodReviews)
        assertTrue("评 1 不清除强标记", after.isReinforced)
    }

    @Test
    fun `非强标记卡 连续好评 不会无故置 false`() = runTest {
        // 非强标记的卡，consecutive 累计但 isReinforced 保持 false
        subCardDao.put(fakeCard(
            "f1", CardType.C1_Recognition,
            stability = 1.0, consecutive = 0, isReinforced = false
        ))
        repeat(3) {
            processor.process(
                ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 3, isReinforcementRetest = false),
                settings
            )
        }
        val after = subCardDao.getCached("f1", "c1")!!
        assertEquals(3, after.consecutiveGoodReviews)
        assertFalse(after.isReinforced)
    }

    // ── ReinforcementUpgraded ────────────────────────────────────────────────

    @Test
    fun `ReinforcementUpgraded 写 isReinforced + stability ×0_5 + 计数归零`() = runTest {
        subCardDao.put(fakeCard(
            "f1", CardType.C1_Recognition,
            stability = 4.0, consecutive = 5, isReinforced = false
        ))
        processor.process(
            ReviewRouter.Event.ReinforcementUpgraded("f1", CardType.C1_Recognition),
            settings
        )
        val after = subCardDao.getCached("f1", "c1")!!
        assertTrue(after.isReinforced)
        assertEquals(2.0, after.stability, 1e-9)
        assertEquals(0, after.consecutiveGoodReviews)
    }

    @Test
    fun `ReinforcementUpgraded 不写 review_log（避免一次评分两条日志）`() = runTest {
        subCardDao.put(fakeCard("f1", CardType.C1_Recognition))
        processor.process(
            ReviewRouter.Event.ReinforcementUpgraded("f1", CardType.C1_Recognition),
            settings
        )
        assertTrue(reviewLogDao.inserted.isEmpty())
    }

    @Test
    fun `ReinforcementUpgraded 子卡不存在 静默跳过`() = runTest {
        processor.process(
            ReviewRouter.Event.ReinforcementUpgraded("nope", CardType.C1_Recognition),
            settings
        )
        assertTrue(reviewLogDao.inserted.isEmpty())
        assertNull(subCardDao.getCached("nope", "c1"))
    }

    // ── ReinforcementCleared / EnterDictation 无副作用 ───────────────────────

    @Test
    fun `ReinforcementCleared 不动 sub_card_states 不写日志`() = runTest {
        subCardDao.put(fakeCard("f1", CardType.C1_Recognition, stability = 4.0, isReinforced = true))
        processor.process(
            ReviewRouter.Event.ReinforcementCleared("f1", CardType.C1_Recognition),
            settings
        )
        val after = subCardDao.getCached("f1", "c1")!!
        assertEquals(4.0, after.stability, 1e-9)
        assertTrue("ReinforcementCleared 是会话内事件，不动 isReinforced", after.isReinforced)
        assertTrue(reviewLogDao.inserted.isEmpty())
    }

    @Test
    fun `EnterDictation 完全无副作用`() = runTest {
        processor.process(ReviewRouter.Event.EnterDictation("f1"), settings)
        assertTrue(reviewLogDao.inserted.isEmpty())
        assertEquals(0, blockedDao.count())
    }

    // ── FormulaBlocked / FormulaGraduated ────────────────────────────────────

    @Test
    fun `FormulaBlocked 写 blocked_formulas`() = runTest {
        processor.process(ReviewRouter.Event.FormulaBlocked("f1"), settings)
        assertEquals(setOf("f1"), blockedDao.getAllIds().toSet())
        assertEquals(fixedClock, blockedDao.getCached("f1")!!.blockedAt)
    }

    @Test
    fun `FormulaGraduated 清 blocked_formulas`() = runTest {
        blockedDao.upsert(BlockedFormulaEntity("f1", blockedAt = 100L))
        processor.process(ReviewRouter.Event.FormulaGraduated("f1"), settings)
        assertTrue(blockedDao.getAllIds().isEmpty())
    }

    @Test
    fun `FormulaGraduated 公式不在 blocked 列表 不抛`() = runTest {
        processor.process(ReviewRouter.Event.FormulaGraduated("ghost"), settings)
        assertEquals(0, blockedDao.count())
    }

    // ── processAll 多事件批处理 ──────────────────────────────────────────────

    @Test
    fun `processAll 按顺序处理一组事件`() = runTest {
        subCardDao.put(fakeCard("f1", CardType.C1_Recognition, stability = 2.0))
        val events = listOf<ReviewRouter.Event>(
            ReviewRouter.Event.CardRated("f1", CardType.C1_Recognition, rating = 1, isReinforcementRetest = true),
            ReviewRouter.Event.ReinforcementUpgraded("f1", CardType.C1_Recognition),
            ReviewRouter.Event.EnterDictation("f1")
        )
        processor.processAll(events, settings)

        val after = subCardDao.getCached("f1", "c1")!!
        // CardRated 先评 1 → lapses+1，consecutive=0；然后 Upgraded → isReinforced=true + stability×0.5
        // FSRS 评 1 时 stability = min(1.0, S×0.2) = 0.4，然后 ×0.5 = 0.2
        assertTrue("Upgraded 后 stability 应被进一步砍半", after.stability < 0.4)
        assertTrue(after.isReinforced)
        assertEquals(1, after.lapses)
        assertEquals(1, reviewLogDao.inserted.size)
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private fun fakeCard(
        formulaId: String,
        cardType: CardType,
        stability: Double = 1.0,
        difficulty: Double = 2.5,
        consecutive: Int = 0,
        lapses: Int = 0,
        isReinforced: Boolean = false
    ) = SubCardStateEntity(
        formulaId = formulaId,
        cardType = cardType.code,
        stability = stability,
        difficulty = difficulty,
        lastReviewTime = 0L,
        nextReviewTime = 0L,
        totalReviews = 0,
        lapses = lapses,
        consecutiveGoodReviews = consecutive,
        isReinforced = isReinforced
    )

    // ── Fake DAOs ───────────────────────────────────────────────────────────

    private class FakeSubCardStateDao : SubCardStateDao {
        private val store = mutableMapOf<Pair<String, String>, SubCardStateEntity>()
        fun put(entity: SubCardStateEntity) { store[entity.formulaId to entity.cardType] = entity }
        fun getCached(formulaId: String, cardType: String) = store[formulaId to cardType]

        override suspend fun insert(state: SubCardStateEntity) { store.putIfAbsent(state.formulaId to state.cardType, state) }
        override suspend fun insertAll(states: List<SubCardStateEntity>) { states.forEach { insert(it) } }
        override suspend fun update(state: SubCardStateEntity) { store[state.formulaId to state.cardType] = state }
        override suspend fun get(formulaId: String, cardType: String) = store[formulaId to cardType]
        override suspend fun getByFormulaId(formulaId: String) =
            store.values.filter { it.formulaId == formulaId }.sortedBy { it.cardType }
        override fun observeByFormulaId(formulaId: String): Flow<List<SubCardStateEntity>> =
            MutableStateFlow(store.values.filter { it.formulaId == formulaId })
        override fun getAllStates(): Flow<List<SubCardStateEntity>> = MutableStateFlow(store.values.toList())
        override suspend fun getAllStatesOnce(): List<SubCardStateEntity> = store.values.toList()
        override fun getTodayReviewQueue(currentTime: Long): Flow<List<SubCardStateEntity>> =
            MutableStateFlow(store.values.filter { it.nextReviewTime <= currentTime })
        override suspend fun applyErrorReportPenalty(
            formulaId: String,
            stabilityMultiplier: Double,
            minStability: Double,
            nextReviewTime: Long
        ) {
            store.keys.filter { it.first == formulaId }.forEach { k ->
                val v = store[k]!!
                store[k] = v.copy(
                    stability = maxOf(v.stability * stabilityMultiplier, minStability),
                    nextReviewTime = nextReviewTime,
                    lapses = v.lapses + 1
                )
            }
        }
        override suspend fun halveStabilityAbove(threshold: Double) {
            store.keys.forEach { k ->
                val v = store[k]!!
                if (v.stability > threshold) store[k] = v.copy(stability = v.stability / 2)
            }
        }
        override suspend fun resetReviewTimeForFormulas(formulaIds: List<String>, currentTime: Long) {
            store.keys.filter { it.first in formulaIds }.forEach { k ->
                store[k] = store[k]!!.copy(nextReviewTime = currentTime)
            }
        }
        override suspend fun getEarliestNextReviewTime(): Long? =
            store.values.minOfOrNull { it.nextReviewTime }
        override suspend fun countDueFormulas(currentTime: Long): Int =
            store.values.filter { it.nextReviewTime <= currentTime }.map { it.formulaId }.distinct().size
    }

    private class FakeReviewLogDao : ReviewLogDao {
        val inserted = mutableListOf<ReviewLogEntity>()
        override suspend fun insert(log: ReviewLogEntity) { inserted += log }
        override fun getLogsByDateRange(start: Long, end: Long): Flow<List<ReviewLogEntity>> =
            MutableStateFlow(inserted.filter { it.reviewTime in start..end })
    }

    private class FakeBlockedFormulaDao : BlockedFormulaDao {
        private val store = mutableMapOf<String, BlockedFormulaEntity>()
        fun getCached(formulaId: String) = store[formulaId]
        override suspend fun upsert(entity: BlockedFormulaEntity) { store[entity.formulaId] = entity }
        override suspend fun deleteById(formulaId: String) = if (store.remove(formulaId) != null) 1 else 0
        override suspend fun getAllIds() = store.keys.toList()
        override suspend fun getAll() = store.values.sortedByDescending { it.blockedAt }
        override fun observeByFormulaId(formulaId: String): Flow<BlockedFormulaEntity?> =
            MutableStateFlow(store[formulaId])
        override suspend fun count() = store.size
    }

    private class FakeReviewSessionProgressDao : ReviewSessionProgressDao {
        private var current: ReviewSessionProgressEntity? = null
        override suspend fun upsert(entity: ReviewSessionProgressEntity) {
            current = entity.copy(id = ReviewSessionProgressEntity.SINGLETON_ID)
        }
        override suspend fun getCurrent() = current
        override fun observeCurrent(): Flow<ReviewSessionProgressEntity?> = MutableStateFlow(current)
        override suspend fun clearSession() {
            current = current?.copy(sessionDateMs = null, formulaContextsJson = null, currentFormulaIndex = 0)
        }
        override suspend fun deleteAll() { current = null }
    }
}
