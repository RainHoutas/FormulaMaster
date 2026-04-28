package com.example.formulamaster.domain

/**
 * 识别器配置的不可变快照。
 *
 * 由 [com.example.formulamaster.data.RecognizerPreference] 从持久化层加载/写入，
 * 由 [RecognizerRegistry] 消费判断可用性与实例化。
 *
 * ## 字段语义
 * - [lightRecognizerId] / [deepRecognizerId]：null = 未绑定（该档不触发识别）
 * - 各识别器的 Key 字段：空字符串 = 未配置
 *
 * ## 默认值
 * 全部 null/空 —— 强制用户首次进设置完成配置（防止"看似工作其实没识别"的隐蔽失败模式）。
 */
data class RecognizerSettings(
    val lightRecognizerId: RecognizerType? = null,
    val deepRecognizerId: RecognizerType? = null,
    val mathpixAppId: String = "",
    val mathpixAppKey: String = "",
    val simpleTexToken: String = "",
)
