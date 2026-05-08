package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.DerivationStep
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * 学习流程重构 Sprint 1 Task 1.2 — 推导链 JSON 解析。
 *
 * 与 [ClozeParser] 同构:输入 [com.example.formulamaster.data.local.entity.FormulaEntity.derivationSteps]
 * 的 JSON 字符串,输出 [DerivationStep] 列表;空 / 异常返回空列表(不抛)。
 *
 * 旧格式(原型阶段:纯字符串数组 `["...","..."]`)解析时会失败被吞,返回空列表。
 * formulas.json 6 条种子在 Task 1.2 已统一重写为新格式,无残留旧字段。
 */
object DerivationStepParser {

    private val gson = Gson()
    private val listType = object : TypeToken<List<DerivationStep>>() {}.type

    fun parse(json: String): List<DerivationStep> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
}
