package com.example.formulamaster.domain

/**
 * 学习阶段（Sprint 5，RFC §9.4 D17 后续 / 报告 §3.2·§7.2）。
 *
 * 仅在 [UseScene.KaoyanMath] 下生效。一轮 → 二轮 → 三轮 → 冲刺 → 保持 严格单向推进
 * （D15=A：主 UI 不显示回退，设置页留「重置阶段」后悔药）。每个阶段携带调度参数，
 * 由复习会话构建（新卡上限 / 交错策略）与 FSRS 调度（间隔因子）读取，实现"三轮策略切换"。
 *
 * 参数依据研究报告 §7.2「一轮/二轮/三轮/冲刺推送策略」表：
 *  - 新卡上限：一轮 6-10 → 二轮 3-5 → 三轮 1-3 → 冲刺/保持 关新卡
 *  - retention：一轮 90% / 二轮 88% / 三轮 ~90% / 冲刺 93% / 保持 95%（越高→间隔越短→复习越勤）
 *  - 交错：一轮章内 block（先同族形成模板）→ 二轮章内交错 → 三轮起全交错
 */
enum class StudyPhase(
    /** 单向推进序号（1..5），自动建议 / 重置校验用。 */
    val order: Int,
    val displayName: String,
    /** 每日新公式激活上限（0 = 关闭新卡）。 */
    val newCardsPerDay: Int,
    /**
     * FSRS 复习间隔缩放因子 = ln(desiredRetention)/ln(0.9)。
     * 90%→1.0（基线）；88%→1.21（间隔更长/复习更少）；93%→0.72；95%→0.49（间隔更短/复习更勤）。
     */
    val intervalFactor: Double,
    val interleave: Interleave,
    val description: String
) {
    OneRound(1, "一轮 · 基础", newCardsPerDay = 8, intervalFactor = 1.00, interleave = Interleave.BLOCK,
        description = "系统过教材，编码 + 巩固为主；同族先集中形成模板，暂不交错"),
    TwoRound(2, "二轮 · 强化", newCardsPerDay = 4, intervalFactor = 1.21, interleave = Interleave.WITHIN_CHAPTER,
        description = "题集强化，提取 + 辨析；开启章内交错，引入易混与题型反查"),
    ThreeRound(3, "三轮 · 真题", newCardsPerDay = 2, intervalFactor = 1.00, interleave = Interleave.FULL,
        description = "真题迁移，默写 + 题型反查；全交错，限时召回"),
    Sprint(4, "冲刺", newCardsPerDay = 0, intervalFactor = 0.72, interleave = Interleave.FULL,
        description = "模考查漏，关新卡、高保留；易遗忘 Top 优先"),
    Maintenance(5, "保持", newCardsPerDay = 0, intervalFactor = 0.49, interleave = Interleave.FULL,
        description = "考前一周回归基础，高保留、晨间闪回");

    /** 是否关闭新卡激活。 */
    val newCardsClosed: Boolean get() = newCardsPerDay <= 0

    companion object {
        val Default = OneRound

        /** DataStore 存的是枚举 name，未知/null 兜底默认。 */
        fun fromName(name: String?): StudyPhase = try {
            if (name != null) valueOf(name) else Default
        } catch (e: IllegalArgumentException) {
            Default
        }

        /**
         * 按距考试天数给出建议阶段（自动建议 + 用户确认，②a）。分界依报告 §3.2 时段：
         * >150 天 一轮 / 60-150 二轮 / 30-60 三轮 / 7-30 冲刺 / <7 保持。
         * 无考试日期（daysToExam < 0）返回 null（不建议）。
         */
        fun suggestedFor(daysToExam: Long): StudyPhase? = when {
            daysToExam < 0 -> null
            daysToExam < 7 -> Maintenance
            daysToExam < 30 -> Sprint
            daysToExam < 60 -> ThreeRound
            daysToExam < 150 -> TwoRound
            else -> OneRound
        }
    }
}

/**
 * 交错策略（研究报告 §7.2）。控制复习会话内公式的排列：
 * 集中 vs 交错是 Rohrer(2015) 的核心「良性难度」——交错逼大脑判断"该用哪条公式"。
 */
enum class Interleave {
    /** 章内 block：同章节公式连续排布（一轮，先形成 chunk 模板）。 */
    BLOCK,

    /** 章内交错：同章节内公式打散，章与章之间仍成块（二轮）。 */
    WITHIN_CHAPTER,

    /** 全交错：跨章节完全打散（三轮起）。 */
    FULL
}
