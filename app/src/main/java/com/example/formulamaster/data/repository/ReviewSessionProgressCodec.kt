package com.example.formulamaster.data.repository

import com.example.formulamaster.domain.CardType
import com.example.formulamaster.domain.ReviewRouter
import com.example.formulamaster.domain.ReviewRouter.DictationState
import com.example.formulamaster.domain.ReviewRouter.FormulaContext
import com.example.formulamaster.domain.ReviewRouter.PhaseStatus
import com.example.formulamaster.domain.ReviewRouter.RouterState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Sprint 2 Task 2.1b：[RouterState] ↔ JSON 编解码。
 *
 * **为什么不直接 Gson 序列化 RouterState**：
 * [DictationState] 是 sealed class（NotStarted object + InProgress data class），
 * Gson 默认 reflection 序列化对 sealed 的 object 子类支持不友好，
 * 反序列化时会还原成一个新实例而不是 singleton object，
 * 在 `when` 模式匹配（`is DictationState.NotStarted`）下会失败。
 *
 * 解决方案：定义一个**扁平 DTO**层，把 sealed/enum 等所有"非平凡"类型
 * 都映射到原生类型（String / Int / List / Map），Gson 处理这些零负担。
 *
 * - [PhaseStatus] enum → name 字符串
 * - [CardType] enum → code 字符串（"c1"~"c6"，落库稳定，不受枚举重排影响）
 * - [DictationState] sealed → [FormulaContextDto.dictationErrorCount]：
 *   - null = NotStarted
 *   - 非 null = InProgress(errorCount)
 *
 * **前向兼容**：反序列化时遇到未知 CardType code（如未来扩展 c7）会被静默剔除，
 * 不抛异常。这意味着升级 APP 后中途会话的 cursor 可能错位，但不会 crash；
 * ViewModel 在 hydrate 后应做一次 sanity check（[validate] 提供）。
 */
object ReviewSessionProgressCodec {

    private val gson = Gson()
    private val listType = object : TypeToken<List<FormulaContextDto>>() {}.type

    // ── DTO ───────────────────────────────────────────────────────────────────

    /**
     * 扁平 DTO，全部字段都是 Gson 友好的原生类型。
     *
     * 字段一一对应 [FormulaContext]，除了：
     * - `dictation: DictationState` → [dictationErrorCount]：null = NotStarted; n = InProgress(n)
     * - 各种 CardType / PhaseStatus 全部用字符串
     */
    data class FormulaContextDto(
        val formulaId: String,
        val dueCardCodes: List<String>,
        val cursor: Int,
        val roundLapsesMap: Map<String, Int>,
        val reinforcementCardCodes: List<String>,
        val reinforcementRetestDone: Boolean,
        val phaseStatusName: String,
        val dictationErrorCount: Int?,
        val wasPreviouslyBlocked: Boolean
    )

    // ── 编码 ──────────────────────────────────────────────────────────────────

    fun encode(formulas: List<FormulaContext>): String {
        val dtos = formulas.map { it.toDto() }
        return gson.toJson(dtos, listType)
    }

    fun encode(state: RouterState): String = encode(state.formulas)

    private fun FormulaContext.toDto() = FormulaContextDto(
        formulaId = formulaId,
        dueCardCodes = dueCards.map { it.code },
        cursor = cursor,
        roundLapsesMap = roundLapses.mapKeys { it.key.code },
        reinforcementCardCodes = reinforcementCards.map { it.code },
        reinforcementRetestDone = reinforcementRetestDone,
        phaseStatusName = phaseStatus.name,
        dictationErrorCount = when (val d = dictation) {
            DictationState.NotStarted     -> null
            is DictationState.InProgress  -> d.errorCount
        },
        wasPreviouslyBlocked = wasPreviouslyBlocked
    )

    // ── 解码 ──────────────────────────────────────────────────────────────────

    /**
     * 解码失败（json 损坏 / 字段缺失）时返回 null，由调用方走"新会话"兜底路径，
     * 不抛异常。这是 destructiveMigration 风格的"宁可丢一次会话也不闪退"。
     */
    fun decode(json: String?): List<FormulaContext>? {
        if (json.isNullOrBlank()) return null
        return try {
            val dtos: List<FormulaContextDto> = gson.fromJson(json, listType) ?: return null
            dtos.map { it.toContext() }
        } catch (_: Exception) {
            null
        }
    }

    private fun FormulaContextDto.toContext(): FormulaContext {
        val due = dueCardCodes.mapNotNull { CardType.fromCode(it) }
        val lapses: Map<CardType, Int> = roundLapsesMap.entries
            .mapNotNull { (k, v) -> CardType.fromCode(k)?.let { it to v } }
            .toMap()
        val reinforce: Set<CardType> = reinforcementCardCodes
            .mapNotNull { CardType.fromCode(it) }
            .toSet()
        val phase = runCatching { PhaseStatus.valueOf(phaseStatusName) }
            .getOrDefault(PhaseStatus.Reviewing)
        val dictation: DictationState = when (val c = dictationErrorCount) {
            null -> DictationState.NotStarted
            else -> DictationState.InProgress(c)
        }
        return FormulaContext(
            formulaId = formulaId,
            dueCards = due,
            cursor = cursor.coerceAtLeast(0),
            roundLapses = lapses,
            reinforcementCards = reinforce,
            reinforcementRetestDone = reinforcementRetestDone,
            phaseStatus = phase,
            dictation = dictation,
            wasPreviouslyBlocked = wasPreviouslyBlocked
        )
    }

    // ── 一致性校验（hydrate 后由 ViewModel 调）──────────────────────────────

    /**
     * 反序列化后做 sanity check：cursor 不应超出 dueCards.size。
     * 升级 APP 导致 CardType 集合变化、或本地写入异常时可能出现越界。
     * 越界时 ViewModel 应丢弃会话进度，按新会话流程走。
     */
    fun validate(ctx: FormulaContext): Boolean {
        if (ctx.cursor < 0) return false
        if (ctx.cursor > ctx.dueCards.size) return false
        return true
    }

    fun validate(contexts: List<FormulaContext>): Boolean = contexts.all { validate(it) }
}
