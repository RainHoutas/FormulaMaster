package com.example.formulamaster.domain

import android.graphics.Bitmap

/**
 * OCR 识别接口（依赖倒置）
 *
 * UI 层只依赖此接口，不感知任何具体 SDK。
 * 后期可平滑替换为 MathpixRecognizer / MlKitInkRecognizer / SimpleTexRecognizer 等实现，UI 层零修改。
 *
 * ## 双档识别（Sprint 1 Task 1.1 引入）
 * 手写画布停笔后分两档触发识别：
 * - [RecognitionMode.Light]：300ms 停笔后触发，用于顶部实时预览条的即时反馈
 * - [RecognitionMode.Deep]：1500ms 停笔后触发，用于稳定的多候选选择
 *
 * 不同实现对两档的策略不同，例如：
 * - ML Kit 本地模型：Light = 当前笔画增量识别；Deep = 整段重识别
 * - 云端 API（Mathpix/SimpleTex）：Light = 跳过或查本地缓存；Deep = 真正调用 API 节省费用
 * - Mock：两档延迟不同、返回条数不同
 */
interface MathOcrRecognizer {
    /**
     * 识别手写输入，返回 Top-N 个 LaTeX 候选字符串，按置信度降序排列。
     *
     * @param input 笔画坐标或位图输入
     * @param mode  识别模式，决定实现类的策略（默认 Deep，保持既有调用点兼容）
     */
    suspend fun recognize(
        input: OcrInput,
        mode: RecognitionMode = RecognitionMode.Deep
    ): List<String>
}

/**
 * 识别模式
 *
 * UI 层按 debounce 档位选择：短 debounce → Light，长 debounce → Deep。
 * 实现类可自由定义两档的具体差异，也可忽略该参数（如 Mock）。
 */
enum class RecognitionMode {
    /** 轻量模式：低延迟、少消耗，用于实时预览 */
    Light,

    /** 深度模式：高精度、可调用云端，用于最终候选 */
    Deep
}

/**
 * OCR 输入数据类，同时支持 Bitmap 和笔画坐标两种来源。
 */
sealed class OcrInput {
    /** 来自 Canvas 截图的位图输入 */
    data class BitmapInput(val bitmap: Bitmap) : OcrInput()

    /** 来自 pointerInput 收集的笔画坐标列表，每笔为一组有序坐标点 */
    data class StrokeInput(val strokes: List<List<Pair<Float, Float>>>) : OcrInput()
}
