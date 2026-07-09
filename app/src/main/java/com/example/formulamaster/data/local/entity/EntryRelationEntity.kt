package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 词条间关系边表（学习流程重构 Sprint 4 Task 4.1）。
 *
 * 取代原 `FormulaEntity` 的 `parents` / `siblings` / `confusableWith` 三个内嵌 JSON 字段。
 * 公式族图谱直接读本表建边；反查、外键防悬空、原子化皆由此解决。
 *
 * [type] 存 [com.example.formulamaster.domain.EntryRelationType.code]（derivation / confusable / sibling）。
 * 有向类型（推导）按 from→to 存；无向类型（易混 / 同族）种子期按字典序规范化 (min,max) 去重。
 * 两侧外键均指向 `formulas`，删除词条级联清边——种子重灌时旧边自动清理。
 */
@Entity(
    tableName = "entry_relations",
    primaryKeys = ["fromId", "toId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["fromId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["toId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fromId"), Index("toId")]
)
data class EntryRelationEntity(
    val fromId: String,
    val toId: String,
    val type: String
)
