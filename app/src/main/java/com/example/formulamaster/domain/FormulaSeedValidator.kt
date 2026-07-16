package com.example.formulamaster.domain

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * 种子数据质量校验（改进点池 P2「种子测试数据质量」）。
 *
 * 对 `assets/formulas.json` 做**结构性**校验（不校验内容完整度——parents/confusableWith 稀疏
 * 是内容债不是错误）：必填字段非空、JSON 数组合法、关系边不悬空/不自环、枚举取值合法。
 * 纯函数、无 Room / Android / IO 依赖，可直接单测；由测试驱动真实 assets 全量体检。
 */
object FormulaSeedValidator {

    /** 校验用最小行（字段可空，缺字段即报错）。 */
    data class Row(
        val formulaId: String? = null,
        val subject: String? = null,
        val chapter: String? = null,
        val title: String? = null,
        val latexCode: String? = null,
        val clozeData: String? = null,
        val derivationSteps: String? = null,
        val preconditions: String? = null,
        val parents: String? = null,
        val siblings: String? = null,
        val confusableWith: String? = null,
        val typicalProblems: String? = null,
        val commonErrors: String? = null,
        val difficultyLevel: Int? = null,
        val examWeight: Int? = null,
        val appliesTo: List<String>? = null,
        val scene: String? = null,
        val chunks: String? = null
    )

    private val gson = Gson()
    private val strListType = object : TypeToken<List<String>>() {}.type

    private val VALID_SUBJECTS = setOf("高数", "线代", "概率论")
    private val VALID_EXAM = setOf("1", "2", "3")

    /** JSON 数组字段名（全部须是合法 JSON 数组）。 */
    private val ARRAY_FIELDS = listOf(
        "clozeData", "derivationSteps", "preconditions",
        "parents", "siblings", "confusableWith", "typicalProblems", "commonErrors"
    )

    /** @return 错误描述列表；空表示全部通过。 */
    fun validate(rows: List<Row>): List<String> {
        val errors = mutableListOf<String>()
        val ids = rows.mapNotNull { it.formulaId }
        val idSet = ids.toHashSet()

        // 全局：id 唯一
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            errors += "重复 formulaId：$it"
        }

        rows.forEachIndexed { i, r ->
            val tag = r.formulaId ?: "#$i"

            // 必填非空
            requireNonBlank("formulaId", r.formulaId, tag, errors)
            requireNonBlank("subject", r.subject, tag, errors)
            requireNonBlank("chapter", r.chapter, tag, errors)
            requireNonBlank("title", r.title, tag, errors)
            requireNonBlank("latexCode", r.latexCode, tag, errors)

            // 枚举
            r.subject?.let { if (it !in VALID_SUBJECTS) errors += "[$tag] subject 非法：$it" }
            r.scene?.let { if (it.isBlank()) errors += "[$tag] scene 不应为空" }

            // 数值范围
            r.difficultyLevel?.let { if (it !in 1..5) errors += "[$tag] difficultyLevel 越界(1-5)：$it" }
            r.examWeight?.let { if (it !in 1..5) errors += "[$tag] examWeight 越界(1-5)：$it" }

            // appliesTo
            val applies = r.appliesTo
            if (applies == null || applies.isEmpty()) {
                errors += "[$tag] appliesTo 不应为空"
            } else {
                applies.filter { it !in VALID_EXAM }.forEach { errors += "[$tag] appliesTo 非法值：$it" }
            }

            // JSON 数组合法性
            mapOf(
                "clozeData" to r.clozeData, "derivationSteps" to r.derivationSteps,
                "preconditions" to r.preconditions, "parents" to r.parents,
                "siblings" to r.siblings, "confusableWith" to r.confusableWith,
                "typicalProblems" to r.typicalProblems, "commonErrors" to r.commonErrors
            ).forEach { (name, v) ->
                if (name in ARRAY_FIELDS && v != null && !isJsonArray(v)) {
                    errors += "[$tag] $name 不是合法 JSON 数组"
                }
            }

            // 关系边：引用存在 + 不自环
            listOf("parents" to r.parents, "siblings" to r.siblings, "confusableWith" to r.confusableWith)
                .forEach { (name, v) ->
                    parseIds(v).forEach { ref ->
                        if (ref == r.formulaId) errors += "[$tag] $name 自环引用"
                        else if (ref !in idSet) errors += "[$tag] $name 悬空引用：$ref"
                    }
                }

            // clozeData 元素结构：ClozeParser.itemDeserializer 直接 obj.get("index").asInt /
            // getAsJsonArray("options")，缺字段会 NPE 且不被 parse() 的 JsonSyntaxException catch 兜住
            // → 运行时崩。此处提前拦截，保证「能过校验＝上线能解析」。
            validateClozeItems(r.clozeData, tag, errors)
            // derivationSteps 元素：DerivationStepParser 用宽松 Gson（缺字段回落默认，不崩），
            // 仅要求每个元素是对象，避免 ["纯字符串"] 旧格式被静默吞成空推导。
            validateObjectArray("derivationSteps", r.derivationSteps, tag, errors)
            // chunks 元素（Sprint 6.2）：同 derivationSteps，每元素须是 {latex, note} 对象
            if (r.chunks != null && !isJsonArray(r.chunks)) errors += "[$tag] chunks 不是合法 JSON 数组"
            validateObjectArray("chunks", r.chunks, tag, errors)
        }
        return errors
    }

    /** clozeData 每个元素须是对象且含 index(数字) / placeholder(非空字符串) / options(非空字符串数组)。 */
    private fun validateClozeItems(json: String?, tag: String, out: MutableList<String>) {
        val arr = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return
        arr.forEachIndexed { i, el ->
            val where = "clozeData[$i]"
            if (!el.isJsonObject) { out += "[$tag] $where 不是对象"; return@forEachIndexed }
            val obj = el.asJsonObject
            val idx = obj.get("index")
            if (idx == null || idx.isJsonNull || !idx.isJsonPrimitive || !idx.asJsonPrimitive.isNumber)
                out += "[$tag] $where 缺 index 或非数字"
            val ph = obj.get("placeholder")
            if (ph == null || ph.isJsonNull || !ph.isJsonPrimitive || ph.asString.isBlank())
                out += "[$tag] $where 缺 placeholder 或为空"
            val opt = obj.get("options")
            if (opt == null || !opt.isJsonArray || opt.asJsonArray.size() == 0)
                out += "[$tag] $where 缺 options 或非空数组"
            else opt.asJsonArray.forEachIndexed { j, o ->
                if (!o.isJsonPrimitive) out += "[$tag] $where.options[$j] 非字符串"
            }
        }
    }

    /** 数组每个元素须是 JSON 对象（用于 derivationSteps 等对象数组）。 */
    private fun validateObjectArray(name: String, json: String?, tag: String, out: MutableList<String>) {
        val arr = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return
        arr.forEachIndexed { i, el ->
            if (!el.isJsonObject) out += "[$tag] $name[$i] 不是对象"
        }
    }

    private fun requireNonBlank(field: String, v: String?, tag: String, out: MutableList<String>) {
        if (v.isNullOrBlank()) out += "[$tag] $field 必填且非空"
    }

    private fun isJsonArray(s: String): Boolean = runCatching {
        JsonParser.parseString(s).isJsonArray
    }.getOrDefault(false)

    private fun parseIds(json: String?): List<String> {
        if (json == null) return emptyList()
        return runCatching { gson.fromJson<List<String>>(json, strListType) }.getOrNull() ?: emptyList()
    }
}
