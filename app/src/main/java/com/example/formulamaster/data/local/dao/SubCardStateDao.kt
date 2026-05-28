package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sprint 1 Task 1.5：子卡级 FSRS 状态 DAO。
 *
 * 与 [StudyStateDao] 的差异：
 * - 复合主键 `(formulaId, cardType)`，CRUD 全部按这两个字段定位
 * - 没有 learningState 列，但写入 Sprint 2 各 C1~C6 卡型 UI 完成后会接管 FSRS 调度
 */
@Dao
interface SubCardStateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: SubCardStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(states: List<SubCardStateEntity>)

    @Update
    suspend fun update(state: SubCardStateEntity)

    @Query("SELECT * FROM sub_card_states WHERE formulaId = :formulaId AND cardType = :cardType LIMIT 1")
    suspend fun get(formulaId: String, cardType: String): SubCardStateEntity?

    /** 取某条公式所有子卡（6 条，按 cardType 升序便于 UI 渲染） */
    @Query("SELECT * FROM sub_card_states WHERE formulaId = :formulaId ORDER BY cardType ASC")
    suspend fun getByFormulaId(formulaId: String): List<SubCardStateEntity>

    /** 取某条公式所有子卡（Flow 版） */
    @Query("SELECT * FROM sub_card_states WHERE formulaId = :formulaId ORDER BY cardType ASC")
    fun observeByFormulaId(formulaId: String): Flow<List<SubCardStateEntity>>

    /** 取所有子卡 Flow（MemoryViewModel 整体进度计算时合并用） */
    @Query("SELECT * FROM sub_card_states")
    fun getAllStates(): Flow<List<SubCardStateEntity>>

    /** 一次性快照（批量重写场景，例如刷新整点切换、错题反向） */
    @Query("SELECT * FROM sub_card_states")
    suspend fun getAllStatesOnce(): List<SubCardStateEntity>

    /** 今日待复习子卡队列：nextReviewTime <= 当前时间，按到期时间升序 */
    @Query("""
        SELECT * FROM sub_card_states
        WHERE nextReviewTime <= :currentTime
        ORDER BY nextReviewTime ASC
    """)
    fun getTodayReviewQueue(currentTime: Long): Flow<List<SubCardStateEntity>>

    /**
     * 错题反向链路（Task 1.6 调用）：将给定 formulaId 的**所有子卡** stability 按
     * `MAX(stability × multiplier, minStability)` 比例砍低，同时写入新 nextReviewTime 并
     * `lapses + 1`。
     *
     * 选 A 决策（2026-05-19）：multiplier=0.5 + minStability=0.5；保留原强度信号，
     * 强公式仍保留较高底子。
     *
     * 在 SQL 里直接 UPDATE 比 Kotlin 端读取-修改-写回更原子。
     */
    @Query("""
        UPDATE sub_card_states
        SET stability = MAX(stability * :stabilityMultiplier, :minStability),
            nextReviewTime = :nextReviewTime,
            lapses = lapses + 1
        WHERE formulaId = :formulaId
    """)
    suspend fun applyErrorReportPenalty(
        formulaId: String,
        stabilityMultiplier: Double,
        minStability: Double,
        nextReviewTime: Long
    )

    // ── Sprint 2 Task 2.6：母卡退役后，冲刺模式 / 通知改读子卡 ──────────────────

    /**
     * 冲刺模式：批量把 `stability > threshold` 的子卡 stability 减半。
     * 母卡 [StudyStateDao.halveStabilityAbove] 的逐卡等价物——本操作语义与母卡一致
     * （纯逐卡阈值，不依赖 learningState），可机械迁移。
     */
    @Query("UPDATE sub_card_states SET stability = stability / 2 WHERE stability > :threshold")
    suspend fun halveStabilityAbove(threshold: Double)

    /**
     * 把给定公式集合的**所有子卡** nextReviewTime 重置为 [currentTime]（拉回复习池）。
     *
     * 替代母卡 [StudyStateDao.resetMasteredReviewTime]：母卡靠 `WHERE learningState = 3` 直接
     * 过滤，但 sub_card_states 无 learningState 列，"已掌握"是派生属性
     * （[com.example.formulamaster.domain.SubCardAggregator]）。故由调用方先用聚合器算出
     * mastered 公式集合，再调本方法重置——SQL 只负责按 formulaId 批量写。
     */
    @Query("UPDATE sub_card_states SET nextReviewTime = :currentTime WHERE formulaId IN (:formulaIds)")
    suspend fun resetReviewTimeForFormulas(formulaIds: List<String>, currentTime: Long)

    /**
     * 通知：最早到期的子卡时间（`MIN(nextReviewTime)`）。无任何子卡时返回 null。
     * 供 DailyReminderWorker 判断"今日是否有复习项"用。
     */
    @Query("SELECT MIN(nextReviewTime) FROM sub_card_states")
    suspend fun getEarliestNextReviewTime(): Long?

    /**
     * 通知：到期（`nextReviewTime <= :currentTime`）的**去重公式**数量。
     * 一条公式哪怕多张子卡到期也只计 1，用于提醒文案"今日 N 个公式待复习"。
     */
    @Query("SELECT COUNT(DISTINCT formulaId) FROM sub_card_states WHERE nextReviewTime <= :currentTime")
    suspend fun countDueFormulas(currentTime: Long): Int
}
