package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 静态公式库表（formulas）
 * 数据由 assets/formulas.json 在首次启动时预加载，运行期只读。
 *
 * 学习流程重构 Sprint 1 Task 1.2 起字段从 9 扩到 18:
 * - 加 [purpose] / [preconditions] / [parents] / [siblings] / [confusableWith] /
 *   [typicalProblems] / [commonErrors] / [mnemonic] / [examWeight] / [scene]
 * - [derivationSteps] 格式重写为 `[{latex, note}, ...]` 对象数组（详见 [com.example.formulamaster.domain.model.DerivationStep]）
 *
 * 多对多关系（数一/数二/数三过滤）通过 [FormulaSubjectMapEntity] 单独存储，
 * 此表不冗余 `appliesTo` 字段。
 */
@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey val formulaId: String,          // 唯一标识符，如 "calc_taylor_01"
    val subject: String,                        // 科目：高数 / 线代 / 概率论
    val chapter: String,                        // 章节，如 "一元函数微积分"
    val title: String,                          // 公式名称，如 "泰勒展开式"
    val latexCode: String,                      // 完整 LaTeX 源码
    val clozeData: String,                      // JSON：填空挖空坐标/片段（见 ClozeItem）
    val derivationSteps: String,                // JSON 对象数组（DerivationStep）：[{latex, note}, ...]
    val tags: String,                           // 应用场景标签，逗号分隔或 JSON
    val difficultyLevel: Int,                   // 客观难度评级 1-5，用于初次激活时权重

    // ── Sprint 1 Task 1.2 新增字段（默认值兜底，避免旧 JSON 反序列化炸）────────
    /** 一句话讲该公式干啥用，C1 识别卡 / C3 条件先行卡的核心展示文字。空字符串表示未标注。 */
    val purpose: String = "",
    /** JSON 字符串数组：适用条件关键词列表（C3 条件先行卡 + clozeData mustBlank 标记的来源）。 */
    val preconditions: String = "[]",
    /** JSON 字符串数组：推导上游公式 ID 列表（C4 推导卡跳转用）。 */
    val parents: String = "[]",
    /** JSON 字符串数组：同族公式 ID 列表（公式族图谱用）。 */
    val siblings: String = "[]",
    /** JSON 字符串数组：易混对公式 ID 列表（C5 易混辨析卡用）。 */
    val confusableWith: String = "[]",
    /** JSON 字符串数组：典型题 ID 或题面（C6 题型反查卡 / worked example 用）。 */
    val typicalProblems: String = "[]",
    /** JSON 字符串数组：常见错误描述（用于挖空概率加权 + 反馈展示）。 */
    val commonErrors: String = "[]",
    /** 可选记忆口诀 / 类比 / 费曼式解释。null 表示未标注。 */
    val mnemonic: String? = null,
    /** 1-5 考频权重，影响推送优先级。默认 3（中等）。 */
    val examWeight: Int = 3,
    /**
     * 该公式所属应用场景（[com.example.formulamaster.domain.UseScene] 枚举名）。
     * Sprint 1 Task 1.2 默认 `KaoyanMath`；后续 Scene 实装时按公式实际场景标注。
     */
    val scene: String = "KaoyanMath"
)
