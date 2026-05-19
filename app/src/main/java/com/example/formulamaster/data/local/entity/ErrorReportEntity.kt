package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 错题反向链路记录（RFC D6=C 落地）—— 学习流程重构 Sprint 1 Task 1.6。
 *
 * 用户在错题本里录入一条错题：来源/章节 + 标签 + **从公式池多选错位公式**。
 * 插入后由 [com.example.formulamaster.domain.ErrorReportProcessor] 触发：
 *   对 `wrongFormulaIdsJson` 列出的每条公式，6 张 SubCardStateEntity 同步压低 stability +
 *   推到次日刷新整点 + lapses+1。
 *
 * 字段约束：
 * - 所有用户输入字段都是 chip / 数字键盘 / 多选 chip 产物，**无自由文字输入**（RFC §3.6 约束）
 * - [sourceTag] 限受限数字编码（如 "2024-18"、"880-186-3"），UI 用数字键盘录入
 * - [note] 字段预留可空 —— 当前不开放，保留是为了后期产品迭代加入"备注"
 *
 * @param id                    自增主键
 * @param createdAt             创建时间（Unix ms）
 * @param subject               高数 / 线代 / 概率 chip
 * @param chapter               章节 chip（从公式池章节列表选）
 * @param sourceType            历年真题 / 模拟卷 / 习题集 / 其他 chip
 * @param sourceTag             受限数字编码（如 "2024-18"）
 * @param wrongFormulaIdsJson   JSON 数组：错位公式的 formulaId 列表
 * @param note                  备注（暂不开放给 UI）
 */
@Entity(
    tableName = "error_reports",
    indices = [Index("createdAt")]
)
data class ErrorReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val subject: String,
    val chapter: String,
    val sourceType: String,
    val sourceTag: String,
    val wrongFormulaIdsJson: String,
    val note: String? = null
)
