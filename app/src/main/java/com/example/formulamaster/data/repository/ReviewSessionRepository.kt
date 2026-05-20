package com.example.formulamaster.data.repository

import com.example.formulamaster.data.local.dao.BlockedFormulaDao
import com.example.formulamaster.data.local.dao.ReviewSessionProgressDao
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import java.time.Instant
import java.time.ZoneId

/**
 * Sprint 2 Task 2.1b：复习会话仓储层（学习流程重构 RFC §9.3 D-S2-2 补充第 4 条）。
 *
 * **职责**：把 [BlockedFormulaDao] / [ReviewSessionProgressDao] / [ReviewSessionProgressCodec]
 * 三者包成一个干净的"会话生命周期 API"。ViewModel 接手时只调本类，
 * 不直接接触 DAO 和序列化细节。
 *
 * **核心 API**：
 * - [computeSessionDateMs]：纯计算"当前 timestamp 属于哪个会话锚"（按 refreshHour 切分自然日）
 * - [startOrResume]：根据已持久化的 [ReviewSessionProgressEntity] 决定**同日续接** / **跨日重开** / **降级新会话**（持久化损坏时）
 * - [saveCurrentSession]：每次 [ReviewRouter.onInput] 后由 ViewModel 调用持久化最新 state
 * - [markFormulaBlocked] / [clearFormulaBlocked]：处理 `FormulaBlocked` / `FormulaGraduated` 事件
 * - [loadBlockedFormulaIds]：[startOrResume] 内部用，但也开放给 UI（如 Memory Tab 想统计）
 * - [endSession]：会话结束（所有公式 Graduated / Blocked）时清空 in-progress 字段
 *
 * **不负责的事**：
 * - FSRS 更新 → ViewModel 接 `CardRated` 事件后自行调 [com.example.formulamaster.domain.ReviewScheduler]
 * - 强标记升降 → ViewModel 接 `ReinforcementUpgraded` 事件后自行写 `sub_card_states.isReinforced`
 * - dueCards 排序（强标记优先）→ ViewModel / Memory 层构造 dueCards 时自行处理
 */
