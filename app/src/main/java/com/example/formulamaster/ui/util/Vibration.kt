package com.example.formulamaster.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 系统级强振动（惩罚级触觉反馈）。
 *
 * Compose 自带的 HapticFeedback（TextHandleMove/LongPress）偏轻，
 * 测试/复习错误场景需要更明显的"惩罚感"，因此走底层 Vibrator。
 *
 * - API 31+：通过 VibratorManager.defaultVibrator
 * - API <31：通过 VIBRATOR_SERVICE（deprecated 但仍可用）
 *
 * @param durationMs 振动时长。普通错题 200ms；顽固难点（lapses ≥ 4）400ms 双倍惩罚。
 */
fun vibrateError(context: Context, durationMs: Long = 200L) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(
        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}
