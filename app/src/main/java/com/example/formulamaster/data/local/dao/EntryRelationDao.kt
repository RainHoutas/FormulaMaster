package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.EntryRelationEntity

/**
 * 词条间关系边表读写（学习流程重构 Sprint 4 Task 4.1）。
 *
 * 公式族图谱读 [getAll] 建全图；详情页 / 邻居高亮读 [getTouching]（含反查方向）。
 */
@Dao
interface EntryRelationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(relations: List<EntryRelationEntity>)

    @Query("SELECT COUNT(*) FROM entry_relations")
    suspend fun count(): Int

    @Query("SELECT * FROM entry_relations")
    suspend fun getAll(): List<EntryRelationEntity>

    /** 与某词条相连的全部边（fromId 或 toId 命中），供邻居 / 反查。 */
    @Query("SELECT * FROM entry_relations WHERE fromId = :entryId OR toId = :entryId")
    suspend fun getTouching(entryId: String): List<EntryRelationEntity>

    @Query("SELECT * FROM entry_relations WHERE type = :type")
    suspend fun getByType(type: String): List<EntryRelationEntity>
}
