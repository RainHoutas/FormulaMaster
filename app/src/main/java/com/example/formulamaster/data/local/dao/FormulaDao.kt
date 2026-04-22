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
}
