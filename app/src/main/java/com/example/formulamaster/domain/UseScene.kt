package com.example.formulamaster.domain

/**
 * 学习流程重构阶段 Sprint 1 Task 1.1 — 应用使用场景。
 *
 * 决定 App 的"外壳行为":学习阶段切换 UI 是否显示、默认 retention、内容包来源、
 * 是否显示考试日期等。内核(FSRS + 6 类卡片矩阵 + 拆块)对所有 Scene 共用。
 *
 * 当前阶段仅实装 [KaoyanMath];[Gaokao]/[SelfStudy] 仅留枚举位,Onboarding 暂不让
 * 用户选 Scene(默认走 [KaoyanMath])。Scene 守卫:任何与"考试日期 / 阶段切换 /
 * 冲刺模式"相关的逻辑必须经过 [UseScene] 分支判断,禁止裸读 `targetExamDate`。
 */
enum class UseScene(
    val displayName: String,
    val description: String
) {
    KaoyanMath(
        displayName = "考研数学",
        description = "面向考研的公式学习与冲刺,默认 retention 90%,启用学习阶段切换 UI"
    ),
    Gaokao(
        displayName = "高考数学",
        description = "(尚未实装)面向高考的公式学习,关闭考研冲刺逻辑"
    ),
    SelfStudy(
        displayName = "日常自学",
        description = "(尚未实装)无考试压力的公式记忆,默认 retention 85%"
    );

    companion object {
        val Default = KaoyanMath
    }
}
