package com.example.formulamaster.data

/**
 * 全局运行时配置。
 *
 * 当前版本：内存级 object，App 进程内共享。
 * 后续可迁移为 DataStore 持久化（UI 设置页面支持修改考试日期时升级）。
 */
object AppConfig {

    /**
     * 目标考试日期时间戳（Unix ms，UTC）。
     * 默认值：2026-12-26 00:00:00 UTC（考研日期）。
     *
     * 冲刺模式（Sprint Mode）在距此日期 ≤ 30 天时自动激活：
     *   - 所有 stability > 15 的记录 stability 减半（缩短复习间隔）
     *   - 所有 Mastered 公式被强制拉回今日复习队列
     */
    var targetExamDate: Long = 1_798_243_200_000L   // 2026-12-26 UTC
}
