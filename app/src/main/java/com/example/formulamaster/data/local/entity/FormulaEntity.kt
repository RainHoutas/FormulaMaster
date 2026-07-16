package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 静态公式库表（formulas）
 * 数据由 assets/formulas.json 在首次启动时预加载，运行期只读。
 *
 * 学习流程重构 Sprint 1 Task 1.2 起字段从 9 扩到 18。
 * - [derivationSteps] 格式为 `[{latex, note}, ...]` 对象数组（详见 [com.example.formulamaster.domain.model.DerivationStep]）
 *
 * **Sprint 4 Task 4.1 数据层地基标签化（RFC §9.4 D16）**：
 * - 分类改由**标签体系**唯一承载（`tags` 表 + `entry_tag_map`）；数一二三过滤经 namespace=exam 标签。
 * - 词条间关系（推导 / 易混 / 同族）迁到 `entry_relations` 边表；本表**移除**原
 *   `parents` / `siblings` / `confusableWith` 三个内嵌 JSON 字段（全 App 无消费方，纯迁移）。
 * - [subject] / [chapter] 保留为**显示缓存**（只在种子期从 primary 标签写一次，单写入者不会漂移，
 *   供列表排序 / FormulaIndex 分组 / 卡顶显示等廉价读取；分类真相源在标签表）。
 * - 原 [tags] 自由关键词列保留（详情页 leech 横幅展示用）；其原子化副本也进 namespace=keyword 标签。
 */
@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey val formulaId: String,          // 唯一标识符，如 "calc_taylor_01"
    val subject: String,                        // 【显示缓存】主学科：高数 / 线代 / 概率论（真相源=namespace:subject 主标签）
    val chapter: String,                        // 【显示缓存】主章节（真相源=namespace:chapter 主标签）
    val title: String,                          // 公式名称，如 "泰勒展开式"
    val latexCode: String,                      // 完整 LaTeX 源码
    val clozeData: String,                      // JSON：填空挖空坐标/片段（见 ClozeItem）
    val derivationSteps: String,                // JSON 对象数组（DerivationStep）：[{latex, note}, ...]
    val tags: String,                           // 自由关键词（逗号串），详情页 leech 横幅展示；原子副本进 namespace=keyword 标签
    val difficultyLevel: Int,                   // 客观难度评级 1-5，用于初次激活时权重

    // ── Sprint 1 Task 1.2 新增字段（默认值兜底，避免旧 JSON 反序列化炸）────────
    /** 一句话讲该公式干啥用，C1 识别卡 / C3 条件先行卡的核心展示文字。空字符串表示未标注。 */
    val purpose: String = "",
    /** JSON 字符串数组：适用条件关键词列表（C3 条件先行卡 + clozeData mustBlank 标记的来源）。 */
    val preconditions: String = "[]",
    /** JSON 字符串数组：典型题 ID 或题面（C6 题型反查卡 / worked example 用）。 */
    val typicalProblems: String = "[]",
    /** JSON 字符串数组：常见错误描述（用于挖空概率加权 + 反馈展示）。 */
    val commonErrors: String = "[]",
    /** 可选记忆口诀 / 类比 / 费曼式解释。null 表示未标注。 */
    val mnemonic: String? = null,
    /** JSON 对象数组（FormulaChunk）：[{latex, note}, ...]，七步 Step 2 拆块阅读用。空数组表示未标注。 */
    val chunks: String = "[]",
    /** 1-5 考频权重，影响推送优先级。默认 3（中等）。 */
    val examWeight: Int = 3,
    /**
     * 该公式所属应用场景（[com.example.formulamaster.domain.UseScene] 枚举名）。
     * Sprint 1 Task 1.2 默认 `KaoyanMath`；后续 Scene 实装时按公式实际场景标注。
     */
    val scene: String = "KaoyanMath"
)