class ReviewSessionRepository(
    private val blockedDao: BlockedFormulaDao,
    private val progressDao: ReviewSessionProgressDao
) {

    // ── 会话日切计算 ─────────────────────────────────────────────────────────

    /**
     * 计算 [currentTimeMs] 所属的"会话锚"时间戳。
     *
     * 规则：取**最近一个 <= currentTimeMs 的 refreshHour 整点**为锚。
     * - 当前时间 8 AM，刷新点 4 AM → 锚 = 今日 4 AM
     * - 当前时间 2 AM，刷新点 4 AM → 锚 = 昨日 4 AM（今日 4 AM 还没到）
     *
     * 两个 timestamp 属于"同一会话"当且仅当它们的锚相同。
     *
     * 与 [com.example.formulamaster.domain.ReviewScheduler.truncateToRefreshHour] 的对齐：
     * 后者把"未来时间"截到指定日的 refreshHour；本方法把"当前时间"截到最近过去的 refreshHour。
     */
    fun computeSessionDateMs(
        currentTimeMs: Long,
        refreshHourOfDay: Int = 4,
        refreshMinute: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = Instant.ofEpochMilli(currentTimeMs).atZone(zoneId)
        val todayAnchor = zdt.toLocalDate()
            .atTime(refreshHourOfDay, refreshMinute, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        return if (todayAnchor <= currentTimeMs) {
            todayAnchor
        } else {
            // 今日刷新点未到，锚向前推一天
            Instant.ofEpochMilli(todayAnchor)
                .atZone(zoneId).minusDays(1)
                .toInstant().toEpochMilli()
        }
    }

    // ── blocked_formulas 操作 ───────────────────────────────────────────────

    suspend fun loadBlockedFormulaIds(): Set<String> = blockedDao.getAllIds().toSet()

    suspend fun markFormulaBlocked(formulaId: String, blockedAt: Long) {
        blockedDao.upsert(BlockedFormulaEntity(formulaId = formulaId, blockedAt = blockedAt))
    }

    suspend fun clearFormulaBlocked(formulaId: String) {
        blockedDao.deleteById(formulaId)
    }

    // ── 会话进度操作 ─────────────────────────────────────────────────────────

    /**
     * 启动或续接复习会话。
     *
     * 决策树：
     * 1. 无持久化进度 / 持久化的 sessionDateMs 与传入不符 → [SessionInit.Fresh]（FSRS 拉新 due，重新 start）
     * 2. 同日且持久化 JSON 有效 → [SessionInit.Resumed]（恢复 RouterState + advance 重新计算下一步）
     * 3. 同日但 JSON 损坏 / cursor 越界 → [SessionInit.FallbackToFresh]（按新会话兜底，传入 reason 便于 ViewModel 上报埋点）
     *
     * @param formulasInOrder        公式 ID 列表（轮转顺序）
     * @param dueCardsByFormula      每条公式的本会话 dueCards（调用方负责按 isReinforced 优先排序）
     * @param sessionDateMs          本次启动的会话锚（[computeSessionDateMs] 算出来传入）
     */
    suspend fun startOrResume(
        formulasInOrder: List<String>,
        dueCardsByFormula: Map<String, List<CardType>>,
        sessionDateMs: Long
    ): SessionInit {
        val blocked = loadBlockedFormulaIds()
        val persisted = progressDao.getCurrent()

        val canResume = persisted != null
            && persisted.sessionDateMs == sessionDateMs
            && persisted.formulaContextsJson != null

        if (!canResume) {
            return startFresh(formulasInOrder, dueCardsByFormula, blocked, sessionDateMs)
        }

        val contexts = ReviewSessionProgressCodec.decode(persisted!!.formulaContextsJson)
        if (contexts == null) {
            return fallback(formulasInOrder, dueCardsByFormula, blocked, sessionDateMs,
                reason = "JSON decode failed")
        }
        if (!ReviewSessionProgressCodec.validate(contexts)) {
            return fallback(formulasInOrder, dueCardsByFormula, blocked, sessionDateMs,
                reason = "Persisted state failed validation (cursor out of range)")
        }

        // 用当前 blocked_formulas 重新覆盖 wasPreviouslyBlocked 标志：
        // 持久化 JSON 是会话开始时的快照，但 blocked_formulas 表会在会话过程中
        // 被 markFormulaBlocked / clearFormulaBlocked 实时更新。续接时应以表为准，
        // 否则 UI 红条会与实际 blocked 状态错位。
        val refreshed = contexts.map { ctx ->
            ctx.copy(wasPreviouslyBlocked = ctx.formulaId in blocked)
        }
        val resumedState = ReviewRouter.RouterState(
            formulas = refreshed,
            currentFormulaIndex = persisted.currentFormulaIndex.coerceIn(0, (refreshed.size - 1).coerceAtLeast(0))
        )
        // 续接时不重写持久化（让 ViewModel 在下一次 onInput 后通过 saveCurrentSession 自然写回）
        val step = ReviewRouter.advance(resumedState)
        return SessionInit.Resumed(step, sessionDateMs)
    }

    private suspend fun startFresh(
        formulasInOrder: List<String>,
        dueCardsByFormula: Map<String, List<CardType>>,
        blocked: Set<String>,
        sessionDateMs: Long
    ): SessionInit.Fresh {
        val step = ReviewRouter.start(formulasInOrder, dueCardsByFormula, previouslyBlockedFormulas = blocked)
        saveCurrentSession(sessionDateMs, step.newState)
        return SessionInit.Fresh(step, sessionDateMs)
    }

    private suspend fun fallback(
        formulasInOrder: List<String>,
        dueCardsByFormula: Map<String, List<CardType>>,
        blocked: Set<String>,
        sessionDateMs: Long,
        reason: String
    ): SessionInit.FallbackToFresh {
        val step = ReviewRouter.start(formulasInOrder, dueCardsByFormula, previouslyBlockedFormulas = blocked)
        saveCurrentSession(sessionDateMs, step.newState)
        return SessionInit.FallbackToFresh(step, sessionDateMs, reason)
    }

    /**
     * 持久化最新 RouterState。每次 [ReviewRouter.onInput] 后 ViewModel 调一次。
     * 单写不读，幂等。
     */
    suspend fun saveCurrentSession(sessionDateMs: Long, state: ReviewRouter.RouterState) {
        progressDao.upsert(
            ReviewSessionProgressEntity(
                sessionDateMs = sessionDateMs,
                formulaContextsJson = ReviewSessionProgressCodec.encode(state),
                currentFormulaIndex = state.currentFormulaIndex
            )
        )
    }

    /** 会话结束（所有公式 Graduated / Blocked）→ 清 in-progress 字段（blocked_formulas 不受影响）。 */
    suspend fun endSession() {
        progressDao.clearSession()
    }
}

/**
 * [ReviewSessionRepository.startOrResume] 的三态返回。
 *
 * UI / ViewModel 通常只关心 [step] 和 [sessionDateMs]；分类型 [Resumed] / [FallbackToFresh]
 * 可用于上报埋点（"会话续接成功率"）或调试。
 */
sealed class SessionInit {
    abstract val step: ReviewRouter.Step
    abstract val sessionDateMs: Long

    data class Fresh(
        override val step: ReviewRouter.Step,
        override val sessionDateMs: Long
    ) : SessionInit()

    data class Resumed(
        override val step: ReviewRouter.Step,
        override val sessionDateMs: Long
    ) : SessionInit()

    data class FallbackToFresh(
        override val step: ReviewRouter.Step,
        override val sessionDateMs: Long,
        val reason: String
    ) : SessionInit()
}
