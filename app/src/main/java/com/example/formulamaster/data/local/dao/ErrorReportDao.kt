package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sprint 1 Task 1.6：错题反向记录 DAO。
 *
 * 写入由 [com.example.formulamaster.domain.ErrorReportProcessor] 统一触发，
 * UI 不应直接调用 [insert]（否则 SubCardStateEntity 不会同步压低 stability）。
 */
@Dao
interface ErrorReportDao {

    /** 插入并返回自增 id */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: ErrorReportEntity): Long

    /** 错题本列表（UI 用，按创建时间倒序） */
    @Query("SELECT * FROM error_reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ErrorReportEntity>>

    @Query("SELECT * FROM error_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ErrorReportEntity?

    @Query("SELECT COUNT(*) FROM error_reports")
    suspend fun count(): Int

    @Delete
    suspend fun delete(report: ErrorReportEntity)
}
