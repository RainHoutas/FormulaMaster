package com.example.formulamaster.domain

import android.graphics.Bitmap
import com.example.formulamaster.domain.util.StrokeBitmapRenderer
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Task 1.5 + Task 1.5b — SimpleTex 公式识别 API 识别器
 *
 * 国内云端识别方案，相比 A1 Mathpix：
 * - 国内直连，无需代理
 * - 双端点真免费（标准模型 500/天 + 轻量模型 2000/天）
 * - 单 Token 鉴权（vs Mathpix 双 Key）
 *
 * ## 双端点（两个并行实例共享同一 token）
 * - [SimpleTexEndpoint.Standard] = `/api/latex_ocr`（500/天，准确率优先）
 * - [SimpleTexEndpoint.Turbo]    = `/api/latex_ocr_turbo`（2000/天，速度优先）
 *
 * 用户在设置页填一个 token，两个端点同时可用，可分别绑定到 Light / Deep 两档：
 * - 推荐：Light = Turbo（高频预览，2000/天烧得起）/ Deep = Standard（关键识别更准）
 *
 * ## 认证
 * - **UAT (User Access Token)**：用户从 https://simpletex.net/user/center 申请
 * - Header 中以 `token` 字段传递（**无 Bearer 前缀**）
 *
 * ## API 契约（按 https://doc.simpletex.cn/zh/api/api_formula_recognition.html，2025-03-31 版）
 * - 端点：POST `https://server.simpletex.cn/{endpoint.path}`
 * - 请求：**multipart/form-data**，字段名 `file`，PNG 二进制
 * - 响应（顶层）：
 *   - `status: Boolean`（true 成功 / false 失败）
 *   - `res: { latex: String, conf: Double }`（成功时主输出在 `res.latex`，**注意是嵌套对象**）
 *   - `request_id: String`
 *   - `message: String?`（失败时的错误说明）
 * - **QPS 限制**：Turbo 5 QPS / Standard 2 QPS（超过会被服务端拒绝，本类未做客户端节流）
 *
 * ## 输入处理
 * - `BitmapInput`：直接编码上传
 * - `StrokeInput`：通过 [StrokeBitmapRenderer] 渲染为白底黑线 PNG（≥800px 宽，OCR 优化）
 *
 * ## 错误兜底
 * 任何异常（HTTP / 超时 / 无网络 / 解析失败）均返回空列表并 Logcat 记录原因，绝不向上抛出。
 */
