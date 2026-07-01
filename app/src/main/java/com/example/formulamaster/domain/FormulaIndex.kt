package com.example.formulamaster.domain

import com.example.formulamaster.data.local.entity.FormulaEntity

/**
 * 公式分类索引（学习流程重构 Sprint 3 Task 3.3 —— 最小实现）。
 *
 * 把一批公式按 `subject（高数/线代/概率论）→ chapter → 公式` 两级分组，并标记每条是否
 * 「已学」（有子卡），供错题本公式多选池按 subject/chapter 导航 + 灰显未学公式。
 *
 * **最小够用边界（2026-07-01 用户拍板）**：只承载错题本公式池所需的「分组 + 未学标记」。
 * Sprint 4 公式族图谱复用同一套分类索引时再按需扩展——**邻接/边/可视化不在此实现**，
 * 避免为尚未设计的图谱过度工程。
 *
 * 纯函数、无 Room / Android 依赖，可直接单测。
 *
 * 分组顺序：保留输入列表中 subject / chapter 的**首次出现顺序**（`groupBy` 走 LinkedHashMap），
 * 公式条目按 `formulaId` 升序稳定排列。调用方若需固定 chip 顺序，自行在传入前排好。
 * kaoyanSubject（数一/二/三）过滤属上游职责（`FormulaRepository.observeFormulasFor`），
 * 本索引不感知。
 */
data class FormulaIndex(
    val subjects: List<SubjectGroup>
) {
    data class SubjectGroup(val subject: String, val chapters: List<ChapterGroup>)
    data class ChapterGroup(val chapter: String, val entries: List<Entry>)

    /** 公式池单条：公式本体 + 是否已学（有子卡）。未学 → UI 灰显 + 点击跳学习。 */
    data class Entry(val formula: FormulaEntity, val isLearned: Boolean)

    /** 全部 subject 名（按首次出现顺序）。 */
    val subjectNames: List<String> get() = subjects.map { it.subject }

    /** 取某 subject 的章节名列表；subject 不存在返回空。 */
    fun chaptersOf(subject: String): List<String> =
        subjects.firstOrNull { it.subject == subject }?.chapters?.map { it.chapter } ?: emptyList()

    /** 取某 (subject, chapter) 的公式条目；不存在返回空。 */
    fun entriesOf(subject: String, chapter: String): List<Entry> =
        subjects.firstOrNull { it.subject == subject }
            ?.chapters?.firstOrNull { it.chapter == chapter }?.entries ?: emptyList()

    /** 展平所有条目（「显示全部」模式用）。 */
    fun allEntries(): List<Entry> = subjects.flatMap { s -> s.chapters.flatMap { it.entries } }

    companion object {
        /**
         * @param formulas          已按 kaoyanSubject 过滤后的公式列表（上游负责）
         * @param learnedFormulaIds 有子卡记录的 formulaId 集合（判「已学」）
         */
        fun build(
            formulas: List<FormulaEntity>,
            learnedFormulaIds: Set<String>
        ): FormulaIndex {
            val subjects = formulas
                .groupBy { it.subject }
                .map { (subject, subjectFormulas) ->
                    val chapters = subjectFormulas
                        .groupBy { it.chapter }
                        .map { (chapter, chapterFormulas) ->
                            ChapterGroup(
                                chapter = chapter,
                                entries = chapterFormulas
                                    .sortedBy { it.formulaId }
                                    .map { Entry(it, it.formulaId in learnedFormulaIds) }
                            )
                        }
                    SubjectGroup(subject, chapters)
                }
            return FormulaIndex(subjects)
        }
    }
}
