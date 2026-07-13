package com.example.formulamaster.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 种子数据质量校验测试：合成用例验校验器逻辑 + 真实 assets/formulas.json 全量体检。
 */
class FormulaSeedValidatorTest {

    private fun good() = FormulaSeedValidator.Row(
        formulaId = "a", subject = "高数", chapter = "极限", title = "公式A", latexCode = "x=1",
        clozeData = "[]", derivationSteps = "[]", preconditions = "[]",
        parents = "[]", siblings = "[]", confusableWith = "[]",
        typicalProblems = "[]", commonErrors = "[]",
        difficultyLevel = 3, examWeight = 3, appliesTo = listOf("1", "2", "3"), scene = "KaoyanMath"
    )

    @Test
    fun `合法行无错误`() {
        assertTrue(FormulaSeedValidator.validate(listOf(good())).isEmpty())
    }

    @Test
    fun `必填字段缺失报错`() {
        val e = FormulaSeedValidator.validate(listOf(good().copy(latexCode = "")))
        assertTrue(e.any { it.contains("latexCode") })
    }

    @Test
    fun `subject 非法报错`() {
        val e = FormulaSeedValidator.validate(listOf(good().copy(subject = "政治")))
        assertTrue(e.any { it.contains("subject 非法") })
    }

    @Test
    fun `关系悬空引用报错`() {
        val e = FormulaSeedValidator.validate(listOf(good().copy(parents = "[\"不存在\"]")))
        assertTrue(e.any { it.contains("悬空引用") })
    }

    @Test
    fun `关系自环报错`() {
        val e = FormulaSeedValidator.validate(listOf(good().copy(siblings = "[\"a\"]")))
        assertTrue(e.any { it.contains("自环") })
    }

    @Test
    fun `非法 JSON 数组报错`() {
        val e = FormulaSeedValidator.validate(listOf(good().copy(clozeData = "不是数组")))
        assertTrue(e.any { it.contains("clozeData") })
    }

    @Test
    fun `appliesTo 空或非法报错`() {
        assertTrue(FormulaSeedValidator.validate(listOf(good().copy(appliesTo = emptyList())))
            .any { it.contains("appliesTo 不应为空") })
        assertTrue(FormulaSeedValidator.validate(listOf(good().copy(appliesTo = listOf("9"))))
            .any { it.contains("appliesTo 非法值") })
    }

    @Test
    fun `数值越界报错`() {
        assertTrue(FormulaSeedValidator.validate(listOf(good().copy(difficultyLevel = 7)))
            .any { it.contains("difficultyLevel 越界") })
    }

    @Test
    fun `重复 formulaId 报错`() {
        val e = FormulaSeedValidator.validate(listOf(good(), good()))
        assertTrue(e.any { it.contains("重复 formulaId") })
    }

    // ── 真实 assets/formulas.json 全量体检 ────────────────────────────────────
    @Test
    fun `真实 formulas_json 结构全部合法`() {
        val file = listOf(
            "src/main/assets/formulas.json",
            "app/src/main/assets/formulas.json"
        ).map { File(it) }.firstOrNull { it.exists() }
            ?: error("找不到 formulas.json（工作目录：${File(".").absolutePath}）")

        val rows: List<FormulaSeedValidator.Row> =
            Gson().fromJson(file.readText(), object : TypeToken<List<FormulaSeedValidator.Row>>() {}.type)
        val errors = FormulaSeedValidator.validate(rows)
        assertTrue("种子数据质量问题：\n" + errors.joinToString("\n"), errors.isEmpty())
    }
}
