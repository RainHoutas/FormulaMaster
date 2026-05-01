package com.example.formulamaster.domain

/**
 * Sprint 3 — 用户在严测 / 默写场景的输入方式。
 *
 * 用户在「设置 · 输入偏好」中二选一持久化（[com.example.formulamaster.data.AppPreference]），
 * Onboarding 引导也会在首次启动时让用户挑选。
 */
enum class InputMode(
    val displayName: String,
    val description: String
) {
    /**
     * 手写识别（默认）：现有 TestCanvas + 双档识别器（Light / Deep）路径。
     * 需要在设置页配置至少一个识别器（Mathpix 付费 / SimpleTex 免费）才能正常使用。
     */
    Handwriting(
        displayName = "手写识别",
        description = "在屏幕上手写公式 → 自动识别为 LaTeX。需配置识别器（推荐 SimpleTex 免费）"
    ),

    /**
     * 纸笔自评：题目展示 → 用户在纸上写 → 点"已完成"→ 弹标准答案 → 用户自评对错。
     * 不依赖任何识别器，不消耗 API 额度，进入此模式时强制横屏最大化公式显示。
     */
    PaperPen(
        displayName = "纸笔自评",
        description = "屏幕只展示公式，你在纸上写完后自己判断对错。不调用识别器"
    );

    companion object {
        val Default = Handwriting
    }
}
