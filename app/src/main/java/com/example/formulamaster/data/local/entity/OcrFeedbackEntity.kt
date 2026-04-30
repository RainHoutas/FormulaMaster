package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sprint 1 Task 1.9 — 识别失败反馈样本
 *
 * 用户在 TestCanvas 点击 👎"都不对"后落库，记录笔画 + 当时的识别候选 + 用户手输的正确 LaTeX。
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
    val correctLatex: String
)
