package com.example.formulamaster.domain

import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 考前冲刺模式管理器（Sprint Mode）。
 *
 * 触发条件：距 `targetExamDate` 剩余天数 ≤ 30。
 *
 * 触发效果：
 *   1. 所有 stability > [STABILITY_THRESHOLD] 的记录 stability 减半
 *      → 缩短高稳定性公式的复习间隔，避免"遗忘盲区"
 *   2. 所有 Mastered(3) 公式 nextReviewTime 重置为当前时间
 *      → 强制拉回复习队列，高强度温习已掌握公式
 *
 * 安全性：
 *   - halveStabilityAbove 自带幂等保护：stability 一旦降至 ≤ 15，
 *     后续调用不再影响该记录，只有新复习使 stability 重新超过 15 才会再次触发。
 *   - resetMasteredReviewTime 是冲刺期间有意为之的强制行为（每次启动均生效）。
 *
 * Sprint 2 Task 2.4：[targetExamDate] 改为参数注入（不再读全局 AppConfig），
 * 调用方从 `AppPreference.settings.value.effectiveTargetExamDate` 取值。
 */
object SprintModeManager {

    private const val DAY_MS: Long = 86_400_000L
    private const val SPRINT_THRESHOLD_DAYS = 30
    private const val STABILITY_THRESHOLD = 15.0

    /**
     * 在 App 启动时调用。满足冲刺条件则执行批量更新，否则立即返回。
     *
     * @param studyStateDao Room DAO，由调用方注入（避免直接持有 Context）
     * @param targetExamDate 目标考试日期（Unix ms）
     * @param currentTimeMs 当前时间戳（便于单元测试注入）
     */
    suspend fun applyIfNeeded(
        studyStateDao: StudyStateDao,
        targetExamDate: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val remainingDays = (targetExamDate - currentTimeMs) / DAY_MS
        if (remainingDays > SPRINT_THRESHOLD_DAYS) return@withContext

        // 批量写库（Room 单次 UPDATE，性能 O(N)）
        studyStateDao.halveStabilityAbove(STABILITY_THRESHOLD)
        studyStateDao.resetMasteredReviewTime(currentTimeMs)
    }

    /**
     * Sprint 2 Task 2.6：子卡版冲刺模式（母卡退役后接管 [applyIfNeeded]）。
     *
     * 与母卡版的差异只在"已掌握"判定：母卡有 learningState 列可直接 `WHERE = 3`，
     * 子卡无此列，"已掌握"是派生属性。故先**快照**全量子卡用
     * [SubCardAggregator] 算出 mastered 公式集合，**再**砍稳定性——保持母卡时代
     * "mastered 判定独立于本次砍半"的语义（砍半若把某公式 AVG 压到 30 以下，
     * 也不影响它本轮是否被重置）。
     *
     * 顺序固定为「先算 mastered → 再 halve → 再 reset」，不可调换。
     *
     * ⚠ mastered 判定依赖 [SubCardAggregator] 的阈值；其中 `MIN(stability) < 1.0`
     * 边界尚待用户拍板（见 SubCardAggregator KDoc）。
     */
    suspend fun applyIfNeededSubCards(
        subCardDao: SubCardStateDao,
        targetExamDate: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val remainingDays = (targetExamDate - currentTimeMs) / DAY_MS
        if (remainingDays > SPRINT_THRESHOLD_DAYS) return@withContext

        // 1. 先快照算出 mastered 公式（砍半前）
        val snapshot = subCardDao.getAllStatesOnce()
        val masteredFormulaIds = SubCardAggregator.deriveAll(snapshot)
            .filterValues { it.learningState == SubCardAggregator.STATE_MASTERED }
            .keys.toList()

        // 2. 砍稳定性
        subCardDao.halveStabilityAbove(STABILITY_THRESHOLD)

        // 3. 把 mastered 公式拉回复习池
        if (masteredFormulaIds.isNotEmpty()) {
            subCardDao.resetReviewTimeForFormulas(masteredFormulaIds, currentTimeMs)
        }
    }

    /** 当前是否处于冲刺期（供 UI 展示警告横幅使用） */
    fun isActive(
        targetExamDate: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        val remainingDays = (targetExamDate - currentTimeMs) / DAY_MS
        return remainingDays in 0..SPRINT_THRESHOLD_DAYS
    }

    /** 距考试天数（向下取整，已过期返回负数）。 */
    fun remainingDays(
        targetExamDate: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long = (targetExamDate - currentTimeMs) / DAY_MS
}
