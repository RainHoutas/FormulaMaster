package com.example.formulamaster.data

/**
 * 全局运行时配置。
 *
 * Sprint 2 Task 2.4 起：[targetExamDate] 已废弃，改由 [AppPreference] 持久化用户设置。
 * 此 object 暂保留以避免外部潜在引用一次性破坏，编译器警告会引导调用方迁移。
 */
@Deprecated(
    message = "改用 AppPreference.settings.value.effectiveTargetExamDate（Sprint 2 Task 2.4）",
    level = DeprecationLevel.WARNING
)
object AppConfig {

    /**
     * @deprecated 用户考试日期已迁移到 [AppPreference]（DataStore 持久化 + 设置页可改）。
     * 当前值仅作为代码兼容残留，不再被任何业务逻辑读取。
     */
    @Deprecated(
        message = "改读 AppPreference.settings.value.effectiveTargetExamDate",
        level = DeprecationLevel.WARNING
    )
    var targetExamDate: Long = 1_798_243_200_000L
}
