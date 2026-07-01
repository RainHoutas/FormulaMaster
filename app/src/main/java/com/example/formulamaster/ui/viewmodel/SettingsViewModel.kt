package com.example.formulamaster.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.AppSettings
import com.example.formulamaster.data.RecognizerPreference
import com.example.formulamaster.data.local.dao.OcrFeedbackDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.worker.DailyReminderWorker
import com.example.formulamaster.domain.ErrorDeletePolicy
import com.example.formulamaster.domain.InputMode
import com.example.formulamaster.domain.RecognizerErrorClassifier
import com.example.formulamaster.domain.RecognizerRegistry
import com.example.formulamaster.domain.RecognizerSettings
import com.example.formulamaster.domain.RecognizerType
import com.example.formulamaster.domain.ReviewScheduler
import java.time.ZoneId
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val preference: RecognizerPreference,
    private val appPreference: AppPreference,
    private val ocrFeedbackDao: OcrFeedbackDao,
    private val subCardStateDao: SubCardStateDao,
    private val appContext: Context
) : ViewModel() {

    /**
     * 当前持久化的识别器配置，DataStore 任意写入后立即 emit。
     *
     * Sprint 2 Task 2.1 修复 H：直接复用 [RecognizerPreference] 内部的 process 级 hot StateFlow，
     * 不再用 `stateIn(viewModelScope)` 包一层 —— 后者会让每个 SettingsViewModel 重建时都要
     * 重新订阅 cold dataStore.data → 重做 DataStore 读盘 + Tink 解密，造成"每次进设置页加载一小下"。
     */
    val settings: StateFlow<RecognizerSettings> = preference.settings

    /** 应用全局偏好（刷新时刻等），DataStore 写入后立即 emit。 */
    val appSettings: StateFlow<AppSettings> = appPreference.settings

    /**
     * Sprint 2 Task 2.4：设置考试目标日期。
     * 传入的 [dateMs] 应为本地时区目标日的 00:00（UI 层负责转换）。
     */
    fun setTargetExamDate(dateMs: Long) {
        viewModelScope.launch { appPreference.setTargetExamDate(dateMs) }
    }

    /** Sprint 2 Task 2.4：重置考试目标日期为默认值（写入 0L → 读取时取动态默认）。 */
    fun resetTargetExamDate() {
        viewModelScope.launch { appPreference.setTargetExamDate(0L) }
    }

    /** Sprint 2 Task 2.5：重置 Onboarding（写 0L），下次启动会重新弹引导。调试用。 */
    fun resetOnboarding() {
        viewModelScope.launch { appPreference.setFirstLaunchCompletedAt(0L) }
    }

    /** Sprint 3 Task 3.1：设置输入方式（手写识别 / 纸笔自评）。 */
    fun setInputMode(mode: InputMode) {
        viewModelScope.launch { appPreference.setInputMode(mode) }
    }

    /** Sprint 3 Task 3.3：设置删除错题时对复习计划的处理策略。 */
    fun setErrorDeletePolicy(policy: ErrorDeletePolicy) {
        viewModelScope.launch { appPreference.setErrorDeletePolicy(policy) }
    }

    fun setDailyRefreshTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            appPreference.setDailyRefreshTime(hour, minute)
            // Sprint 2 Task 2.3：切换刷新时刻时，把库内既有 nextReviewTime 重新截断到新整点
            // 仅改时分秒，保留原日期（与用户期望"天数不变，只用该当日复习时间"一致）
            // Task 2.6（2026-05-29）：母卡退役，改作用于全部子卡
            val zone = ZoneId.systemDefault()
            val subCards = subCardStateDao.getAllStatesOnce()
            subCards.forEach { sub ->
                if (sub.nextReviewTime <= 0L) return@forEach
                val newTime = ReviewScheduler.truncateToRefreshHour(
                    sub.nextReviewTime, hour, minute, zone
                )
                if (newTime != sub.nextReviewTime) {
                    subCardStateDao.update(sub.copy(nextReviewTime = newTime))
                }
            }
            // 同步重排每日复习提醒到新刷新时刻（UPDATE 策略，幂等）
            DailyReminderWorker.schedule(appContext, hour, minute)
        }
    }

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
                TestConnectionStatus.Failed(RecognizerErrorClassifier.classify(e))
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

    // ── Sprint 1 Task 1.9：识别失败反馈样本管理 ──────────────────────────────

    /** 已收集的反馈样本数量，响应式刷新（DAO countFlow）。 */
    val feedbackCount: StateFlow<Int> = ocrFeedbackDao.countFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    /** 导出结果状态（一次性事件，用 String? 简化处理；显示后由 UI 调 [clearExportResult] 清掉）。 */
    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * 把全部反馈样本导出为 JSON 数组到用户通过 SAF 选择的目标 Uri。
     *
     * 失败原因分类（写入 [exportResult]）：
     * - 无样本：UI 提前禁用按钮，正常路径不会到这；若到了返回 NoSamples
     * - I/O 错误（用户撤销 / 存储已满 / 权限丢失）→ Failed
     */
    fun exportFeedbackJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = ocrFeedbackDao.getAll()
            if (list.isEmpty()) {
                _exportResult.value = ExportResult.NoSamples
                return@launch
            }
            try {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(list)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                        os.flush()
                    } ?: throw java.io.IOException("openOutputStream returned null")
                }
                _exportResult.value = ExportResult.Success(list.size)
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Failed(
                    RecognizerErrorClassifier.classify(e)
                )
            }
        }
    }

    /** 清空所有反馈样本（带二次确认由 UI 层负责）。 */
    fun clearFeedback() {
        viewModelScope.launch(Dispatchers.IO) {
            ocrFeedbackDao.clearAll()
        }
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                // Sprint 2 Task 2.1 修复 D：从 AppContainer 取单例，
                // 替代之前每次 `RecognizerPreference(app)` 新建实例的做法
                val db = AppContainer.appDatabase(app)
                return SettingsViewModel(
                    preference = AppContainer.recognizerPreference(app),
                    appPreference = AppContainer.appPreference(app),
                    ocrFeedbackDao = db.ocrFeedbackDao(),
                    subCardStateDao = db.subCardStateDao(),
                    appContext = app
                ) as T
            }
        }
    }
}

/**
 * Sprint 1 Task 1.9 — 反馈 JSON 导出结果。
 */
sealed class ExportResult {
    data object NoSamples : ExportResult()
    data class Success(val count: Int) : ExportResult()
    data class Failed(val reason: String) : ExportResult()
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
