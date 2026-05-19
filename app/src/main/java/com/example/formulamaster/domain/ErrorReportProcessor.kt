package com.example.formulamaster.domain

import com.example.formulamaster.data.local.dao.ErrorReportDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import com.google.gson.Gson
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
        val entity = ErrorReportEntity(
            createdAt           = currentTimeMs,
            subject             = input.subject,
            chapter             = input.chapter,
            sourceType          = input.sourceType,
            sourceTag           = input.sourceTag,
            wrongFormulaIdsJson = gson.toJson(input.wrongFormulaIds),
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
}
