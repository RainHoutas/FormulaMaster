package com.example.formulamaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.formulamaster.domain.RecognizerSettings
import com.example.formulamaster.domain.RecognizerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 识别器偏好持久化（DataStore Preferences + Tink 加密）。
 *
 * ## 字段加密策略
 * - **加密字段**（敏感）：mathpixAppId / mathpixAppKey / simpleTexToken
 *   均通过 [EncryptedKeyStore] 加密为 Base64 后存入 DataStore，明文绝不落盘
 * - **明文字段**（非敏感）：lightRecognizerId / deepRecognizerId 仅是枚举名，无安全风险
 *
 * ## API
 * - [settings]：process 级 hot [StateFlow]（Sprint 2 Task 2.1 修复 H 升级），
 *   所有 ViewModel / Composable 共享同一实例，避免每次订阅都重做 DataStore 读盘 + Tink 解密
 * - 写入方法均为 suspend，DataStore 协程内串行原子写
 *
 * ## Sprint 2 Task 2.1 修复 H（2026-04-30）
 * 此前 `settings` 是 cold [Flow]，每个 ViewModel 用 `stateIn` 转 hot 时都要重新订阅
 * 上游 `dataStore.data` 一次。即便 [AppContainer] 已让 [RecognizerPreference] 单例化，
 * 每次 ViewModel 重建仍要：
 * 1. 从 DataStore 读最新值（IO 读盘）
 * 2. 三次 `keyStore.decrypt(...)`（Tink AEAD + AndroidKeystore RPC）
 *
 * 即每次进 SettingsScreen / TestScreen 都有 50–150ms 的"加载感"。
 *
 * 现在 settings 是 process 级 hot StateFlow（[SharingStarted.Eagerly] + applicationScope），
 * App 启动后只需走一次 DataStore + Tink 链路，之后所有访问拿到的是已缓存的 [StateFlow.value]。
 *
 * ## 验收
 * - 重启 App 后所有字段持久化
 * - 用 DDMS 文件浏览查看 `datastore/recognizer_prefs.preferences_pb`，敏感 Key 字段为 Base64 密文
 *
 * ## 测试
 * 本类强依赖 Android Context + Keystore，单元测试需 Robolectric 或 androidTest。
 * [com.example.formulamaster.domain.RecognizerRegistry] 的纯函数路径已有 JVM 单测覆盖。
 *
 * @param applicationScope 用于 [stateIn] 的 hot StateFlow 持续运行；通常由 [AppContainer.applicationScope] 注入
 */
class RecognizerPreference(
    private val context: Context,
    applicationScope: CoroutineScope
) {

    private val keyStore = EncryptedKeyStore(context)
    private val dataStore: DataStore<Preferences> = context.recognizerDataStore

    /**
     * process 级 hot 偏好快照 [StateFlow]。
     *
     * - DataStore 写入后自动 emit 新快照
     * - 所有 ViewModel / Composable 共享同一实例
     * - 第一次访问触发 IO 读盘 + Tink 解密（建议在 [com.example.formulamaster.MainActivity] 冷启动时预热）
     * - 之后立即返回缓存值，无延迟
     */
    val settings: StateFlow<RecognizerSettings> = dataStore.data
        .map { prefs ->
            RecognizerSettings(
                lightRecognizerId = prefs[KEY_LIGHT_RECOGNIZER]?.toRecognizerTypeOrNull(),
                deepRecognizerId = prefs[KEY_DEEP_RECOGNIZER]?.toRecognizerTypeOrNull(),
                mathpixAppId = keyStore.decrypt(prefs[KEY_MATHPIX_APP_ID].orEmpty()),
                mathpixAppKey = keyStore.decrypt(prefs[KEY_MATHPIX_APP_KEY].orEmpty()),
                simpleTexToken = keyStore.decrypt(prefs[KEY_SIMPLETEX_TOKEN].orEmpty()),
            )
        }
        // map 内部 Tink decrypt 是 CPU + JNI 操作，显式跑在 IO 避免污染 Main
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = RecognizerSettings()
        )

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
