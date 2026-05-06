package com.example.formulamaster.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.formulamaster.domain.model.FormulaWithState

/**
 * Sprint 3 Task 3.2 — 纸笔自评输入区
 *
 * ## 使用场景
 * 用户在设置页选择"纸笔自评"模式后，[com.example.formulamaster.ui.screen.TestScreen]
 * 将用本组件替换 [TestCanvas]，不再调用任何 OCR 识别器，不消耗 API 额度。
 * 进入此路径时 TestScreen 已强制横屏（[android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE]），
 * 横屏为公式最大化展示提供更大宽度。
 *
 * ## 两阶段交互流程
 *
 * **Phase 1（默写阶段）**
 * - 屏幕展示题目标题（答案隐藏），用户在纸上手写公式。
 * - 显示居中的说明文案和"✏️"图标。
 * - 底部一个全宽"已完成默写"大按钮，点击进入 Phase 2。
 *
 * **Phase 2（核对阶段）**
 * - 标准答案以 [MathFormulaView]（fillMaxWidth + weight 占满剩余空间）最大化渲染。
 * - 底部两个自评按钮：
 *   - 「出现错误」（错误色 TextButton）→ [onSubmitError]
 *   - 「完全正确」（FilledTonalButton）→ [onSubmitCorrect]
 *
 * ## UDF 约定
 * - 计时从首次 Composition 开始（[questionStartMs]），与手写路径对齐。
 * - 评分结果通过回调上抛到 [com.example.formulamaster.ui.screen.TestScreen]，
 *   再由 [com.example.formulamaster.ui.viewmodel.TestViewModel.submitJudgment] 落库。
 * - 本组件不持有 ViewModel 引用，保持无状态（除本地 UI 阶段切换）。
 */
@Composable
fun PaperPenInputArea(
    item: FormulaWithState,
    progressText: String,
    onSubmitCorrect: (costMs: Long) -> Unit,
    onSubmitError: (costMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val questionStartMs = remember { System.currentTimeMillis() }
    // Phase 1 = false（默写阶段）；Phase 2 = true（核对阶段）
    var isDone by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {

        // ── 固定顶部：进度文字 + 题目标题 ────────────────────────────────────────
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.formula.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ── 弹性中部：Phase 1 默写提示 / Phase 2 标准答案 ────────────────────────
        // weight(1f) 使此区域占满固定顶部和固定底部之间的全部剩余空间
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!isDone) {
                // ── Phase 1：居中说明文案 ─────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✏️",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "请在纸上默写该公式",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "完成后点击「已完成默写」，屏幕将展示标准答案供你对照",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                // ── Phase 2：标准答案最大化展示 + 自评按钮 ───────────────────────
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "标准答案",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // 公式占满 Column 剩余高度（weight(1f) 在此 Column scope 内生效）
                    MathFormulaView(
                        latex = item.formula.latexCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 自评按钮并排，等宽
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onSubmitError(System.currentTimeMillis() - questionStartMs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "出现错误",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                onSubmitCorrect(System.currentTimeMillis() - questionStartMs)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "完全正确",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // ── 固定底部："已完成默写"按钮（仅 Phase 1 显示）────────────────────────
        // Phase 2 时此按钮消失，自评按钮已在中部弹性区内，避免两排按钮并存
        if (!isDone) {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { isDone = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "已完成默写",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
