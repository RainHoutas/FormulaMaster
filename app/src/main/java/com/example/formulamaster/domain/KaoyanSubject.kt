package com.example.formulamaster.domain

/**
 * 学习流程重构阶段 Sprint 1 Task 1.1 — 考研数学子科目。
 *
 * 仅在 [UseScene.KaoyanMath] 下生效,决定用户看到的公式池子集。
 * 数一/数二/数三公式范围不同(数二不考概率,数三高数部分内容也有差异)。
 *
 * Sprint 4 Task 4.1 起通过统一标签体系过滤：公式挂 `namespace=exam` 标签，
 * [code] 即 `tags.value`（"1"/"2"/"3"），经 `entry_tag_map` JOIN 过滤公式列表。
 */
enum class KaoyanSubject(
    /** 落库 `tags.value`（namespace=exam）的短码,与 `formulas.json` 中 `appliesTo` 数组元素对应。 */
    val code: String,
    val displayName: String,
    val description: String
) {
    Type1(
        code = "1",
        displayName = "数学一",
        description = "理工科,内容最全:高数(全)+ 线代 + 概率"
    ),
    Type2(
        code = "2",
        displayName = "数学二",
        description = "部分理工科,不考概率:高数(部分)+ 线代"
    ),
    Type3(
        code = "3",
        displayName = "数学三",
        description = "经济类:高数(部分)+ 线代 + 概率(部分)"
    );

    companion object {
        val Default = Type1

        /** 解析 DataStore 存储的字符串(枚举 name)到枚举,未知/null 兜底默认。 */
        fun fromName(name: String?): KaoyanSubject = try {
            if (name != null) valueOf(name) else Default
        } catch (e: IllegalArgumentException) {
            Default
        }

        /** 反向:从落库的 [code] 字符串解析回枚举。SubjectMap JOIN 后用。 */
        fun fromCode(code: String?): KaoyanSubject? =
            entries.firstOrNull { it.code == code }
    }
}
