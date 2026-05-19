package com.example.formulamaster.domain

import com.example.formulamaster.domain.model.ClozeItem
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

object ClozeParser {

    /**
     * 自定义反序列化器：Gson 通过反射跳过 Kotlin 主构造器，
     * data class 的默认值（weight=1, mustBlank=false）不会自动生效，
     * 缺字段时会得到 0/false。手动读取并补默认值，保证旧 JSON 兼容。
     */
    private val itemDeserializer = JsonDeserializer { json, _, _ ->
        val obj = json.asJsonObject
        ClozeItem(
            index = obj.get("index").asInt,
            placeholder = obj.get("placeholder").asString,
            options = obj.getAsJsonArray("options").map { it.asString },
            weight = obj.get("weight")
                ?.takeIf { !it.isJsonNull }
                ?.asInt
                ?.coerceAtLeast(0)
                ?: 1,
            mustBlank = obj.get("mustBlank")
                ?.takeIf { !it.isJsonNull }
                ?.asBoolean
                ?: false
        )
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(ClozeItem::class.java, itemDeserializer)
        .create()

    private val listType = object : TypeToken<List<ClozeItem>>() {}.type

    /**
     * 将 FormulaEntity.clozeData JSON 字符串解析为 [ClozeItem] 列表。
     *
     * 兼容旧 JSON（缺 weight/mustBlank 字段时回落默认值）。
     */
    fun parse(json: String): List<ClozeItem> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<ClozeItem>>(json, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }

    /**
     * C2 加权 cloze 卡用抽样：从 [items] 中挑 [n] 个挖空位置。
     *
     * 规则：
     * 1. `mustBlank = true` 的项强制入选（数量超过 n 时按 weight 降序截断）
     * 2. 剩余名额从非强制项中按 `effectiveWeight = weight × (1 + recentErrors[index] ?: 0)`
     *    加权无放回抽样
     * 3. weight = 0 的项不会被抽到（除非 mustBlank）
     * 4. 不足 n 时返回所有可抽项
     *
     * 结果按 index 升序返回，便于 UI 按公式顺序渲染。
     *
     * @param items         候选项
     * @param n             目标抽样数量
     * @param recentErrors  最近错次映射（index → 错次），用于动态强化薄弱位置
     * @param random        注入 Random，便于测试
     */
    fun weightedSample(
        items: List<ClozeItem>,
        n: Int,
        recentErrors: Map<Int, Int> = emptyMap(),
        random: Random = Random.Default
    ): List<ClozeItem> {
        if (items.isEmpty() || n <= 0) return emptyList()

        val forcedAll = items.filter { it.mustBlank }
        val forced = if (forcedAll.size <= n) {
            forcedAll
        } else {
            forcedAll.sortedByDescending { it.weight }.take(n)
        }
        val picked = forced.toMutableList()

        val needed = (n - picked.size).coerceAtLeast(0)
        if (needed > 0) {
            val remaining = items.filter { !it.mustBlank }.toMutableList()
            val weights = remaining.map { item ->
                val errors = recentErrors[item.index] ?: 0
                (item.weight.toLong() * (1L + errors)).coerceAtLeast(0L)
            }.toMutableList()

            repeat(needed.coerceAtMost(remaining.size)) {
                val totalWeight = weights.sum()
                if (totalWeight <= 0L) return@repeat
                var r = random.nextLong(totalWeight)
                var chosen = -1
                for (i in weights.indices) {
                    r -= weights[i]
                    if (r < 0L) {
                        chosen = i
                        break
                    }
                }
                if (chosen < 0) chosen = weights.indexOfLast { it > 0L }
                if (chosen < 0) return@repeat
                picked.add(remaining[chosen])
                remaining.removeAt(chosen)
                weights.removeAt(chosen)
            }
        }

        return picked.sortedBy { it.index }
    }

    /**
     * 编码六步仪式第 ⑥ 步「最小填空预热」专用：挑 1 个**公式本体**位置挖空。
     *
     * 通过 [preconditions]（FormulaEntity.preconditions 反序列化后的数组）反向排除
     * 条件关键词位置 —— placeholder 字符串落在 preconditions 集合内视为条件，跳过。
     *
     * 兜底：若过滤后没有候选（标注遗漏 / 全是条件），回落到全部 items 随机选一个，
     * 避免编码仪式卡住。
     *
     * @param items         候选项
     * @param preconditions 条件关键词列表
     * @param random        注入 Random，便于测试
     */
    fun minimalSample(
        items: List<ClozeItem>,
        preconditions: List<String> = emptyList(),
        random: Random = Random.Default
    ): ClozeItem? {
        if (items.isEmpty()) return null
        val preconditionSet = preconditions.toSet()
        val body = items.filter { it.placeholder !in preconditionSet }
        val pool = if (body.isNotEmpty()) body else items
        return pool[random.nextInt(pool.size)]
    }
}
