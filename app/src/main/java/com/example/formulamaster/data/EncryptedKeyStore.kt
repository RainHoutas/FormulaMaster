package com.example.formulamaster.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Tink AEAD 加解密包装（AES256-GCM + Android Keystore 主密钥）。
 *
 * 用于 [RecognizerPreference] 持久化 API Key 时的加密。
 *
 * ## 安全模型
 * - **主密钥**：存于 Android Keystore（硬件安全模块支持的设备走 TEE/StrongBox）
 *   URI: `android-keystore://formula_master_master_key`，进程间不共享
 * - **数据密钥（keyset）**：Tink 自管理的 AES256-GCM 密钥，由主密钥加密后存于 SharedPreferences
 *   文件 `formula_master_keyset_prefs`，键 `formula_master_recognizer_keyset`
 * - **应用数据**：调用 [encrypt] 后得到 Base64 字符串，可写入 DataStore；
 *   反向调用 [decrypt] 还原明文
 *
 * ## 失败模式
 * - 主密钥被 Android 系统清除（极少数升级/恢复场景）→ [decrypt] 抛 GeneralSecurityException，
 *   被 catch 后返回空字符串 + Logcat 警告。调用方语义上等同于"Key 未配置"。
 * - 用户改变锁屏方式（Android 部分版本会触发 keystore 失效）→ 同上
 *
 * ## 替代品
 * 取代已弃用（2025-04）的 androidx.security:security-crypto EncryptedSharedPreferences。
 * Google 官方推荐路径：DataStore + Tink + Android Keystore。
 */
class EncryptedKeyStore(context: Context) {

    private val aead: Aead by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    /**
     * 加密明文为 Base64（可直接存入 DataStore）。
     * 空字符串原样返回（避免对未配置的 Key 字段做无意义加密）。
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val cipher = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), null)
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    /**
     * 解密 Base64 密文为明文。
     * 空串原样返回；解密失败（密钥失效 / 数据损坏）返回空字符串 + Logcat 警告，
     * 调用方应据此进入"Key 未配置"分支。
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        return try {
            val cipher = Base64.decode(ciphertext, Base64.NO_WRAP)
            String(aead.decrypt(cipher, null), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed; treating as unconfigured", e)
            ""
        }
    }

    companion object {
        private const val TAG = "EncryptedKeyStore"
        private const val KEYSET_NAME = "formula_master_recognizer_keyset"
        private const val KEYSET_PREF_FILE = "formula_master_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://formula_master_master_key"

        init {
            // 进程级一次性注册 AEAD primitives（重复调用幂等）
            AeadConfig.register()
        }
    }
}
