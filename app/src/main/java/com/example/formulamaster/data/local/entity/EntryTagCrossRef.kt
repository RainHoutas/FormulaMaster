package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 词条 ↔ 标签 多对多关系（学习流程重构 Sprint 4 Task 4.1）。
 *
 * 统一取代原来分散的分类机制：
 *  - `FormulaEntity.subject` / `chapter` 硬列（现降级为「显示缓存」，真相在此表的 primary 标签）
 *  - `formula_subject_map` 表（数一二三，已退休，并入 namespace=exam）
 *
 * [isPrimary]：一条词条在某 namespace 下可挂多个标签（如泰勒同属"微分中值定理"与"无穷级数"两章），
 * 其中标为 primary 的那个供 UI 显示「主学科 / 主章节」。当前每词条 subject/chapter 各一主标签。
 */
@Entity(
    tableName = "entry_tag_map",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["tagId"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class EntryTagCrossRef(
    val entryId: String,
    val tagId: String,
    val isPrimary: Boolean = false
)
