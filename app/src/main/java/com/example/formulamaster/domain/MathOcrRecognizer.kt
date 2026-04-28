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
     * **重要**：实现类应**吞掉所有异常返回空列表**，避免协程异常冒泡到 Composable
     * 导致 UI 崩溃。设置页"测试连接"使用 [testConnection]，那里需要明确区分错误类型。
     *
     * @param input 笔画坐标或位图输入
     * @param mode  识别模式，决定实现类的策略（默认 Deep，保持既有调用点兼容）
     */
    suspend fun recognize(
        input: OcrInput,
        mode: RecognitionMode = RecognitionMode.Deep
    ): List<String>

    /**
     * 测试连接（鉴权 + 网络可达性），用于设置页"测试连接"按钮。
     *
     * 与 [recognize] 完全不同的设计：本方法**不吞任何异常**，
     * 失败时直接抛对应异常（[retrofit2.HttpException] / [java.net.SocketTimeoutException] /
     * [java.net.UnknownHostException] / [IllegalStateException] 等），
     * 调用方据此区分"Key 无效 / 网络超时 / 服务异常"等不同错误。
     *
     * 默认实现 no-op（Mock 等无需鉴权的实现）。
     * 真实云端识别器（A1 Mathpix / A2 SimpleTex）必须重写此方法做实际鉴权检查。
     */
    suspend fun testConnection() { /* no-op */ }
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
