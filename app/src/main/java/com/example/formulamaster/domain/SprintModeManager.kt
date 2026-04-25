package com.example.formulamaster.domain

import com.example.formulamaster.data.AppConfig
import com.example.formulamaster.data.local.dao.StudyStateDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 考前冲刺模式管理器（Sprint Mode）。
 *
 * 触发条件：距 [AppConfig.targetExamDate] 剩余天数 ≤ 30。
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
 */
object SprintModeManager {

    private const val DAY_MS: Long = 86_400_000L
    private const val SPRINT_THRESHOLD_DAYS = 30
    private const val STABILITY_THRESHOLD = 15.0

    /**
     * 在 App 启动时调用。满足冲刺条件则执行批量更新，否则立即返回。
     *
     * @param studyStateDao Room DAO，由调用方注入（避免直接持有 Context）
     * @param currentTimeMs 当前时间戳（便于单元测试注入）
     */
    suspend fun applyIfNeeded(
        studyStateDao: StudyStateDao,
        currentTimeMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val remainingDays = (AppConfig.targetExamDate - currentTimeMs) / DAY_MS
        if (remainingDays > SPRINT_THRESHOLD_DAYS) return@withContext

        // 批量写库（Room 单次 UPDATE，性能 O(N)）
        studyStateDao.halveStabilityAbove(STABILITY_THRESHOLD)
        studyStateDao.resetMasteredReviewTime(currentTimeMs)
    }

    /** 当前是否处于冲刺期（供 UI 展示警告横幅使用） */
    fun isActive(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val remainingDays = (AppConfig.targetExamDate - currentTimeMs) / DAY_MS
        return remainingDays in 0..SPRINT_THRESHOLD_DAYS
    }
}
