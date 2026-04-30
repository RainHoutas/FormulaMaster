package com.example.formulamaster.data

import android.content.Context
import com.example.formulamaster.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Sprint 2 Task 2.1 修复 D — 进程级单例容器（轻量 service locator）。
 *
 * ## 解决的问题
 * 此前 [RecognizerPreference] 在多处独立实例化：
 * - `TestScreen` 内 `remember { RecognizerPreference(context.applicationContext) }`
 * - `SettingsViewModel.factory` 内 `RecognizerPreference(app)`
 * - 任意可能用到的新场景（Onboarding 等）
 *
 * 每次实例化都会持有独立的 [EncryptedKeyStore]，而 `EncryptedKeyStore.aead`
 * 是 **实例级 lazy** —— 每个新实例的首次解密都要重新走 Tink AEAD + AndroidKeystore
 * 初始化路径（DataStore 读盘 + Keystore RPC），耗时几十到上百 ms 不等。
 *
 * 进 TestScreen 时这条路径在主线程相关的 Composition / collect emission 链路上，
 * 用户体感为"切到测试页不跟手"。
 *
 * ## 设计
 * - 单例对象 + 双重检查锁的 `ensure(context)` 初始化方法
 * - 持有 [RecognizerPreference] 和 [AppDatabase] 引用，供 ViewModel 工厂 / Composable 共享
 * - 不引入 DI 框架（项目体量不需要）；不接触 Application class（避免改 AndroidManifest）
 * - 所有调用方传 [Context]（取 applicationContext），首次访问时 `ensure` 初始化
 *
 * ## 用法
 * ```kotlin
 * val pref = AppContainer.recognizerPreference(context)
 * val db   = AppContainer.appDatabase(context)
 * ```
 *
 * ## 线程安全
 * `ensure` 用 synchronized 双重检查；`recognizerPreference` / `appDatabase` 调用内联，
 * 不会重复进入临界区。
 */
object AppContainer {

    @Volatile private var recognizerPreference: RecognizerPreference? = null
    @Volatile private var appDatabase: AppDatabase? = null

    /**
     * 应用级 CoroutineScope。
     *
     * Sprint 2 Task 2.1 修复 H：用于把识别器偏好 Flow 提升为 process 级 hot StateFlow，
     * 避免每个 ViewModel 创建时重新订阅 cold flow（DataStore + Tink decrypt 重做一遍）。
     *
     * - SupervisorJob：单个子协程失败不连累其他
     * - Dispatchers.Main.immediate：collect StateFlow 时给 UI 线程立即拿值的机会，
     *   实际计算（DataStore 读盘 + Tink）由 RecognizerPreference 内部的 map 切到 IO
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 取识别器偏好单例。首次调用触发初始化（包括 [EncryptedKeyStore] 内 Tink 的 lazy aead 注册
     * 和 application-scope hot StateFlow 启动）。后续调用返回同一实例 + 同一 hot StateFlow。
     */
    fun recognizerPreference(context: Context): RecognizerPreference {
        return recognizerPreference ?: synchronized(this) {
            recognizerPreference ?: RecognizerPreference(
                context.applicationContext,
                applicationScope
            ).also { recognizerPreference = it }
        }
    }

    /**
     * 取数据库单例。底层 [AppDatabase.getInstance] 本身已是单例（双重检查锁），
     * 这里再包一层只是为了 service locator 风格统一。
     */
    fun appDatabase(context: Context): AppDatabase {
        return appDatabase ?: synchronized(this) {
            appDatabase ?: AppDatabase.getInstance(context.applicationContext)
                .also { appDatabase = it }
        }
    }
}
