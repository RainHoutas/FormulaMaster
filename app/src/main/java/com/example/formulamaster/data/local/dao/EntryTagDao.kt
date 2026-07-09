package com.example.formulamaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.formulamaster.data.local.entity.EntryTagCrossRef

/**
 * 词条 ↔ 标签 多对多关系读写（学习流程重构 Sprint 4 Task 4.1）。
 *
 * 数一二三过滤走 [FormulaDao.observeByKaoyanSubject] 的 JOIN（namespace=exam）。
 * 本 DAO 服务种子写入 + 图谱 / 显示派生（取某词条的全部标签、按标签反查词条）。
 */
@Dao
interface EntryTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<EntryTagCrossRef>)

    @Query("SELECT COUNT(*) FROM entry_tag_map")
    suspend fun count(): Int

    /** 某 namespace 下的映射行数（测试用：exam 行数 = Σ appliesTo 长度）。 */
    @Query(
        """
        SELECT COUNT(*) FROM entry_tag_map m
        INNER JOIN tags t ON m.tagId = t.tagId
        WHERE t.namespace = :namespace
        """
    )
    suspend fun countByNamespace(namespace: String): Int

    @Query("SELECT * FROM entry_tag_map")
    suspend fun getAll(): List<EntryTagCrossRef>

    /** 取某词条挂的全部映射（供派生主学科 / 主章节 + 展示所有标签）。 */
    @Query("SELECT * FROM entry_tag_map WHERE entryId = :entryId")
    suspend fun getByEntry(entryId: String): List<EntryTagCrossRef>
}
