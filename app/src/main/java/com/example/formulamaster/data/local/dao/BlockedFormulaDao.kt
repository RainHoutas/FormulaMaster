package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sprint 2 Task 2.1b：公式级跨会话 blocked 状态 DAO。
 *
 * 三种典型用法：
 * 1. 新会话启动前调 [getAllIds] 拿 Set 传给
 *    [com.example.formulamaster.domain.ReviewRouter.start.previouslyBlockedFormulas]
 * 2. 收到 FormulaBlocked 事件时 [upsert]（最新 blockedAt 覆盖旧）
 * 3. 收到 FormulaGraduated 事件时 [deleteById]（默写通过 → 清除标志）
 */
@Dao
interface BlockedFormulaDao {

    /** REPLACE 策略：同一公式重复被 blocked 时刷新 blockedAt。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BlockedFormulaEntity)

    @Query("DELETE FROM blocked_formulas WHERE formulaId = :formulaId")
    suspend fun deleteById(formulaId: String): Int

    @Query("SELECT formulaId FROM blocked_formulas")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM blocked_formulas ORDER BY blockedAt DESC")
    suspend fun getAll(): List<BlockedFormulaEntity>

    /** Flow 版：用于 FormulaDetail 红色 banner 实时响应。 */
    @Query("SELECT * FROM blocked_formulas WHERE formulaId = :formulaId LIMIT 1")
    fun observeByFormulaId(formulaId: String): Flow<BlockedFormulaEntity?>

    @Query("SELECT COUNT(*) FROM blocked_formulas")
    suspend fun count(): Int
}
