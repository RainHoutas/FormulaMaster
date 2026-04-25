package com.example.formulamaster.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Task 5.4：冲刺期顽固难点（Leech）降噪对话框。
 *
 * 触发条件：冲刺模式激活（SprintModeManager.isActive()）+ lapses ≥ 4 的公式再次失败。
 *
 * 用意：考前时间紧迫，让反复遗忘的"钉子题"无限占据队列反而拖慢整体进度。
 * 此时提示用户"本周先跳过、回到计划后再集中攻克"，避免陷入局部卡点。
 *
 * - [onSkip]      → 推迟 7 天（调用 ViewModel.postponeByWeek）
 * - [onContinue]  → 不做任何额外动作，FSRS 惩罚已由 submit 完成
 */
@Composable
fun SprintSkipDialog(
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("顽固难点") },
        text = {
            Text("冲刺期时间紧迫，该公式可暂时跳过 7 天，回到复习计划后再集中攻克。")
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("继续强攻")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("跳过本周")
            }
        }
    )
}
