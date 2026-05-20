package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 公式级跨会话"默写被阻断"状态表（学习流程重构 Sprint 2 Task 2.1b）。
 *
 * **设计意图**：
 * RFC §9.3 D-S2-2 补充第 3 条规定，blocked 公式在下次复习时
 * **正常走轮转**（不跳队），但路由到该公式的默写阶段时 UI 需展示红色强提醒。
 * 因此 blocked 是公式级别、跨会话保留的标志，与"当前会话的复习进度"解耦。
 *
 * **生命周期**：
 * - 写入：[com.example.formulamaster.domain.ReviewRouter.Event.FormulaBlocked]
 *   触发时由 ViewModel 写入对应 formulaId 行（如已存在则更新 blockedAt）
 * - 读取：新会话 [com.example.formulamaster.domain.ReviewRouter.start] 前
 *   调用方读取全部 formulaId 集合，传入 `previouslyBlockedFormulas` 参数
 * - 删除：[Event.FormulaGraduated] 触发时由 ViewModel 删该公式行
 *
 * **不放在 [SubCardStateEntity] 里的原因**：blocked 是公式级别（不是 cardType 级别），
 * 且生命周期独立于 FSRS（FSRS 由 nextReviewTime 控制 due，blocked 仅控制 banner）。
 *
 * @param formulaId  公式 ID（CASCADE 删除：FormulaEntity 删除时同步清理）
 * @param blockedAt  最近一次默写被阻断的时间戳（Unix ms），用于潜在的 7 日内统计
 */
@Entity(
    tableName = "blocked_formulas",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["formulaId"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlockedFormulaEntity(
    @PrimaryKey val formulaId: String,
    val blockedAt: Long
)
