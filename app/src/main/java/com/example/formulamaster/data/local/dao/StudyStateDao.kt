package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.formulamaster.data.local.entity.StudyStateEntity
import kotlinx.coroutines.flow.Flow

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
}
