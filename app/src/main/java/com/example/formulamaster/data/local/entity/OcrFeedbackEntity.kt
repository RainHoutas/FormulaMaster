package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sprint 1 Task 1.9 — 识别失败反馈样本
 *
 * 用户在 TestCanvas 点击「都不对」后落库，记录笔画 + 当时的识别候选 +
 * 用户标记的错误部件（Sprint 3 Task 3.4 起改用 placeholder 多选）。
 *
 * ## 字段说明
 * - [strokesJson]：`List<List<Pair<Float, Float>>>` 序列化为 JSON，每笔为一组有序坐标点。
 *   不直接持有 Bitmap 是因为笔画时序数据更轻量，且未来 L2 端侧训练（CROHME 风格）也以
 *   笔画时序为输入。导出 JSON 时给数据科学/训练端用。
 * - [candidatesJson]：识别器返回的候选列表 JSON（可能为空字符串 "[]" 表示当时无候选）。
 *   含 LatexNormalizer 处理后的最终结果，反映用户实际看到的输出。
 * - [recognizerType]：`RecognizerType.name`（如 "A2_SimpleTex_Turbo"）或 "none"（无绑定）。
 * - [mode]：`"Light"` / `"Deep"`，便于区分两档失败模式。
 * - [formulaId]：可选，仅在严测场景（[com.example.formulamaster.ui.screen.TestScreen]）有上下文。
 *
 * ## Sprint 3 Task 3.4 schema 升级
 * - [wrongPlaceholdersJson]：用户从 [com.example.formulamaster.domain.model.ClozeItem.placeholder]
 *   列表中多选的错误子部件 LaTeX 字符串，序列化为 JSON 数组。
 *   "都不对"快捷按钮 = 全选所有 placeholder；clozeData 空数组场景下为 `[]`。
 * - [correctLatex]：原"用户手输正确 LaTeX"。Sprint 3 起改 nullable 保留：
 *   - 新版本反馈不再让用户手输 LaTeX（输入法自研工程量过大已搁置），统一为 null
 *   - 旧版本（v2 destructive migration 前导出过的）数据导出 JSON 仍兼容
 *
 * ## 设计决策
 * 不冗余 subject/chapter 字段——导出 JSON 时按 formulaId join formulas 表即可。
 *
 * ## 后续用途
 * 累计到一定量（≥200 条）后可作为改进点池中"L2 端侧识别"的训练数据起点。
 * 详见 [`docs/改进点池.md`] 中"端侧 TFLite/ONNX 手写公式识别"条目。
 */
@Entity(tableName = "ocr_feedback")
data class OcrFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val formulaId: String?,
    val recognizerType: String,
    val mode: String,
    val strokesJson: String,
    val candidatesJson: String,
    /** Sprint 3 Task 3.4 起改 nullable：新流程不再用，旧导出兼容。 */
    val correctLatex: String?,
    /** Sprint 3 Task 3.4 新增：用户标记错误部件的 LaTeX 列表（JSON 数组）。 */
    val wrongPlaceholdersJson: String?
)
