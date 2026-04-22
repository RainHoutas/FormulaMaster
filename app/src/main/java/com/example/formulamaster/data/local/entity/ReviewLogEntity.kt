package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学习流水日志表（review_logs）
 * 记录每次交互事件，用于热力图统计、耗时分析、溯源复盘。
 *
 * interactionType：
 *   1 = 初次记忆探索（Memory 模块）
 *   2 = 日常复习唤醒（Review 模块）
 *   3 = 阶段考试严格默写（Test 模块）
 *
 * userRating：
 *   1 = 忘记/错误
 *   2 = 困难
 *   3 = 顺利
 *   4 = 极易/完全正确
 */
@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val formulaId: String,
    val reviewTime: Long,           // 产生记录的 Unix 时间戳（ms）
    val interactionType: Int,
    val userRating: Int,
    val costTimeMs: Long            // 停留耗时（ms），耗时过长暗示记忆不够熟练
)
