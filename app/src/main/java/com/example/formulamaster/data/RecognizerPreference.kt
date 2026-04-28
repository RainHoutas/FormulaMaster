package com.example.formulamaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.formulamaster.domain.RecognizerSettings
import com.example.formulamaster.domain.RecognizerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 识别器偏好持久化（DataStore Preferences + Tink 加密）。
 *
 * ## 字段加密策略
 * - **加密字段**（敏感）：mathpixAppId / mathpixAppKey / simpleTexToken
 *   均通过 [EncryptedKeyStore] 加密为 Base64 后存入 DataStore，明文绝不落盘
 * - **明文字段**（非敏感）：lightRecognizerId / deepRecognizerId 仅是枚举名，无安全风险
 *
 * ## API
 * - [settings]：响应式 [Flow]，DataStore 写入后自动 emit 新快照
 * - 写入方法均为 suspend，DataStore 协程内串行原子写
 *
 * ## 验收
 * - 重启 App 后所有字段持久化
 * - 用 DDMS 文件浏览查看 `datastore/recognizer_prefs.preferences_pb`，敏感 Key 字段为 Base64 密文
 *
 * ## 测试
 * 本类强依赖 Android Context + Keystore，单元测试需 Robolectric 或 androidTest。
 * [com.example.formulamaster.domain.RecognizerRegistry] 的纯函数路径已有 JVM 单测覆盖。
 */
class RecognizerPreference(private val context: Context) {

    private val keyStore = EncryptedKeyStore(context)
    private val dataStore: DataStore<Preferences> = context.recognizerDataStore

    /** 响应式偏好快照流。DataStore 任意写入后立刻 emit。 */
    val settings: Flow<RecognizerSettings> = dataStore.data.map { prefs ->
        RecognizerSettings(
            lightRecognizerId = prefs[KEY_LIGHT_RECOGNIZER]?.toRecognizerTypeOrNull(),
            deepRecognizerId = prefs[KEY_DEEP_RECOGNIZER]?.toRecognizerTypeOrNull(),
            mathpixAppId = keyStore.decrypt(prefs[KEY_MATHPIX_APP_ID].orEmpty()),
            mathpixAppKey = keyStore.decrypt(prefs[KEY_MATHPIX_APP_KEY].orEmpty()),
            simpleTexToken = keyStore.decrypt(prefs[KEY_SIMPLETEX_TOKEN].orEmpty()),
        )
    }

    /** 设置 Light 档绑定。null = 解除绑定。 */
    suspend fun setLightRecognizer(type: RecognizerType?) {
        dataStore.edit { prefs ->
            if (type == null) prefs.remove(KEY_LIGHT_RECOGNIZER)
            else prefs[KEY_LIGHT_RECOGNIZER] = type.name
        }
    }

    /** 设置 Deep 档绑定。null = 解除绑定。 */
    suspend fun setDeepRecognizer(type: RecognizerType?) {
        dataStore.edit { prefs ->
            if (type == null) prefs.remove(KEY_DEEP_RECOGNIZER)
            else prefs[KEY_DEEP_RECOGNIZER] = type.name
        }
    }

    /** 一次写入 Mathpix 双 Key（appId + appKey 一起改，避免半状态）。 */
    suspend fun setMathpixCredentials(appId: String, appKey: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MATHPIX_APP_ID] = keyStore.encrypt(appId)
            prefs[KEY_MATHPIX_APP_KEY] = keyStore.encrypt(appKey)
        }
    }

    /** 写入 SimpleTex token。 */
    suspend fun setSimpleTexToken(token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SIMPLETEX_TOKEN] = keyStore.encrypt(token)
        }
    }

    /** 清除所有配置（设置页"重置"按钮用）。 */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun String.toRecognizerTypeOrNull(): RecognizerType? = try {
        // 历史枚举值迁移：旧 "A2_SimpleTex" 单类型已拆为 Standard/Turbo 双类型，
        // 用户既有绑定按"标准模型"恢复（可在设置页随时改为 Turbo）
        when (this) {
            "A2_SimpleTex" -> RecognizerType.A2_SimpleTex_Standard
            else -> RecognizerType.valueOf(this)
        }
    } catch (e: IllegalArgumentException) {
        // 未来版本枚举值兼容：未知值视为未绑定
        null
    }

    companion object {
        private const val DATASTORE_NAME = "recognizer_prefs"

        private val KEY_LIGHT_RECOGNIZER = stringPreferencesKey("light_recognizer_id")
        private val KEY_DEEP_RECOGNIZER = stringPreferencesKey("deep_recognizer_id")
        private val KEY_MATHPIX_APP_ID = stringPreferencesKey("mathpix_app_id_enc")
        private val KEY_MATHPIX_APP_KEY = stringPreferencesKey("mathpix_app_key_enc")
        private val KEY_SIMPLETEX_TOKEN = stringPreferencesKey("simpletex_token_enc")

        // DataStore 必须是单例（同一文件多次创建会抛异常），用扩展属性绑定到 Context
        private val Context.recognizerDataStore: DataStore<Preferences>
                by preferencesDataStore(name = DATASTORE_NAME)
    }
}
