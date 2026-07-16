package com.example.formulamaster.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.domain.HandwrittenLatexGrader
import com.example.formulamaster.domain.InputMode
import com.example.formulamaster.domain.MathOcrRecognizer
import com.example.formulamaster.domain.RecognizerRegistry

/**
 * 可复用「手写作答」区（Sprint 6.8）：把回想环节从纯自评升级为**真作答**，供学习巩固识别 /
 * 复习 C1 识别 / 复习默写三处共用。自包含，不依赖 TestViewModel / FormulaWithState。
 *
 * 按 [inputMode] 分流：
 * - **手写识别**：[TestCanvas] 手写 → OCR 出候选 → 点候选**分块拼接**整条公式 → 提交 →
 *   [HandwrittenLatexGrader] 与 [answerLatex] **自动判对错**（错可清空重写）。
 * - **纸笔自评**：提示纸上默写 → 看标准答案 → 用户自评写对/写错。
 *
 * @param answerLatex 标准答案 LaTeX
 * @param onGraded    判定完成回调（true=对 / false=错）；调用方据此落评分或入重做队列
 */
/** 手写作答所需的运行时配置（输入模式 + 已解析的 Light/Deep 识别器）。 */
data class HandwritingConfig(
    val inputMode: InputMode,
    val light: MathOcrRecognizer?,
    val deep: MathOcrRecognizer?,
)

/** 从用户偏好一次性解析输入模式 + Light/Deep 识别器，供 [HandwrittenAnswerArea] 三处调用方复用。 */
@Composable
fun rememberHandwritingConfig(): HandwritingConfig {
    val context = LocalContext.current
    val recPref = remember { AppContainer.recognizerPreference(context) }
    val recSettings by recPref.settings.collectAsState()
    val appPref = remember { AppContainer.appPreference(context) }
    val appSettings by appPref.settings.collectAsState()
    val light = remember(recSettings) { RecognizerRegistry.resolveLight(recSettings) }
    val deep = remember(recSettings) { RecognizerRegistry.resolveDeep(recSettings) }
    return HandwritingConfig(appSettings.inputMode, light, deep)
}

@Composable
fun HandwrittenAnswerArea(
    answerLatex: String,
    inputMode: InputMode,
    lightRecognizer: MathOcrRecognizer?,
    deepRecognizer: MathOcrRecognizer?,
    onGraded: (isCorrect: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onWritingButNoRecognizer: () -> Unit = {},
    onDeepFailure: (Throwable) -> Unit = {},
    /** 揭晓/判定后、"继续"前额外展示的内容（如适用条件/用途/口诀）；两种模式的答案揭晓处都会渲染。 */
    revealExtra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    when (inputMode) {
        InputMode.Handwriting -> HandwritingBranch(
            answerLatex, lightRecognizer, deepRecognizer, onGraded,
            onWritingButNoRecognizer, onDeepFailure, revealExtra, modifier
        )
        InputMode.PaperPen -> PaperPenBranch(answerLatex, onGraded, revealExtra, modifier)
    }
}

@Composable
private fun HandwritingBranch(
    answerLatex: String,
    lightRecognizer: MathOcrRecognizer?,
    deepRecognizer: MathOcrRecognizer?,
    onGraded: (Boolean) -> Unit,
    onWritingButNoRecognizer: () -> Unit,
    onDeepFailure: (Throwable) -> Unit,
    revealExtra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    modifier: Modifier,
) {
    val pieces = remember { mutableStateListOf<String>() }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val assembled = pieces.joinToString("")

    Column(modifier) {
        if (result == null) {
            Text(
                "手写这条公式（可分块识别、逐块拼接），完成后提交自动判分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (assembled.isNotBlank()) {
                Text("当前作答", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                MathFormulaView(latex = assembled, modifier = Modifier.fillMaxWidth().height(70.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (pieces.isNotEmpty()) pieces.removeAt(pieces.lastIndex) }) { Text("删除末块") }
                    TextButton(onClick = { pieces.clear() }) { Text("清空") }
                }
            }

            TestCanvas(
                onCandidateSelected = { pieces.add(it) },
                lightRecognizer = lightRecognizer,
                deepRecognizer = deepRecognizer,
                onWritingButNoRecognizer = onWritingButNoRecognizer,
                onDeepFailure = onDeepFailure,
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { result = HandwrittenLatexGrader.isMatch(assembled, answerLatex) },
                enabled = assembled.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (assembled.isNotBlank()) "提交判分" else "先写出公式")
            }
        } else {
            val ok = result == true
            ResultBanner(ok)
            Spacer(Modifier.height(12.dp))
            Text("你的作答", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MathFormulaView(latex = assembled, modifier = Modifier.fillMaxWidth().height(70.dp))
            Spacer(Modifier.height(8.dp))
            Text("标准答案", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            MathFormulaView(latex = answerLatex, modifier = Modifier.fillMaxWidth().height(70.dp))
            revealExtra()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!ok) {
                    OutlinedButton(
                        onClick = { pieces.clear(); result = null },
                        modifier = Modifier.weight(1f)
                    ) { Text("清空重写") }
                }
                Button(onClick = { onGraded(ok) }, modifier = Modifier.weight(1f)) { Text("继续") }
            }
        }
    }
}

@Composable
private fun PaperPenBranch(
    answerLatex: String,
    onGraded: (Boolean) -> Unit,
    revealExtra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    modifier: Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier) {
        if (!revealed) {
            Text("请在纸上默写该公式，写完后看答案对照", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("已写完，看答案") }
        } else {
            Text("标准答案", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            MathFormulaView(latex = answerLatex, modifier = Modifier.fillMaxWidth().height(90.dp))
            revealExtra()
            Spacer(Modifier.height(8.dp))
            Text("与你纸上所写对照，是否完全正确？", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onGraded(false) }, modifier = Modifier.weight(1f)) { Text("写错了") }
                Button(onClick = { onGraded(true) }, modifier = Modifier.weight(1f)) { Text("写对了") }
            }
        }
    }
}

@Composable
private fun ResultBanner(ok: Boolean) {
    Surface(
        color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (ok) "正确 · 系统评定 4" else "不对 · 系统评定 1",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
