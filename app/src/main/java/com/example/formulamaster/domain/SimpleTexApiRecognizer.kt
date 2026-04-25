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
 * Task 1.5 — SimpleTex 公式识别 API 识别器
 *
 * 国内云端识别方案，相比 A1 Mathpix：
 * - 国内直连，无需代理
 * - 个人用户免费 1000 次/天
 * - 单 Token 鉴权（vs Mathpix 双 Key）
 *
 * ## 认证
 * - **UAT (User Access Token)**：用户从 https://simpletex.net/user/center 申请
 * - Header 中以 `token` 字段传递（**无 Bearer 前缀**）
 *
 * ## API 契约（按 https://doc.simpletex.cn/zh/api/api_formula_recognition.html）
 * - 端点：POST `https://server.simpletex.net/api/latex_ocr`（标准模型，准确率优先）
 *   - 备选 `latex_ocr_turbo`（轻量模型，速度优先）—— Sprint 1 不接，Sprint 后期看需求
 * - 请求：**multipart/form-data**，字段名 `file`，PNG 二进制
 * - 响应（顶层）：
 *   - `status: Boolean`（true 成功 / false 失败）
 *   - `res: { latex: String, conf: Double }`（成功时主输出在 `res.latex`，**注意是嵌套对象**）
 *   - `request_id: String`
 *   - `message: String?`（失败时的错误说明）
 *
 * ## 输入处理
 * - `BitmapInput`：直接编码上传
 * - `StrokeInput`：通过 [StrokeBitmapRenderer] 渲染为白底黑线 PNG（≥800px 宽，OCR 优化）
 *
 * ## Light/Deep 档位
 * 本类不感知档位差异——由 [Task 1.6 RecognizerRegistry] 决定哪个识别器实例分配到哪个槽位。
 *
 * ## 错误兜底
 * 任何异常（HTTP / 超时 / 无网络 / 解析失败）均返回空列表并 Logcat 记录原因，绝不向上抛出。
 */
class SimpleTexApiRecognizer(
    private val token: String
) : MathOcrRecognizer {

    private val service: SimpleTexService by lazy {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://server.simpletex.net/")
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
                val response = service.recognize(token, part)

                if (!response.status) {
                    android.util.Log.w(
                        "SimpleTexApiRecognizer",
                        "SimpleTex returned status=false: ${response.message} (request_id=${response.requestId})"
                    )
                }
                extractCandidates(response)
            }
        } catch (e: retrofit2.HttpException) {
            android.util.Log.w("SimpleTexApiRecognizer", "HTTP Error: ${e.code()}", e)
            listOf()
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.w("SimpleTexApiRecognizer", "Network timeout", e)
            listOf()
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.w("SimpleTexApiRecognizer", "No network", e)
            listOf()
        } catch (e: Exception) {
            android.util.Log.e("SimpleTexApiRecognizer", "Unexpected error", e)
            listOf()
        }
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
        @Multipart
        @POST("api/latex_ocr")
        suspend fun recognize(
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
