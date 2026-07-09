package com.example.formulamaster.domain

/**
 * 标签命名空间（学习流程重构 Sprint 4 Task 4.1 —— 数据层地基标签化）。
 *
 * 词典「外壳」愿景的地基：一条词条的所有分类维度都表达为 `(namespace, value)` 原子标签，
 * 落库到 `tags` 表 + `entry_tag_map` 多对多。分类机制唯一——加任何新维度（题型 / 考频 /
 * 收藏 / 甚至别领域的词性 / 词根）只需引入新的 namespace 字符串，**无需改 schema**。
 *
 * 命名空间**故意开放**（用字符串而非 sealed enum），以支持未来任意维度扩展。
 * 下列常量只是当前考研数学已用到的几个，非穷举。
 *
 * ⚠ 眼下仅到「分类 + 关系」层通用化；词条内容字段（latexCode / clozeData / derivationSteps
 * 等数学专用字段）不在标签体系内，属将来「内容通用化」阶段的事（详 RFC §9.4 D16）。
 */
object TagNamespace {
    /** 学科：高数 / 线代 / 概率论。isPrimary=true（每词条一主学科，供显示 / 分组）。 */
    const val SUBJECT = "subject"

    /** 章节：微分中值定理 / 一元函数积分学 …。isPrimary=true（每词条一主章节）。 */
    const val CHAPTER = "chapter"

    /** 考研子科目：数一/二/三，value 存 [KaoyanSubject.code]（"1"/"2"/"3"）。多值，非主。 */
    const val EXAM = "exam"

    /** 自由关键词（原 `FormulaEntity.tags` 逗号串拆原子）：极限 / 基本公式 / 三角 …。多值，非主。 */
    const val KEYWORD = "keyword"

    /** 拼确定性 tagId：`"namespace:value"`，种子幂等、无需自增 id + 反查。 */
    fun tagId(namespace: String, value: String): String = "$namespace:$value"
}