class SimpleTexApiRecognizer(
    private val token: String,
    private val endpoint: SimpleTexEndpoint = SimpleTexEndpoint.Standard
) : MathOcrRecognizer {

    private val service: SimpleTexService by lazy {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            // 官方文档（doc.simpletex.cn）明确域名为 .cn —— 之前误用 .net 已修正
            .baseUrl("https://server.simpletex.cn/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(SimpleTexService::class.java)
    }

    override suspend fun recognize(
        input: OcrInput,
        mode: RecognitionMode
    ): List<String> {
        return try {
            if (token.isBlank()) {
                listOf()  // Token 缺失，本地短路
            } else {
                val bitmap = when (input) {
                    is OcrInput.BitmapInput -> input.bitmap
                    is OcrInput.StrokeInput -> StrokeBitmapRenderer.render(input.strokes)
                }

                val part = bitmapToMultipart(bitmap)
                val response = service.recognize(endpoint.path, token, part)

                if (!response.status) {
                    android.util.Log.w(
                        logTag,
                        "SimpleTex returned status=false: ${response.message} (request_id=${response.requestId})"
                    )
                }
                extractCandidates(response)
            }
        } catch (e: retrofit2.HttpException) {
            android.util.Log.w(logTag, "HTTP Error: ${e.code()}", e)
            listOf()
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.w(logTag, "Network timeout", e)
            listOf()
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w(logTag, "No network", e)
            listOf()
        } catch (e: Exception) {
            android.util.Log.e(logTag, "Unexpected error", e)
            listOf()
        }
    }

    private val logTag: String get() = "SimpleTex.${endpoint.name}"

    /**
     * 测试连接：发送一次微小笔画请求，失败时**抛出**异常（与 [recognize] 吞异常的策略相反）。
     *
     * 失败模式与抛出的异常：
     * - Token 缺失 → [IllegalStateException]
     * - HTTP 401/403/500 → [retrofit2.HttpException]
     * - 网络超时 → [java.net.SocketTimeoutException]
     * - 无网络 → [java.net.UnknownHostException]
     * - 服务端 `status: false`（业务拒绝，例如 token 无效）→ [IllegalStateException]
     *
     * 成功条件：HTTP 2xx + 响应体 `status: true`，仅此一种状态判为通过。
     */
    override suspend fun testConnection() {
        if (token.isBlank()) {
            throw IllegalStateException("Token 未配置")
        }
        // 微小测试笔画（3 个点的曲线），仅用于验证请求链路
        val testStrokes = listOf(listOf(0f to 0f, 50f to 50f, 100f to 0f))
        val bitmap = StrokeBitmapRenderer.render(testStrokes)
        val part = bitmapToMultipart(bitmap)

        val response = service.recognize(endpoint.path, token, part)
        if (!response.status) {
            // HTTP 2xx 但服务端拒绝：通常是 token 不合法 / 业务规则失败
            throw IllegalStateException(
                "SimpleTex 服务拒绝请求：${response.message ?: "未知原因"} (request_id=${response.requestId})"
            )
        }
        // success: HTTP 2xx + status=true → 鉴权通过
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────

    /**
     * Bitmap → multipart/form-data Part（字段名固定 `file`）
     */
    private fun bitmapToMultipart(bitmap: Bitmap): MultipartBody.Part {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        val body = bytes.toRequestBody("image/png".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", "formula.png", body)
    }

    // ── Retrofit 接口 ────────────────────────────────────────────────────

    private interface SimpleTexService {
        /**
         * 动态端点 POST：通过 [retrofit2.http.Url] 让单个方法支持多个 path
         * （绝对路径下避免 baseUrl 拼接问题，path 由 [SimpleTexEndpoint.path] 提供）
         */
        @Multipart
        @POST
        suspend fun recognize(
            @retrofit2.http.Url path: String,
            @Header("token") token: String,
            @Part file: MultipartBody.Part
        ): SimpleTexResponse
    }

    // ── 数据类（internal: 暴露给单元测试验证 JSON 契约） ───────────────

    /**
     * SimpleTex /api/latex_ocr 响应。
     * 完整字段见 https://doc.simpletex.cn/zh/api/api_formula_recognition.html
     */
    internal data class SimpleTexResponse(
        @SerializedName("status")
        val status: Boolean = false,
        @SerializedName("res")
        val res: SimpleTexResult? = null,
        @SerializedName("request_id")
        val requestId: String? = null,
        @SerializedName("message")
        val message: String? = null
    )

    internal data class SimpleTexResult(
        @SerializedName("latex")
        val latex: String? = null,
        @SerializedName("conf")
        val conf: Double? = null
    )

    companion object Parser {
        /**
         * 从 SimpleTex 响应抽取 LaTeX 候选列表（**纯函数**，无副作用，便于单元测试）。
         *
         * 规则：
         * - `status: false` → 服务端业务错误，返回空列表（日志由调用方记录）
         * - 否则取 `res.latex`（嵌套对象，**注意不是顶层字段**）
         * - SimpleTex 单次请求仅返回 1 个最优结果，不返回多候选
         */
        internal fun extractCandidates(response: SimpleTexResponse): List<String> {
            if (!response.status) return listOf()
            return listOfNotNull(response.res?.latex?.takeIf { it.isNotBlank() })
        }
    }
}

/**
 * SimpleTex 端点选择。两个端点共享同一 UAT token，免费额度独立计算。
 *
 * 引用 https://doc.simpletex.cn/zh/api/api_formula_recognition.html ：
 * - [Standard]：完整版模型，识别准确率优先
 * - [Turbo]：轻量版模型，速度优先
 */
enum class SimpleTexEndpoint(val path: String, val displayName: String, val freeQuota: String) {
    Standard("api/latex_ocr",       "标准模型", "500/天"),
    Turbo   ("api/latex_ocr_turbo", "Turbo 模型", "2000/天"),
}
