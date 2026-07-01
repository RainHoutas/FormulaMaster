package com.example.formulamaster.domain

import com.example.formulamaster.data.local.dao.ErrorReportDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.ZoneId

/**
 * 错题录入参数（来自 UI 多 chip 选择 + 数字键盘）。
 */
data class ErrorReportInput(
    val subject: String,
    val chapter: String,
    val sourceType: String,
    val sourceTag: String,
    val wrongFormulaIds: List<String>,
    val note: String? = null
)

/**
 * 录入错题、施加惩罚**之前**某张子卡的原值快照（Sprint 3 Task 3.3）。
 *
 * 删除错题选「恢复计划」时据此逐子卡 best-effort 还原。整批以 JSON 存入
 * [ErrorReportEntity.penaltySnapshotJson]。
 */
data class SubCardPenaltySnapshot(
    val formulaId: String,
    val cardType: String,
    val stability: Double,
    val nextReviewTime: Long,
    val lapses: Int
)

/**
 * Sprint 1 Task 1.6：错题反向链路处理器（RFC D6=C 落地）。
 *
 * 用户在错题本里勾选若干"我这题里用错的公式"后，本处理器：
 *   1. 把 [ErrorReportInput] 序列化为 [ErrorReportEntity] 写入 error_reports 表
 *   2. 对每个错位公式的 **6 张 SubCardStateEntity** 同时：
 *      - stability ←─ `MAX(stability × 0.5, 0.5)`（选 A 决策 2026-05-19：砍半 + 下限保护）
 *      - nextReviewTime ←─ 次日刷新整点（[ReviewScheduler.adjustToRefreshHour] 计算）
 *      - lapses ←─ lapses + 1
 *
 * 设计取舍：本处理器不强制 SubCardState **必须先存在**。若用户标错位的公式没学过
 * （SubCardState 空），UPDATE 语句不 match 任何行，等用户首次学习这条公式时按正常初值
 * 进入 FSRS。这避免了"在用户还没编码过的公式上提前插占位 SubCardState"的脏写。
 */
class ErrorReportProcessor(
    private val errorReportDao: ErrorReportDao,
    private val subCardStateDao: SubCardStateDao,
    private val gson: Gson = Gson()
) {
    companion object {
        /** stability 砍半倍率（选 A，2026-05-19） */
        const val STABILITY_MULTIPLIER: Double = 0.5
        /** stability 下限：避免砍到 0 让 FSRS 无意义 */
        const val MIN_STABILITY: Double = 0.5

        private const val DAY_MS = 86_400_000L

        private val SNAPSHOT_LIST_TYPE =
            object : TypeToken<List<SubCardPenaltySnapshot>>() {}.type
    }

    /**
     * 处理一条错题录入：持久化 + 同步压低相关公式的所有子卡。
     *
     * @return 新建的 [ErrorReportEntity.id]
     */
    suspend fun process(
        input: ErrorReportInput,
        hourOfDay: Int = 8,
        minute: Int = 0,
        currentTimeMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        // 施加惩罚前，先快照每条错位公式**现有**子卡的 (S/nextReview/lapses) 原值，
        // 供日后删除时「恢复计划」逐子卡 best-effort 还原。未学过的公式无子卡 → 无快照条目。
        val snapshot: List<SubCardPenaltySnapshot> = input.wrongFormulaIds.flatMap { formulaId ->
            subCardStateDao.getByFormulaId(formulaId).map { sc ->
                SubCardPenaltySnapshot(
                    formulaId      = sc.formulaId,
                    cardType       = sc.cardType,
                    stability      = sc.stability,
                    nextReviewTime = sc.nextReviewTime,
                    lapses         = sc.lapses
                )
            }
        }

        val entity = ErrorReportEntity(
            createdAt           = currentTimeMs,
            subject             = input.subject,
            chapter             = input.chapter,
            sourceType          = input.sourceType,
            sourceTag           = input.sourceTag,
            wrongFormulaIdsJson = gson.toJson(input.wrongFormulaIds),
            penaltySnapshotJson = if (snapshot.isEmpty()) null else gson.toJson(snapshot),
            note                = input.note
        )
        val reportId = errorReportDao.insert(entity)

        val nextReviewTime = ReviewScheduler.adjustToRefreshHour(
            rawTimeMs     = currentTimeMs + DAY_MS,
            currentTimeMs = currentTimeMs,
            hourOfDay     = hourOfDay,
            minute        = minute,
            zoneId        = zoneId
        )

        input.wrongFormulaIds.forEach { formulaId ->
            subCardStateDao.applyErrorReportPenalty(
                formulaId           = formulaId,
                stabilityMultiplier = STABILITY_MULTIPLIER,
                minStability        = MIN_STABILITY,
                nextReviewTime      = nextReviewTime
            )
        }

        return reportId
    }

    /**
     * 删除一条错题记录，可选按快照 best-effort 还原所选公式的复习计划。
     *
     * - `restore = false`：仅删记录，不动任何子卡。
     * - `restore = true`：逐子卡还原——**仅当该子卡录入后未被真实复习触碰**
     *   （`lastReviewTime <= report.createdAt`）才回滚 stability/nextReviewTime/lapses 到快照值。
     *   录入后复习过的子卡保留当前进度（惩罚已被真实复习消费，不抹，2026-07-01 用户拍板）。
     *
     * 边界：快照缺失（旧记录 / 录入时无子卡）或 JSON 损坏 → 静默跳过还原，仍删记录。
     * 快照里的子卡若已被删除（公式清库）→ `get` 返回 null，跳过该条。
     */
    suspend fun deleteReport(report: ErrorReportEntity, restore: Boolean) {
        if (restore) {
            val snapshots: List<SubCardPenaltySnapshot> = report.penaltySnapshotJson
                ?.let { runCatching { gson.fromJson<List<SubCardPenaltySnapshot>>(it, SNAPSHOT_LIST_TYPE) }.getOrNull() }
                ?: emptyList()
            snapshots.forEach { snap ->
                val current = subCardStateDao.get(snap.formulaId, snap.cardType) ?: return@forEach
                // 录入后被真实复习过 → 保留当前进度，不还原。
                if (current.lastReviewTime > report.createdAt) return@forEach
                subCardStateDao.update(
                    current.copy(
                        stability      = snap.stability,
                        nextReviewTime = snap.nextReviewTime,
                        lapses         = snap.lapses
                    )
                )
            }
        }
        errorReportDao.delete(report)
    }
}
