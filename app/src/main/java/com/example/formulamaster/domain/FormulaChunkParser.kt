package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.FormulaChunk
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * 公式拆块 JSON 解析（Sprint 6.2）。与 [DerivationStepParser] 同构：
 * 输入 [com.example.formulamaster.data.local.entity.FormulaEntity.chunks] 的 JSON 字符串，
 * 输出 [FormulaChunk] 列表；空 / 异常返回空列表（不抛，由 UI 回落占位）。
 */
object FormulaChunkParser {

    private val gson = Gson()
    private val listType = object : TypeToken<List<FormulaChunk>>() {}.type

    fun parse(json: String): List<FormulaChunk> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
}
