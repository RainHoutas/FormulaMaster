package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.OcrFeedbackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sprint 1 Task 1.9 — 识别失败反馈样本 DAO
 */
@Dao
interface OcrFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OcrFeedbackEntity): Long

    /** 按时间倒序返回全部样本。导出 JSON 用。 */
    @Query("SELECT * FROM ocr_feedback ORDER BY createdAt DESC")
    suspend fun getAll(): List<OcrFeedbackEntity>

    /** 当前累计样本数（响应式，供设置页 UI 显示）。 */
    @Query("SELECT COUNT(*) FROM ocr_feedback")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM ocr_feedback")
    suspend fun clearAll()
}
