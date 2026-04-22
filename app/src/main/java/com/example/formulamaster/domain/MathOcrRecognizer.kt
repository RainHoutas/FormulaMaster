package com.example.formulamaster.domain

import android.graphics.Bitmap

/**
 * OCR 识别接口（依赖倒置）
 *
 * UI 层只依赖此接口，不感知任何具体 SDK。
 * 后期可平滑替换为 MathpixRecognizer / OnDeviceRecognizer 等实现，UI 层零修改。
 */
interface MathOcrRecognizer {
    /**
     * 识别手写输入，返回 Top-N 个 LaTeX 候选字符串，按置信度降序排列。
     */
    suspend fun recognize(input: OcrInput): List<String>
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
