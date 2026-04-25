package com.example.formulamaster.domain

import kotlinx.coroutines.delay

/**
 * MathOcrRecognizer 的 Mock 实现，用于开发阶段跑通 UI 流程。
 *
 * ## Sprint 1 Task 1.1 起支持双档识别
 * - [RecognitionMode.Light]：模拟 120ms 低延迟，返回置信度最高的 1 条候选
 * - [RecognitionMode.Deep]：模拟 500ms 延迟，返回 3 条候选
 *
 * 真实 SDK 会在 Light 档做增量识别或本地缓存查询，在 Deep 档调完整模型/API。
 * Mock 不解析输入，仅用于跑通 UI 流程。
 */
class MockMathRecognizer : MathOcrRecognizer {
    override suspend fun recognize(
        input: OcrInput,
        mode: RecognitionMode
    ): List<String> {
        return when (mode) {
            RecognitionMode.Light -> {
                delay(120L)
                FULL_CANDIDATES.take(1)
            }
            RecognitionMode.Deep -> {
                delay(500L)
                FULL_CANDIDATES.take(3)
            }
        }
    }

    private companion object {
        val FULL_CANDIDATES = listOf(
            "\\int_0^1 x^2 \\, dx",
            "\\frac{d}{dx}f(x)",
            "\\sum_{n=1}^{\\infty} \\frac{1}{n^2}"
        )
    }
}
