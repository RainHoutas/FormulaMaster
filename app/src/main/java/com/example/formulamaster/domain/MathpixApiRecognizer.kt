package com.example.formulamaster.domain

import android.graphics.Bitmap
import com.example.formulamaster.domain.util.StrokeBitmapRenderer
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Task 1.4 — Mathpix Snip API 识别器
 *
 * 将手写笔画快照或清晰图像发送到 Mathpix Cloud，获取 LaTeX 识别结果。
 *
 * ## 认证
 * - **app_id** & **app_key**：用户从 https://snip.mathpix.com 申请
 * - Header 中以 `app_id` 和 `app_key` 字段传递
 *
 * ## API 契约（按 https://docs.mathpix.com/reference/post-v3-text）
 * - 端点：POST `https://api.mathpix.com/v3/text`
 * - 请求字段：
 *   - `src`: `data:image/png;base64,...` 数据 URL
 *   - `formats`: `["latex_styled"]` —— **`"latex"` 不是合法值，必须用 `latex_styled`**
 * - 响应字段（顶层）：
 *   - `latex_styled`: 单公式纯 LaTeX（**主输出**，不是 `data[]`）
 *   - `text`: 带定界符的 Mathpix Markdown（备用）
 *   - `confidence`: 整体置信度
 *   - `error`: 服务端错误说明（图像太小、内容无法识别等）
 *
 * ## 输入处理
 * - `BitmapInput`：直接编码上传
 * - `StrokeInput`：渲染为白底黑线 PNG，**自动 upscale 到至少 800px 宽**（OCR 准确率优化）
 *
 * ## Light/Deep 档位
 * 本类不感知档位差异——由 [Task 1.6 RecognizerRegistry] 决定哪个识别器实例分配到哪个槽位。
 *
 * ## 错误兜底
 * 任何异常（HTTP / 超时 / 无网络 / 解析失败）均返回空列表并 Logcat 记录原因，绝不向上抛出。
 */
class MathpixApiRecognizer(
    private val appId: String,
    private val appKey: String
) : MathOcrRecognizer {

    private val service: MathpixService by lazy {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mathpix.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(MathpixService::class.java)
    }

    /**
     * 识别 bitmap 输入并返回 LaTeX 候选列表。
     * 支持 Light/Deep 两档，均走相同的识别逻辑（服务端不区分档位）。
     */
    override suspend fun recognize(
        input: OcrInput,
        mode: RecognitionMode
    ): List<String> {
        return try {
            if (appId.isBlank() || appKey.isBlank()) {
                listOf()  // Key 缺失，返回空列表
            } else {
                val bitmap = when (input) {
                    is OcrInput.BitmapInput -> input.bitmap
                    is OcrInput.StrokeInput -> StrokeBitmapRenderer.render(input.strokes)
                }

                val base64 = bitmapToBase64(bitmap)
                val request = MathpixRequest(
                    src = "data:image/png;base64,$base64",
                    formats = listOf("latex_styled")
                )
                val response = service.recognize(appId, appKey, request)
                if (!response.error.isNullOrBlank()) {
                    android.util.Log.w(
                        "MathpixApiRecognizer",
                        "Mathpix server error: ${response.error} (request_id=${response.requestId})"
                    )
                }
                extractCandidates(response)
            }
        } catch (e: retrofit2.HttpException) {
            // HTTP 错误分类
            android.util.Log.w("MathpixApiRecognizer", "HTTP Error: ${e.code()}", e)
            listOf()
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.w("MathpixApiRecognizer", "Network timeout", e)
            listOf()
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w("MathpixApiRecognizer", "No network", e)
            listOf()
        } catch (e: Exception) {
            android.util.Log.e("MathpixApiRecognizer", "Unexpected error", e)
            listOf()
        }
    }

    /**
     * 测试连接：发送一次微小笔画请求，失败时**抛出**异常（与 [recognize] 吞异常的策略相反）。
     *
     * 失败模式与抛出的异常：
     * - Key 缺失 → [IllegalStateException]
     * - HTTP 401/403/500 → [retrofit2.HttpException]
     * - 网络超时 → [java.net.SocketTimeoutException]
     * - 无网络 → [java.net.UnknownHostException]
     * - 服务端响应 `error` 非空（业务拒绝）→ [IllegalStateException]
     *
     * 成功条件：HTTP 2xx + `error` 字段为空，仅此一种状态判为通过。
     */
    override suspend fun testConnection() {
        if (appId.isBlank() || appKey.isBlank()) {
            throw IllegalStateException("App ID 或 App Key 未配置")
        }
        // 微小测试笔画（3 个点的曲线），仅用于验证请求链路
        val testStrokes = listOf(listOf(0f to 0f, 50f to 50f, 100f to 0f))
        val bitmap = StrokeBitmapRenderer.render(testStrokes)
        val base64 = bitmapToBase64(bitmap)
        val request = MathpixRequest(
            src = "data:image/png;base64,$base64",
            formats = listOf("latex_styled")
        )
        val response = service.recognize(appId, appKey, request)
        if (!response.error.isNullOrBlank()) {
            throw IllegalStateException(
                "Mathpix 服务拒绝请求：${response.error} (request_id=${response.requestId})"
            )
        }
        // success: HTTP 2xx + 无 error → 鉴权通过
    }

    // ── 私有辅助函数 ──────────────────────────────────────────────────────

    /**
     * Bitmap → Base64 PNG 编码（用于 data URL）
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ── Retrofit 接口 ────────────────────────────────────────────────────

    private interface MathpixService {
        @POST("v3/text")
        suspend fun recognize(
            @Header("app_id") appId: String,
            @Header("app_key") appKey: String,
            @Body request: MathpixRequest
        ): MathpixResponse
    }

    // ── 数据类（internal: 暴露给单元测试验证 JSON 契约） ───────────────

    internal data class MathpixRequest(
        val src: String,
        val formats: List<String> = listOf("latex_styled")
    )

    /**
     * Mathpix v3/text 响应（仅保留我们关心的字段）。
     * 完整字段见 https://docs.mathpix.com/reference/post-v3-text
     */
    internal data class MathpixResponse(
        @SerializedName("latex_styled")
        val latexStyled: String? = null,
        @SerializedName("text")
        val text: String? = null,
        @SerializedName("confidence")
        val confidence: Double? = null,
        @SerializedName("error")
        val error: String? = null,
        @SerializedName("request_id")
        val requestId: String? = null
    )

    companion object Parser {
        /** OCR 推荐的最小图像宽度（px），低于此值会上采样 */
        private const val TARGET_MIN_WIDTH_PX = 800f

        /** 笔画 bitmap 的内边距（原坐标系，缩放前） */
        private const val PADDING_PX = 20f

        /** 基础笔画粗细（原坐标系，缩放前） */
        private const val STROKE_WIDTH_PX = 3f

        /**
         * 从 Mathpix 响应抽取 LaTeX 候选列表（**纯函数**，无副作用，便于单元测试）。
         *
         * 规则：
         * - `error` 非空 → 服务端业务错误，返回空列表（日志由调用方记录）
         * - 否则取顶层 `latex_styled`（单公式纯 LaTeX）
         * - Mathpix 单次请求仅返回 1 个最优结果，不返回多候选
         */
        internal fun extractCandidates(response: MathpixResponse): List<String> {
            if (!response.error.isNullOrBlank()) return listOf()
            return listOfNotNull(response.latexStyled?.takeIf { it.isNotBlank() })
        }
    }
}
