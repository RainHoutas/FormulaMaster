package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReviewLogEntity)

    /**
     * 按时间范围查询日志，用于热力图统计（Task 5.5）
     * start/end 均为 Unix 时间戳（ms）
     */
    @Query("""
        SELECT * FROM review_logs
        WHERE reviewTime >= :start AND reviewTime <= :end
        ORDER BY reviewTime ASC
    """)
    fun getLogsByDateRange(start: Long, end: Long): Flow<List<ReviewLogEntity>>
}
