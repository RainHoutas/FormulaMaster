package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.TagEntity

/**
 * 标签表读写（学习流程重构 Sprint 4 Task 4.1）。主要服务种子写入与图谱 / 过滤读取。
 */
@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE namespace = :namespace ORDER BY value")
    suspend fun getByNamespace(namespace: String): List<TagEntity>
}
