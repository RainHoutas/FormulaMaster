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
     * Sprint 4 Task 4.1：数据源从 `formula_subject_map` 迁到统一标签体系（namespace=exam）。
     *
     * JOIN `entry_tag_map` + `tags`，只返回挂了 `exam:<subjectCode>` 标签的公式。
     * [subjectCode] 为 [com.example.formulamaster.domain.KaoyanSubject.code]（"1"/"2"/"3"），
     * 即 `tags.value`（namespace=exam）。
     *
     * 同一公式可能挂多个 exam 标签（数一+数三都考），用 DISTINCT 去重避免 JOIN 放大。
     */
    @Query(
        """
        SELECT DISTINCT f.* FROM formulas f
        INNER JOIN entry_tag_map m ON f.formulaId = m.entryId
        INNER JOIN tags t ON m.tagId = t.tagId
        WHERE t.namespace = 'exam' AND t.value = :subjectCode
        ORDER BY f.subject, f.chapter
        """
    )
    fun observeByKaoyanSubject(subjectCode: String): Flow<List<FormulaEntity>>
}
