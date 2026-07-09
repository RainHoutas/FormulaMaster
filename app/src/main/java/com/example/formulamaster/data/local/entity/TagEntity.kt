package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签表（学习流程重构 Sprint 4 Task 4.1 —— 数据层地基标签化，详 RFC §9.4 D16）。
 *
 * 词典「外壳」愿景的**分类唯一真相源**：一条词条的所有分类维度（学科 / 章节 / 数一二三 /
 * 关键词 / 未来任意维度）都是这里的一条原子标签。图谱聚合、子科目过滤、将来按任意维度筛选
 * 都读本表 + [EntryTagCrossRef]。
 *
 * [tagId] 用确定性拼接 `"namespace:value"`（见 [com.example.formulamaster.domain.TagNamespace.tagId]），
 * 种子写入天然幂等（同一 namespace/value 只有一行）。`(namespace, value)` 建唯一索引双保险。
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["namespace", "value"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val tagId: String,
    /** 维度名，见 [com.example.formulamaster.domain.TagNamespace]（subject / chapter / exam / keyword / …）。 */
    val namespace: String,
    /** 该维度下的取值，如 "高数" / "微分中值定理" / "1"（数一 code） / "极限"。 */
    val value: String,
    /** 人类可读展示名（多数等于 value；exam 的 "1" → "数学一"）。 */
    val displayName: String
)
