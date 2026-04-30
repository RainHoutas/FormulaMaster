package com.example.formulamaster.domain

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Sprint 1 Task 1.8 — 识别器异常分类器
 *
 * 把 [MathOcrRecognizer.testConnection] 抛出的异常或 Light/Deep 识别路径捕获到的异常
 * 统一分类为面向用户的简短文案，供 SettingsScreen 状态条与 TestScreen Snackbar 共用，
 * 避免在两处分别维护一份 when 分支（曾出现"测试连接显示'Key 无效'，Snackbar 显示
 * 通用 message"的不一致）。
 */
object RecognizerErrorClassifier {

    /**
     * 分类异常并返回简短中文提示文案。
     * 文案统一控制在 ≤14 汉字以内，便于 Snackbar / Chip 单行显示。
     */
    fun classify(e: Throwable): String = when (e) {
        is UnknownHostException -> "无网络连接"
        is SocketTimeoutException -> "网络超时，请稍后重试"
        is HttpException -> when (e.code()) {
            401, 403 -> "Key 无效或已过期"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务暂时不可用"
            else -> "服务返回错误（HTTP ${e.code()}）"
        }
        else -> e.message?.takeIf { it.isNotBlank() }?.take(40)
            ?: e.javaClass.simpleName
    }
}
