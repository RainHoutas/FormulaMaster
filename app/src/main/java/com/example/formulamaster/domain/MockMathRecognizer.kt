package com.example.formulamaster.domain

import kotlinx.coroutines.delay

/**
 * MathOcrRecognizer 的 Mock 实现，用于开发阶段跑通 UI 流程。
 *
 * 模拟 500ms 网络/计算延迟后，返回三条常见的考研数学 LaTeX 候选。
 * 后期替换为真实 SDK（Mathpix / ML Kit / ONNX）时，只需实现同一接口即可。
 */
class MockMathRecognizer : MathOcrRecognizer {
    override suspend fun recognize(input: OcrInput): List<String> {
        delay(500L) // 模拟识别延迟
        return listOf(
            "\\int_0^1 x^2 \\, dx",
            "\\frac{d}{dx}f(x)",
            "\\sum_{n=1}^{\\infty} \\frac{1}{n^2}"
        )
    }
}
