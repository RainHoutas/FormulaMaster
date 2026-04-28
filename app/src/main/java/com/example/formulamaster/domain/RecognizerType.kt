package com.example.formulamaster.domain

/**
 * 识别器类型枚举。
 *
 * 一个 Type 对应一个识别器实现 + 一组配置参数：
 * - [A1_Mathpix]: Mathpix Snip API（appId + appKey，付费）
 * - [A2_SimpleTex_Standard]: SimpleTex 标准模型（token，500/天免费）
 * - [A2_SimpleTex_Turbo]: SimpleTex 轻量模型（token，2000/天免费）
 *
 * Standard 和 Turbo 共享同一 token，**用户配置一次 token，两个类型同时可用**。
 * 用户可分别绑定到 Light / Deep 两档，例：Light=Turbo（高频预览）/ Deep=Standard（准确把关）。
 *
 * 命名约定：枚举名以"层级 + 提供商[ + 子模型]"形式标记
 * - A 级 = 云端 API（A1, A2, …）
 * - L 级 = 本地端侧（L1 已拒绝；L2 端侧 TFLite 在改进点池）
 *
 * 添加新识别器时，需同步：
 * - 在此枚举追加条目
 * - [RecognizerRegistry.isAvailable] / [RecognizerRegistry.instantiate] 添加分支
 * - [com.example.formulamaster.data.RecognizerPreference] 增加对应配置字段（如需新 Key）
 */
enum class RecognizerType(val displayName: String, val description: String) {
    A1_Mathpix(
        displayName = "Mathpix Snip",
        description = "海外 · 准确率最强 · 付费"
    ),
    A2_SimpleTex_Turbo(
        displayName = "SimpleTex Turbo",
        description = "国内直连 · 2000 次/天 · 速度优先"
    ),
    A2_SimpleTex_Standard(
        displayName = "SimpleTex 标准",
        description = "国内直连 · 500 次/天 · 准确率优先"
    ),
}
