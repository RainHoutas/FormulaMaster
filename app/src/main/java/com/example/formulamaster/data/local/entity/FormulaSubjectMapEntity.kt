package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 公式与考研数学子科目的多对多关系表（学习流程重构 Sprint 1 Task 1.3）。
 *
 * 一条公式可能同时属于"数一/数二/数三"中的多个：
 * - 高数基础（极限/微分中值/泰勒等）通常 ["1","2","3"] 三者皆考
 * - 概率论分布族（正态/卡方/t/F/泊松）通常 ["1","3"]，数二不考
 * - 高级数学物理向（曲线/曲面积分等）通常仅 ["1"]
 *
 * [subjectType] 存储 [com.example.formulamaster.domain.KaoyanSubject.code]
 * 的短码字符串（"1" / "2" / "3"），便于 SQL JOIN 阅读，落库无中文。
 *
 * 删除公式（formulas.json schema 演进 / destructive migration 重置时）会级联清理映射行。
 */
@Entity(
    tableName = "formula_subject_map",
    primaryKeys = ["formulaId", "subjectType"],
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectType")]
)
data class FormulaSubjectMapEntity(
    val formulaId: String,
    /** 落库短码（"1" / "2" / "3"），与 [com.example.formulamaster.domain.KaoyanSubject.code] 对齐。 */
    val subjectType: String
)
