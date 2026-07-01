package com.example.formulamaster.domain

/**
 * 删除错题时对所选公式复习计划的处理策略（学习流程重构 Sprint 3 Task 3.3）。
 *
 * 持久化到 [com.example.formulamaster.data.AppPreference]；删除时若为 [Ask] 弹窗二选一，
 * 否则直接按已记忆的策略执行。
 *
 * @property label 设置页 / 对话框展示文案
 */
enum class ErrorDeletePolicy(val label: String) {
    /** 每次删除都弹窗问「仅删记录 / 恢复计划」。 */
    Ask("每次询问"),

    /** 只删错题记录，不动任何子卡复习计划。 */
    DeleteOnly("仅删记录"),

    /**
     * best-effort 还原：逐子卡回滚到录入前快照，但**录入后已被真实复习触碰过的子卡保留**
     * （惩罚已被消费，见 [com.example.formulamaster.domain.ErrorReportProcessor.deleteReport]）。
     */
    Restore("恢复计划");

    companion object {
        val Default = Ask

        fun fromName(name: String?): ErrorDeletePolicy =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
