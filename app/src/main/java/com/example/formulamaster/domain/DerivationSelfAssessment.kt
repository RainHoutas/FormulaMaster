package com.example.formulamaster.domain

/**
 * 学习流程重构 Sprint 3 Task 3.1 — C4 推导卡三档自评 → FSRS 评分映射。
 *
 * 用户 2026-06-05 拍板（RFC §3.3 原建议 1/2/4）：
 * - [CANNOT_RECALL]「不会」→ 1（Again）：推不动，短间隔重来
 * - [VIEWED]「查看了」→ 2（Hard）：看了推导才想起来，短间隔
 * - [DERIVED]「推出来了」→ 4（Easy）：心里独立推出，跳过 Good 直接最长间隔
 *
 * 有意跳过评分 3（Good）：推导是高难度卡，「完全推出」值得拿满分长间隔，
 * 「查看了」与「推出」之间不设中间档，避免用户纠结。
 *
 * 纯枚举（无 Android 依赖），供 `C4DerivationPane` 三个按钮直接取 [rating]，单测覆盖映射稳定性。
 */
enum class DerivationSelfAssessment(val rating: Int, val label: String) {
    CANNOT_RECALL(1, "不会"),
    VIEWED(2, "查看了"),
    DERIVED(4, "推出来了")
}
