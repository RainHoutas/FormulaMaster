package com.example.formulamaster.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 学习项「三态可见性」判定（改进点池 #323「人为毙掉的学习项应隐藏」的**代码半**）。
 *
 * 背景：不是每个公式都需要全部学习项板块。必须区分两种「空」——
 * - **人为毙掉**：该公式**特意**不要某项（明确决定）→ [Visibility.HIDDEN] 隐藏该板块
 * - **还没写**：标注阶段尚未填 → [Visibility.PLACEHOLDER] 保留占位（内容债，提醒补）
 *
 * 隐藏由**显式排除标记**（`FormulaEntity.excludedItems`，本轮尚未落库，见下方 TODO）驱动，
 * **不是**由「字段为空」驱动——空字段默认按「未标注/待补」对待，不自动隐藏。
 *
 * 本文件是**纯判定逻辑 + 单测**，不含 Room / UI。落地剩余部分（后续设备场次）：
 * - `FormulaEntity` 加 `excludedItems` 列（JSON 数组，值取 [LearningItem.key]）+ DB v12→v13 迁移
 * - `FormulaDetailScreen` / `FormulaLearnRitualScreen` / C1 识别卡按 [Visibility] 三态渲染
 *
 * 纯函数、无 Room / Android 依赖，可直接单测。
 */
object LearningItemVisibility {

    /** 可被「人为毙掉」的学习项板块。[key] 落库进 `excludedItems` JSON，不要更名（否则旧记录失配）。 */
    enum class LearningItem(val key: String, val displayName: String) {
        PURPOSE("purpose", "用途"),
        PRECONDITION("precondition", "适用条件"),
        DERIVATION("derivation", "推导链"),
        MNEMONIC("mnemonic", "口诀"),
        TYPICAL_PROBLEM("typicalProblem", "典型例题"),
        CONFUSABLE("confusable", "易混辨析"),
        COMMON_ERROR("commonError", "常见错误");

        companion object {
            fun fromKey(key: String): LearningItem? = entries.firstOrNull { it.key == key }
        }
    }

    /** 板块三态。 */
    enum class Visibility {
        /** 有内容 → 正常显示。 */
        SHOWN,

        /** 未毙掉但内容为空 → 显示占位「（暂未标注）」提醒补（内容债）。 */
        PLACEHOLDER,

        /** 人为毙掉 → 隐藏，不占版面。 */
        HIDDEN,
    }

    private val strListType = object : TypeToken<List<String>>() {}.type

    /**
     * 解析 `FormulaEntity.excludedItems` JSON 数组为排除 key 集合。
     * null / 空白 / 非法 JSON → 空集（保守：解析不出就当没毙任何项，不误伤）。
     */
    fun parseExcluded(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            Gson().fromJson<List<String>>(json, strListType)?.filter { it.isNotBlank() }?.toSet()
        }.getOrNull() ?: emptySet()
    }

    /**
     * 单个板块判定。
     * @param isExcluded 该项是否在排除标记内（人为毙掉）
     * @param hasContent 该项是否有实际内容
     */
    fun decide(isExcluded: Boolean, hasContent: Boolean): Visibility = when {
        isExcluded -> Visibility.HIDDEN
        hasContent -> Visibility.SHOWN
        else -> Visibility.PLACEHOLDER
    }

    /**
     * 批量判定所有板块。
     * @param excludedKeys  排除 key 集合（[parseExcluded] 的输出；未知 key 无对应板块自然忽略）
     * @param contentByItem 各板块是否有内容；缺项视为无内容（→ 占位，保守）
     */
    fun decideAll(
        excludedKeys: Set<String>,
        contentByItem: Map<LearningItem, Boolean>,
    ): Map<LearningItem, Visibility> =
        LearningItem.entries.associateWith { item ->
            decide(
                isExcluded = item.key in excludedKeys,
                hasContent = contentByItem[item] == true,
            )
        }
}
