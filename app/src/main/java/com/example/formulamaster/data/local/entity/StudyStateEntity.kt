package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户学习状态表（study_states）
 * 每条公式对应一条状态记录，存储 FSRS 算法所需的全部动态参数。
 *
 * learningState 枚举：
 *   0 = New（未学习）
 *   1 = Learning（记忆中）
 *   2 = Reviewing（复习中）
 *   3 = Mastered（已掌握）
 */
@Entity(
    tableName = "study_states",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE   // 公式被删除时级联删除学习记录
        )
    ],
    indices = [Index("formulaId")]
)
data class StudyStateEntity(
    @PrimaryKey val formulaId: String,

    // 状态机
    val learningState: Int,

    // FSRS 核心参数
    val stability: Double,      // 记忆稳定性 S：决定下次复习间隔（天）
    val difficulty: Double,     // 主观难度 D：1.0 ~ 5.0，影响 S 增长速度

    // 调度时间戳（Unix ms）
    val lastReviewTime: Long,
    val nextReviewTime: Long,

    // 统计字段
    val totalReviews: Int,      // 累计复习次数
    val lapses: Int,            // 遗忘次数（评分 R=1 的累计次数）

    // 状态迁移辅助字段（Task 4.6：连续 3 次 R≥3 → Learning 升入 Reviewing）
    val consecutiveGoodReviews: Int = 0
)
