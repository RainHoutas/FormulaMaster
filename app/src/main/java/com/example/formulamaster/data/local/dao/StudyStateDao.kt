package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.formulamaster.data.local.entity.StudyStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 母卡（公式整体）FSRS 状态 DAO。
 *
 * ⚠ **Task 2.6（2026-05-29）已退役**：子卡 sub_card_states 成为唯一真相源，
 * 全部读写已迁出（见 [SubCardStateDao] + [com.example.formulamaster.domain.SubCardAggregator]）。
 * 保留 entity 与表仅为兼容老数据库（彻底删表需 Room 迁移）；**禁止再新增对本 DAO 的调用**。
 */
@Deprecated("母卡已退役，改用 SubCardStateDao（Task 2.6）。保留仅为兼容老库，勿新增调用。")
@Dao
interface StudyStateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: StudyStateEntity)

    @Update
    suspend fun update(state: StudyStateEntity)

    @Query("SELECT * FROM study_states WHERE formulaId = :id")
    suspend fun getByFormulaId(id: String): StudyStateEntity?

    /**
     * 今日待复习队列：nextReviewTime <= 当前时间 且状态为 Learning(1) 或 Reviewing(2)
     */
    @Query("""
        SELECT * FROM study_states
        WHERE nextReviewTime <= :currentTime
        AND learningState IN (1, 2)
        ORDER BY nextReviewTime ASC
    """)
    fun getTodayReviewQueue(currentTime: Long): Flow<List<StudyStateEntity>>

    /**
     * 所有学习状态（用于 MemoryViewModel 合并公式列表）
     */
    @Query("SELECT * FROM study_states")
    fun getAllStates(): Flow<List<StudyStateEntity>>

    /**
     * 已掌握的公式列表（learningState = 3），供 Test 模块使用
     */
    @Query("SELECT * FROM study_states WHERE learningState = 3")
    fun getMasteredFormulas(): Flow<List<StudyStateEntity>>

    /**
     * 冲刺模式：批量将 stability > threshold 的记录 stability 减半
     */
    @Query("UPDATE study_states SET stability = stability / 2 WHERE stability > :threshold")
    suspend fun halveStabilityAbove(threshold: Double)

    /**
     * 冲刺模式：将 Mastered 公式的 nextReviewTime 重置到当前时间（拉入复习池）
     */
    @Query("UPDATE study_states SET nextReviewTime = :currentTime WHERE learningState = 3")
    suspend fun resetMasteredReviewTime(currentTime: Long)

    /**
     * Task 5.4：顽固难点（Leech）一键延后 N 天
     * 冲刺期对 lapses ≥ 4 的公式再次遗忘时，允许用户"跳过本周"
     */
    @Query("UPDATE study_states SET nextReviewTime = :nextReviewTime WHERE formulaId = :id")
    suspend fun setNextReviewTime(id: String, nextReviewTime: Long)

    /**
     * Sprint 2 Task 2.3：一次性快照所有 study_states，供"切换刷新整点时批量重写"使用。
     * 与 [getAllStates] 不同，本方法是 suspend 一次性读取，不返回 Flow。
     */
    @Query("SELECT * FROM study_states")
    suspend fun getAllStatesOnce(): List<StudyStateEntity>
}
