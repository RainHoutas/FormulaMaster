package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.FormulaSubjectMapEntity

/**
 * 学习流程重构 Sprint 1 Task 1.3。
 *
 * 多对多关系表的读写。读路径主要通过 [FormulaDao.observeByKaoyanSubject] 的 JOIN 完成；
 * 本 DAO 主要服务种子写入。
 */
@Dao
interface FormulaSubjectMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(maps: List<FormulaSubjectMapEntity>)

    @Query("SELECT COUNT(*) FROM formula_subject_map")
    suspend fun count(): Int

    @Query("SELECT * FROM formula_subject_map WHERE formulaId = :formulaId")
    suspend fun getByFormulaId(formulaId: String): List<FormulaSubjectMapEntity>

    @Query("SELECT * FROM formula_subject_map WHERE subjectType = :subjectType")
    suspend fun getBySubjectType(subjectType: String): List<FormulaSubjectMapEntity>
}
