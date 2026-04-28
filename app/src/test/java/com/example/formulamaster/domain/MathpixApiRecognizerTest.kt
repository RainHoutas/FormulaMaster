package com.example.formulamaster.domain

import com.google.gson.Gson
import com.example.formulamaster.domain.MathpixApiRecognizer.MathpixRequest
import com.example.formulamaster.domain.MathpixApiRecognizer.MathpixResponse
import org.junit.Assert.*
import org.junit.Test

/**
 * Sprint 1 Task 1.4 — MathpixApiRecognizer 单元测试
 *
 * 重点验证 **API 契约正确性**（Bug 修复后的核心保证）：
 * 1. 请求体 `formats` 字段值必须是 `latex_styled`（不是 `latex`）
 * 2. 响应解析必须读取顶层 `latex_styled`（不是 `data[]`）
 * 3. 服务端 `error` 字段非空时返回空列表
 *
 * 不验证（需 Android 框架，移交 Robolectric/androidTest）：
 * - Bitmap 编码 base64
 * - StrokeInput 渲染为 PNG
 * - 真实 HTTP 调用
 */
class MathpixApiRecognizerTest {

    private val gson = Gson()

    // ── 1. 请求契约（Bug 1 修复回归） ────────────────────────────────────

    @Test fun `请求 formats 字段使用 latex_styled 而非 latex`() {
        val req = MathpixRequest(src = "data:image/png;base64,xxx")
        val json = gson.toJson(req)
        assertTrue("应包含 latex_styled，实际：$json", json.contains("\"latex_styled\""))
        assertFalse(
            "不应使用 \"latex\" 作为独立 format（无效值会被服务端忽略）",
            json.contains("\"formats\":[\"latex\"]") || json.contains("\"formats\":[\"latex\",")
        )
    }

    @Test fun `请求 src 字段为 data URL 格式`() {
        val req = MathpixRequest(src = "data:image/png;base64,iVBORw0KGgo...")
        val json = gson.toJson(req)
        assertTrue("src 应序列化为 \"data:image/png;base64,…\"",
            json.contains("\"src\":\"data:image/png;base64,iVBORw0KGgo...\""))
    }

    // ── 2. 响应解析（Bug 2 修复回归） ───────────────────────────────────

    @Test fun `从顶层 latex_styled 字段抽取候选`() {
        // 模拟 Mathpix 真实响应（来自 docs.mathpix.com 文档示例）
        val json = """
            {
              "request_id": "abc-123",
              "latex_styled": "x^2 + 2x + 1",
              "confidence": 0.99,
              "is_handwritten": true,
              "image_height": 200,
              "image_width": 800
            }
        """.trimIndent()

        val response = gson.fromJson(json, MathpixResponse::class.java)
        val candidates = MathpixApiRecognizer.extractCandidates(response)

        assertEquals("应抽取 1 个候选", 1, candidates.size)
        assertEquals("x^2 + 2x + 1", candidates[0])
    }

    @Test fun `latex_styled 缺失时返回空列表`() {
        val json = """
            {
              "request_id": "abc-456",
              "text": "\\(x^2\\)",
              "confidence": 0.5
            }
        """.trimIndent()

        val response = gson.fromJson(json, MathpixResponse::class.java)
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertTrue("缺 latex_styled 应返回空列表", candidates.isEmpty())
    }

    @Test fun `latex_styled 为空字符串时返回空列表`() {
        val response = MathpixResponse(latexStyled = "")
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertTrue("空字符串应被过滤", candidates.isEmpty())
    }

    @Test fun `latex_styled 为纯空白时返回空列表`() {
        val response = MathpixResponse(latexStyled = "   \n\t  ")
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertTrue("纯空白应被过滤", candidates.isEmpty())
    }

    // ── 3. 服务端错误处理（200 但 error 非空） ──────────────────────────

    @Test fun `服务端 error 字段非空时返回空列表`() {
        val json = """
            {
              "request_id": "err-001",
              "latex_styled": "fallback_should_be_ignored",
              "error": "Image is too small"
            }
        """.trimIndent()

        val response = gson.fromJson(json, MathpixResponse::class.java)
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertTrue("error 非空时应忽略 latex_styled，返回空", candidates.isEmpty())
    }

    @Test fun `error 为空字符串视为无错误`() {
        val response = MathpixResponse(latexStyled = "x+1", error = "")
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertEquals("error=\"\" 不应触发错误分支", listOf("x+1"), candidates)
    }

    // ── 4. 复杂 LaTeX 整段透传（不做规范化，由 LatexNormalizer 处理） ─────

    @Test fun `复杂积分公式整段透传`() {
        val expected = "\\int_0^1 \\frac{x^2}{1+x^2} dx"
        val response = MathpixResponse(latexStyled = expected, confidence = 0.95)
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertEquals("应原样透传，规范化交给 LatexNormalizer", listOf(expected), candidates)
    }

    @Test fun `带反斜杠和大括号的 LaTeX 不丢失字符`() {
        val expected = "\\sqrt{a^2+b^2}"
        val response = MathpixResponse(latexStyled = expected)
        val candidates = MathpixApiRecognizer.extractCandidates(response)
        assertEquals(listOf(expected), candidates)
    }

    // ── 5. 构造器与接口实现 ──────────────────────────────────────────────

    @Test fun `构造器接受 appId 和 appKey`() {
        val rec = MathpixApiRecognizer("id", "key")
        assertNotNull(rec)
    }

    @Test fun `实现 MathOcrRecognizer 接口`() {
        val rec: MathOcrRecognizer = MathpixApiRecognizer("id", "key")
        assertNotNull(rec)
    }

    // ── 6. testConnection 行为契约（不吞异常） ───────────────────────────

    @Test fun `testConnection 在 appId 缺失时抛 IllegalStateException`() {
        val rec = MathpixApiRecognizer("", "key")
        var thrown: Throwable? = null
        kotlinx.coroutines.runBlocking {
            try {
                rec.testConnection()
            } catch (e: Throwable) {
                thrown = e
            }
        }
        assertNotNull("应抛异常而非吞掉", thrown)
        assertTrue("应是 IllegalStateException", thrown is IllegalStateException)
    }

    @Test fun `testConnection 在 appKey 缺失时抛 IllegalStateException`() {
        val rec = MathpixApiRecognizer("id", "")
        var thrown: Throwable? = null
        kotlinx.coroutines.runBlocking {
            try {
                rec.testConnection()
            } catch (e: Throwable) {
                thrown = e
            }
        }
        assertNotNull(thrown)
        assertTrue(thrown is IllegalStateException)
    }
}
