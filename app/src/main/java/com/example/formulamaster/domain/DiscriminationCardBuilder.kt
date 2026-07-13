package com.example.formulamaster.domain

import kotlin.random.Random

/**
 * C5 易混辨析卡的「N 选 1」内容构造器（学习流程重构 · 最后一张卡型的**代码半**）。
 *
 * 与 C6 题型反查同构，差异在**干扰项来源**：C6 用「同章节」凑候选池，C5 专挑
 * [com.example.formulamaster.domain.EntryRelationType.CONFUSABLE]（原 `confusableWith`）
 * 的**易混邻居**当干扰项，强迫用户在长得像的公式间做**细辨**。
 *
 * 出卡逻辑纯逻辑、不依赖任何内容字段：给定目标 + 易混邻居即可组卡。
 * [diffExplanation]（揭晓后"它们为何不同"的解释文案）是**内容半**，缺内容时传 null，
 * 卡照样能出（只是没有揭晓解释）——因此本构造器不被"缺 diffExplanation"阻塞。
 *
 * 纯函数、无 Room / Android 依赖，可直接单测。
 */
object DiscriminationCardBuilder {

    /** N 选 1 的一个选项（目标或干扰项）。 */
    data class Option(
        val formulaId: String,
        val title: String,
        val latex: String,
    )

    /** 一张组装好的 C5 卡。 */
    data class Card(
        val stem: String,                 // 辨析提示（目标公式的用途/描述，由调用方给定，避免题面泄底）
        val options: List<Option>,        // 已打乱：目标 + 易混干扰项
        val correctId: String,            // 正确项 = 目标公式 id
        val diffExplanation: String?,     // 揭晓后"为何不同"的解释；内容半未落地时为 null
    )

    /** 单卡默认最多选项数（目标 + 至多 3 个易混干扰项），避免易混邻居过多时选项爆炸。 */
    const val DEFAULT_MAX_OPTIONS = 4

    /**
     * 组装一张 C5 易混辨析卡。
     *
     * @param target          目标公式（正确答案）
     * @param stem            辨析提示文案（如目标的用途/适用条件；调用方负责不泄底）
     * @param confusables     易混邻居候选（会剔除目标自身 + 去重 id）
     * @param diffExplanation 揭晓解释文案（内容半；null / 空白视为暂无解释）
     * @param maxOptions      选项上限（含目标），干扰项过多时随机截断
     * @param random          注入 Random，便于测试
     * @return 组装好的卡；**无有效易混干扰项时返回 null**（不构成辨析卡，调用方剔除该卡）
     */
    fun build(
        target: Option,
        stem: String,
        confusables: List<Option>,
        diffExplanation: String? = null,
        maxOptions: Int = DEFAULT_MAX_OPTIONS,
        random: Random = Random.Default,
    ): Card? {
        val distractors = confusables
            .filter { it.formulaId != target.formulaId }
            .distinctBy { it.formulaId }
        if (distractors.isEmpty()) return null

        val keptDistractors = if (maxOptions >= 2 && distractors.size > maxOptions - 1) {
            distractors.shuffled(random).take(maxOptions - 1)
        } else {
            distractors
        }

        val options = (keptDistractors + target).shuffled(random)
        return Card(
            stem = stem,
            options = options,
            correctId = target.formulaId,
            diffExplanation = diffExplanation?.takeIf { it.isNotBlank() },
        )
    }

    /** 判分：所选项 id 命中正确项即答对。 */
    fun isCorrect(card: Card, selectedId: String?): Boolean =
        selectedId != null && selectedId == card.correctId
}
