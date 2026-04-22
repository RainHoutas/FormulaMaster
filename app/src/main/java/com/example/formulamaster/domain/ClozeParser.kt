package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

object ClozeParser {

    private val gson = Gson()
    private val listType = object : TypeToken<List<ClozeItem>>() {}.type

    /**
     * 将 FormulaEntity.clozeData JSON 字符串解析为 [ClozeItem] 列表。
     *
     * @param json clozeData 字段值，如：
     *   [{"index":1,"placeholder":"\\frac{1}{n!}","options":["\\frac{1}{n!}","n!"]}]
     * @return 解析结果；JSON 为空或解析失败时返回空列表
     */
    fun parse(json: String): List<ClozeItem> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
}
