package com.example.formulamaster.domain

import com.example.formulamaster.domain.SimpleTexApiRecognizer.SimpleTexResponse
import com.example.formulamaster.domain.SimpleTexApiRecognizer.SimpleTexResult
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Sprint 1 Task 1.5 — SimpleTexApiRecognizer 单元测试
 *
 * 重点验证 **API 契约正确性**：
 * 1. 响应解析必须读取嵌套对象 `res.latex`（不是顶层 `latex`）
 * 2. `status: false` 时返回空列表
 * 3. SimpleTex 真实响应 JSON 反序列化正确
 *
 * 不验证（需 Android 框架，移交 Robolectric/androidTest）：
 * - Bitmap 编码 PNG
 * - StrokeInput 渲染
 * - multipart/form-data 真实请求
 */
class SimpleTexApiRecognizerTest {

    private val gson = Gson()

    // ── 1. 响应解析（成功路径） ──────────────────────────────────────────

    @Test fun `从 res latex 抽取候选`() {
        // 模拟 SimpleTex 真实响应（来自 doc.simpletex.cn 文档示例）
        val json = """
            {
              "status": true,
              "res": {
                "latex": "a^{2}-b^{2}",
                "conf": 0.95
              },
              "request_id": "tr_16755479007123063412063155819"
            }
        """.trimIndent()

        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)

        assertEquals("应抽取 1 个候选", 1, candidates.size)
        assertEquals("a^{2}-b^{2}", candidates[0])
    }

    @Test fun `confidence 字段映射正确`() {
        val json = """
            {
              "status": true,
              "res": { "latex": "x", "conf": 0.875 },
              "request_id": "abc"
            }
        """.trimIndent()
        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        assertEquals(0.875, response.res?.conf!!, 0.0001)
    }

    // ── 2. status: false（Bug 防护：嵌套字段不能被错读为顶层） ────────────

    @Test fun `status false 时返回空列表`() {
        val json = """
            {
              "status": false,
              "res": { "latex": "should_be_ignored", "conf": 0 },
              "request_id": "err-001",
              "message": "Token invalid"
            }
        """.trimIndent()

        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("status=false 时应忽略 res.latex，返回空", candidates.isEmpty())
    }

    @Test fun `失败响应 message 字段被解析`() {
        val json = """
            {
              "status": false,
              "request_id": "x",
              "message": "Invalid token"
            }
        """.trimIndent()
        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        assertEquals("Invalid token", response.message)
        assertNull("失败响应可能无 res 对象", response.res)
    }

    // ── 3. 边界：res 缺失 / latex 为空 / 嵌套异常 ────────────────────────

    @Test fun `res 对象缺失时返回空列表`() {
        val json = """{ "status": true, "request_id": "x" }""".trimIndent()
        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("res 缺失应返回空", candidates.isEmpty())
    }

    @Test fun `res latex 缺失时返回空列表`() {
        val json = """{ "status": true, "res": { "conf": 0.5 } }""".trimIndent()
        val response = gson.fromJson(json, SimpleTexResponse::class.java)
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("latex 字段缺失应返回空", candidates.isEmpty())
    }

    @Test fun `res latex 为空字符串时返回空列表`() {
        val response = SimpleTexResponse(
            status = true,
            res = SimpleTexResult(latex = "")
        )
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("空字符串应被过滤", candidates.isEmpty())
    }

    @Test fun `res latex 为纯空白时返回空列表`() {
        val response = SimpleTexResponse(
            status = true,
            res = SimpleTexResult(latex = "   \n\t  ")
        )
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("纯空白应被过滤", candidates.isEmpty())
    }

    // ── 4. 复杂 LaTeX 整段透传（不做规范化，由 LatexNormalizer 处理） ─────

    @Test fun `复杂积分公式整段透传`() {
        val expected = "\\int_{0}^{1} \\frac{x^{2}}{1+x^{2}} dx"
        val response = SimpleTexResponse(
            status = true,
            res = SimpleTexResult(latex = expected, conf = 0.95)
        )
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertEquals("应原样透传，规范化交给 LatexNormalizer", listOf(expected), candidates)
    }

    @Test fun `带反斜杠和大括号的 LaTeX 不丢失字符`() {
        val expected = "\\sqrt{a^{2}+b^{2}}"
        val response = SimpleTexResponse(
            status = true,
            res = SimpleTexResult(latex = expected)
        )
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertEquals(listOf(expected), candidates)
    }

    // ── 5. 默认值（防 Gson 反序列化时字段缺失抛 NPE） ────────────────────

    @Test fun `空响应不抛 NPE`() {
        val response = gson.fromJson("{}", SimpleTexResponse::class.java)
        // 默认 status=false，extractCandidates 应安全返回空
        val candidates = SimpleTexApiRecognizer.extractCandidates(response)
        assertTrue("空 JSON 应返回空候选，不崩溃", candidates.isEmpty())
    }

    // ── 6. 构造器与接口实现 ──────────────────────────────────────────────

    @Test fun `构造器接受 token`() {
        val rec = SimpleTexApiRecognizer("test_token")
        assertNotNull(rec)
    }

    @Test fun `实现 MathOcrRecognizer 接口`() {
        val rec: MathOcrRecognizer = SimpleTexApiRecognizer("token")
        assertNotNull(rec)
    }
}
