package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 复习会话进度持久化（学习流程重构 Sprint 2 Task 2.1b）。
 *
 * **单行表**（[id] 固定 [SINGLETON_ID]）：APP 一次只有一个在进行的复习会话，
 * 用 REPLACE 策略 upsert 即可，不需要按用户/设备多行。
 *
 * **生命周期**（RFC §9.3 D-S2-2 补充第 4 条 "同日 cursor 续上 / 跨日重开"）：
 * - 用户点击「复习」Tab 时，ViewModel 读 [sessionDateMs]：
 *   - null 或不是今日 → 走"新会话"路径：FSRS 重新拉 due → [com.example.formulamaster.domain.ReviewRouter.start]
 *   - 同日 → 走"续接"路径：从 [formulaContextsJson] 反序列化 RouterState 续考
 * - 每次 [com.example.formulamaster.domain.ReviewRouter.onInput] 后由 ViewModel upsert 最新状态
 * - 会话结束（所有公式 Graduated / Blocked）→ ViewModel 调 DAO 清空 active 字段
 *
 * **与 [BlockedFormulaEntity] 的分工**：
 * - 本表持有"in-progress 会话状态"，会话结束清空
 * - [BlockedFormulaEntity] 持有"公式级跨会话 blocked 标志"，独立于本表生命周期
 *
 * @param id                    固定为 [SINGLETON_ID]
 * @param sessionDateMs         会话起始的本地"自然日 0 点" ms；null = 无在进行会话
 * @param formulaContextsJson   List&lt;FormulaContextDto&gt; 的 JSON（详见 ReviewSessionProgressCodec）；null = 无会话
 * @param currentFormulaIndex   轮转指针；无会话时为 0
 */
@Entity(tableName = "review_session_progress")
data class ReviewSessionProgressEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val sessionDateMs: Long? = null,
    val formulaContextsJson: String? = null,
    val currentFormulaIndex: Int = 0
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
