package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sprint 2 Task 2.1b：复习会话进度单行表 DAO。
 *
 * 所有操作隐式针对单行（`id = SINGLETON_ID`），调用方不需要也不应该传 id。
 */
@Dao
interface ReviewSessionProgressDao {

    /** REPLACE：单行表场景下等价于 upsert。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewSessionProgressEntity)

    @Query("SELECT * FROM review_session_progress WHERE id = ${ReviewSessionProgressEntity.SINGLETON_ID} LIMIT 1")
    suspend fun getCurrent(): ReviewSessionProgressEntity?

    /** Flow 版（ViewModel observe；UI 不直接订阅）。 */
    @Query("SELECT * FROM review_session_progress WHERE id = ${ReviewSessionProgressEntity.SINGLETON_ID} LIMIT 1")
    fun observeCurrent(): Flow<ReviewSessionProgressEntity?>

    /**
     * 清空会话字段但保留行（避免 upsert 时反复 INSERT/DELETE）。
     * 会话结束（所有公式 Graduated / Blocked）时调用。
     */
    @Query("""
        UPDATE review_session_progress
        SET sessionDateMs = NULL,
            formulaContextsJson = NULL,
            currentFormulaIndex = 0
        WHERE id = ${ReviewSessionProgressEntity.SINGLETON_ID}
    """)
    suspend fun clearSession()

    /** 兜底删除整行（测试场景 / 极端重置）。 */
    @Query("DELETE FROM review_session_progress")
    suspend fun deleteAll()
}
