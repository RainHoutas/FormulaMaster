package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.FormulaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(formulas: List<FormulaEntity>)

    @Query("SELECT * FROM formulas ORDER BY subject, chapter")
    fun getAll(): Flow<List<FormulaEntity>>

    @Query("SELECT * FROM formulas WHERE formulaId = :id")
    suspend fun getById(id: String): FormulaEntity?

    /** 用于首次启动判断是否需要预加载数据 */
    @Query("SELECT COUNT(*) FROM formulas")
    suspend fun count(): Int

    /**
     * 学习流程重构 Sprint 1 Task 1.3 — 按考研数学子科目过滤公式列表。
     *
     * JOIN `formula_subject_map`，只返回 `subjectType` 匹配传入 [subjectCode] 的公式。
     * 传入参数为 [com.example.formulamaster.domain.KaoyanSubject.code]（"1"/"2"/"3"）。
     *
     * 同一公式可能在 map 表里有多行（数一+数三都考），用 DISTINCT 去重避免 JOIN 笛卡尔放大。
     */
    @Query(
        """
        SELECT DISTINCT f.* FROM formulas f
        INNER JOIN formula_subject_map m ON f.formulaId = m.formulaId
        WHERE m.subjectType = :subjectCode
        ORDER BY f.subject, f.chapter
        """
    )
    fun observeByKaoyanSubject(subjectCode: String): Flow<List<FormulaEntity>>
}
