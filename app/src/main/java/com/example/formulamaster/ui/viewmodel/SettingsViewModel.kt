package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.RecognizerPreference
import com.example.formulamaster.domain.RecognizerRegistry
import com.example.formulamaster.domain.RecognizerSettings
import com.example.formulamaster.domain.RecognizerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sprint 1 Task 1.7 — SettingsViewModel
 *
 * 桥接 [RecognizerPreference]（持久化层）和 SettingsScreen（UI 层）。
 * 暴露：
 * - [settings]：响应式的当前配置快照（DataStore 写入后即时刷新）
 * - [testStatus]：每个识别器的连接测试状态
 * - 一组 set / clear 方法（写入 DataStore，无需重启）
 * - [testConnection]：发起最小测试请求验证 Key 有效性
 */
class SettingsViewModel(
    private val preference: RecognizerPreference
) : ViewModel() {

    /** 当前持久化的识别器配置，DataStore 任意写入后立即 emit。 */
    val settings: StateFlow<RecognizerSettings> = preference.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = RecognizerSettings()
        )

    private val _testStatus = MutableStateFlow<Map<RecognizerType, TestConnectionStatus>>(emptyMap())
    val testStatus: StateFlow<Map<RecognizerType, TestConnectionStatus>> = _testStatus.asStateFlow()

    /**
     * 每个识别器类型最近一次测试连接的完成时间戳（毫秒）。
     * 用于实现客户端速率限制：连续两次测试至少间隔 [TEST_COOLDOWN_MS]，
     * 防止用户疯狂点击造成对服务端的不必要冲击 + 浪费免费额度。
     */
    private val lastTestAtMs = mutableMapOf<RecognizerType, Long>()

    /**
     * 任意识别器最近一次测试完成的时间戳信号。
     *
     * 给 UI 层的 LaunchedEffect 用作"什么时候启动倒计时 ticker"的 key —— 仅在新测试完成时
     * emit 新值，**状态条 4 秒自动消失不会影响这个 Flow**，避免之前 ticker 半路被取消导致
     * 按钮卡在过时倒计时的 bug。
     */
    private val _lastCompletionAtMs = MutableStateFlow(0L)
    val lastCompletionAtMs: StateFlow<Long> = _lastCompletionAtMs.asStateFlow()

    fun setLightRecognizer(type: RecognizerType?) {
        viewModelScope.launch { preference.setLightRecognizer(type) }
    }

    fun setDeepRecognizer(type: RecognizerType?) {
        viewModelScope.launch { preference.setDeepRecognizer(type) }
    }

    fun setMathpixCredentials(appId: String, appKey: String) {
        viewModelScope.launch { preference.setMathpixCredentials(appId, appKey) }
    }

    fun setSimpleTexToken(token: String) {
        viewModelScope.launch { preference.setSimpleTexToken(token) }
    }

    fun clearAll() {
        viewModelScope.launch { preference.clearAll() }
    }

    /**
     * 测试指定识别器的连接。
     *
     * 关键设计：
     * 1. 调用 [MathOcrRecognizer.testConnection]（**不吞异常**）
     *    不能用 [MathOcrRecognizer.recognize]（吞异常返回空列表 → 假阳性，曾踩坑）
     * 2. **客户端速率限制**：相同 type 连续两次测试至少间隔 [TEST_COOLDOWN_MS]，
     *    超频请求直接忽略（不下到网络）
     *
     * @param type 要测试的识别器类型
     */
    fun testConnection(type: RecognizerType) {
        // 客户端速率限制：拦截过快的重复请求
        val now = System.currentTimeMillis()
        val lastAt = lastTestAtMs[type] ?: 0L
        if (now - lastAt < TEST_COOLDOWN_MS) {
            // 静默拒绝（UI 层应该在冷却期间禁用按钮，正常路径下不会到这）
            return
        }

        val current = settings.value
        val recognizer = RecognizerRegistry.instantiate(type, current)
        if (recognizer == null) {
            _testStatus.value = _testStatus.value + (type to TestConnectionStatus.NotConfigured)
            return
        }

        _testStatus.value = _testStatus.value + (type to TestConnectionStatus.Testing)

        viewModelScope.launch {
            val status = try {
                recognizer.testConnection()
                // 未抛异常 = HTTP 2xx + 服务端业务通过 = Key 鉴权确认通过
                TestConnectionStatus.Success
            } catch (e: Exception) {
                TestConnectionStatus.Failed(reasonOf(e))
            }
            // 仅在请求实际完成后记录时间戳（用作下次冷却起点）
            val completedAt = System.currentTimeMillis()
            lastTestAtMs[type] = completedAt
            _testStatus.value = _testStatus.value + (type to status)
            // emit 信号驱动 UI 启动倒计时 ticker
            _lastCompletionAtMs.value = completedAt
        }
    }

    /**
     * 距离上次测试还需等待的秒数（向上取整，已超期则返回 0）。
     * 供 UI 显示倒计时。
     */
    fun cooldownSecondsRemaining(type: RecognizerType): Int {
        val lastAt = lastTestAtMs[type] ?: return 0
        val elapsed = System.currentTimeMillis() - lastAt
        val remaining = TEST_COOLDOWN_MS - elapsed
        return if (remaining <= 0) 0 else ((remaining + 999) / 1000).toInt()
    }

    fun clearTestStatus(type: RecognizerType) {
        _testStatus.value = _testStatus.value - type
    }

    private fun reasonOf(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "无网络连接"
        is java.net.SocketTimeoutException -> "网络超时"
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> "Key 无效或已过期"
            500, 502, 503 -> "服务暂时不可用"
            else -> "HTTP ${e.code()}"
        }
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        /**
         * 测试连接速率限制：相同识别器类型两次测试至少间隔 5 秒。
         *
         * 设计依据：
         * - SimpleTex Standard 服务端 QPS 限制是 2，Turbo 是 5；客户端 5 秒一次远低于服务端阈值
         * - 给用户足够时间看测试结果（成功/失败文案显示约 4 秒）
         * - 即使 UI 失效（比如未来变更没禁用按钮），ViewModel 这层兜底也能拦截大部分误用
         */
        const val TEST_COOLDOWN_MS = 5_000L

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(RecognizerPreference(context.applicationContext)) as T
        }
    }
}

/**
 * 单个识别器的连接测试状态。
 */
sealed class TestConnectionStatus {
    /** 配置不完整（缺 Key），测试前置失败 */
    data object NotConfigured : TestConnectionStatus()

    /** 测试中（loading） */
    data object Testing : TestConnectionStatus()

    /** 成功：Key 鉴权通过 */
    data object Success : TestConnectionStatus()

    /** 失败：附带原因文案 */
    data class Failed(val reason: String) : TestConnectionStatus()
}
