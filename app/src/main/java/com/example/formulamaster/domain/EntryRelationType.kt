package com.example.formulamaster.domain

/**
 * 词条间关系类型（学习流程重构 Sprint 4 Task 4.1）。
 *
 * 取代原 `FormulaEntity` 上的 `parents` / `siblings` / `confusableWith` 三个内嵌 JSON 字段，
 * 统一落到 `entry_relations` 边表 `(fromId, toId, type)`。公式族图谱直接读边表建图；
 * 反查（谁推导出我 / 谁跟我易混）+ 外键防悬空 + 原子化，皆由此解决。
 *
 * [directed]：
 *  - `true`（推导）——有向：`fromId` 由 `toId` 推导得到（`toId` 是 `fromId` 的上游 / 父）。
 *  - `false`（易混 / 同族）——无向：种子写入时按字典序规范化 `(min, max)` 去重，避免双向重复行。
 */
enum class EntryRelationType(val code: String, val directed: Boolean) {
    /** 推导：fromId 由 toId 推导（toId 为上游）。来源 = 旧 `parents`。图谱黑/蓝边。 */
    DERIVATION("derivation", true),

    /** 易混：无向。来源 = 旧 `confusableWith`。图谱红/橙边。C5 易混辨析卡将复用。 */
    CONFUSABLE("confusable", false),

    /** 同族：无向。来源 = 旧 `siblings`。图谱补连通用（把无推导/易混边的孤点连起来）。 */
    SIBLING("sibling", false);

    companion object {
        fun fromCode(code: String?): EntryRelationType? = entries.firstOrNull { it.code == code }
    }
}
