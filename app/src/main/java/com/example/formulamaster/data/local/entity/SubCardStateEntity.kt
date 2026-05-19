package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 子卡级 FSRS 状态表（sub_card_states）—— 学习流程重构 Sprint 1 Task 1.5 / RFC D10。
 *
 * 复合主键 `(formulaId, cardType)`：一条公式对应 6 张子卡（C1~C6），每张独立 FSRS 参数。
 *
 * 母 [StudyStateEntity] **保留**，作为公式整体进度展示（learningState 着色 / Mastered 状态等）。
 * 本表只承载 FSRS 调度所需的逐卡 S/D/lastReview/nextReview/lapses，无 learningState。
 *
 * [cardType] 存 [com.example.formulamaster.domain.CardType.code] 字符串，
 * 不存数字 ordinal —— 防止枚举重排后旧记录错位。
 *
 * @param formulaId               外键 → FormulaEntity.formulaId（级联删除）
 * @param cardType                CardType.code（"c1"~"c6"）
 * @param stability               记忆稳定性 S（天）
 * @param difficulty              主观难度 D，范围 [1.0, 5.0]
 * @param lastReviewTime          上次复习时间戳（Unix ms）；首次创建时可置 0
 * @param nextReviewTime          下次复习时间戳（Unix ms），已截断到刷新整点
 * @param totalReviews            累计复习次数
 * @param lapses                  遗忘次数（R=1 累计）
 * @param consecutiveGoodReviews  连续 R≥3 次数（用于子卡是否升入稳定档判断）
 */
@Entity(
    tableName = "sub_card_states",
    primaryKeys = ["formulaId", "cardType"],
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("formulaId")]
)
data class SubCardStateEntity(
    val formulaId: String,
    val cardType: String,
    val stability: Double,
    val difficulty: Double,
    val lastReviewTime: Long,
    val nextReviewTime: Long,
    val totalReviews: Int,
    val lapses: Int,
    val consecutiveGoodReviews: Int = 0
)
