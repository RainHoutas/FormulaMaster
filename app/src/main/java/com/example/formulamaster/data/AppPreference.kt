package com.example.formulamaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.formulamaster.domain.InputMode
import com.example.formulamaster.domain.KaoyanSubject
import com.example.formulamaster.domain.UseScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 应用全局偏好快照（非识别器敏感数据，无需加密）。
 *
 * - [dailyRefreshHourOfDay]：每日复习刷新整点（0-23），默认 8（即 08:00）
 * - [targetExamDate]：目标考试日期（Unix ms，本地时区当日 00:00）；
 *   存储 0 表示用户未设置，运行期通过 [effectiveTargetExamDate] 取得动态默认值。
 */
data class AppSettings(
    val dailyRefreshHourOfDay: Int = 8,
    /** 每日刷新时刻的分钟（0-59），默认 0。与 [dailyRefreshHourOfDay] 共同决定每日刷新点。 */
    val dailyRefreshMinuteOfHour: Int = 0,
    val targetExamDate: Long = 0L,
    /** 首次启动引导完成时间戳（Unix ms）。0 表示未完成 → 启动时弹 Onboarding。 */
    val firstLaunchCompletedAt: Long = 0L,
    /** 严测 / 默写时的输入方式。Sprint 3 Task 3.1 引入。 */
    val inputMode: InputMode = InputMode.Default,
    /** 学习流程重构 Sprint 1 Task 1.1 — 应用场景(默认考研数学)。 */
    val useScene: UseScene = UseScene.Default,
    /** 学习流程重构 Sprint 1 Task 1.1 — 考研数学子科目;仅当 [useScene] = [UseScene.KaoyanMath] 时生效。 */
    val kaoyanSubject: KaoyanSubject = KaoyanSubject.Default
) {
    /** 实际生效的考试日期：用户已设过则用持久化值，否则取动态默认（当前年份 12-20）。 */
    val effectiveTargetExamDate: Long
        get() = if (targetExamDate > 0L) targetExamDate else defaultTargetExamDate()

    /** 用户是否已显式设置过考试日期（区别于使用默认值）。 */
    val hasUserSetExamDate: Boolean get() = targetExamDate > 0L

    /** 是否完成过首次启动引导。 */
    val hasCompletedOnboarding: Boolean get() = firstLaunchCompletedAt > 0L

    companion object {
        /**
         * 默认考试日期：当前年份"12 月倒数第二个周六"的 00:00（本地时区）。
         *
         * 启发式来源：考研日期基本落在 12 月倒数第二个周末（用户拍板，2026-05-01）。
         * 近 3 年（2023/2024/2025 年研考）符合，更早年份偶有落到"最后一个周末"，
         * 但作为首次启动默认值已经足够，用户可在 Onboarding / 设置页随时改。
         *
         * 算法：
         *   1. 12-31 当日往回找 ≤ 31 号的最后一个周六 → "最后一个周六"
         *   2. 减 7 天 → 倒数第二个周六
         *
         * 跨年自动滚动（每次访问按当前年份重算）。
         */
        fun defaultTargetExamDate(zoneId: ZoneId = ZoneId.systemDefault()): Long {
            val year = LocalDate.now(zoneId).year
            val lastSatOfDec = LocalDate.of(year, 12, 31)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            val penultimateSat = lastSatOfDec.minusDays(7)
            return penultimateSat.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
    }
}

/**
 * 应用全局偏好持久化（DataStore Preferences-backed）。
 *
 * 与 [RecognizerPreference] 共用 [AppContainer.applicationScope]，
 * 以便 [settings] 成为 process 级 hot [StateFlow]，避免重入订阅开销。
 */
class AppPreference(
    context: Context,
    applicationScope: CoroutineScope
) {
    private val dataStore: DataStore<Preferences> = context.appDataStore

    private val _isLoaded = MutableStateFlow(false)

    /**
     * 首次 DataStore 真实读取完成的信号。
     *
     * StateFlow.initialValue = AppSettings() 与"未配置但已读取"的合法值一致，
     * 无法区分"还在初始默认"和"已读到默认"。Onboarding 决策必须等真实读取完成才能判断
     * `firstLaunchCompletedAt`，否则冷启动一闪先弹 Onboarding 再切到 MainScreen。
     */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val settings: StateFlow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                dailyRefreshHourOfDay = prefs[KEY_REFRESH_HOUR] ?: 8,
                dailyRefreshMinuteOfHour = prefs[KEY_REFRESH_MINUTE] ?: 0,
                targetExamDate = prefs[KEY_TARGET_EXAM_DATE] ?: 0L,
                firstLaunchCompletedAt = prefs[KEY_FIRST_LAUNCH_COMPLETED_AT] ?: 0L,
                inputMode = prefs[KEY_INPUT_MODE]?.toInputModeOrDefault() ?: InputMode.Default,
                useScene = prefs[KEY_USE_SCENE]?.toUseSceneOrDefault() ?: UseScene.Default,
                kaoyanSubject = KaoyanSubject.fromName(prefs[KEY_KAOYAN_SUBJECT])
            )
        }
        .onEach { _isLoaded.value = true }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    /**
     * 一次写入小时和分钟（原子写，避免分钟先写、小时后写的中间状态）。
     */
    suspend fun setDailyRefreshTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "hourOfDay 必须在 0..23，实际值：$hour" }
        require(minute in 0..59) { "minute 必须在 0..59，实际值：$minute" }
        dataStore.edit {
            it[KEY_REFRESH_HOUR] = hour
            it[KEY_REFRESH_MINUTE] = minute
        }
    }

    suspend fun setTargetExamDate(dateMs: Long) {
        dataStore.edit { it[KEY_TARGET_EXAM_DATE] = dateMs }
    }

    /** 写入引导完成时间戳；传 0L 等于"重置 Onboarding"（调试用）。 */
    suspend fun setFirstLaunchCompletedAt(timeMs: Long) {
        dataStore.edit { it[KEY_FIRST_LAUNCH_COMPLETED_AT] = timeMs }
    }

    /** Sprint 3 Task 3.1：写入用户的输入方式偏好。 */
    suspend fun setInputMode(mode: InputMode) {
        dataStore.edit { it[KEY_INPUT_MODE] = mode.name }
    }

    /** 学习流程重构 Sprint 1 Task 1.1：写入应用场景。 */
    suspend fun setUseScene(scene: UseScene) {
        dataStore.edit { it[KEY_USE_SCENE] = scene.name }
    }

    /** 学习流程重构 Sprint 1 Task 1.1：写入考研数学子科目。 */
    suspend fun setKaoyanSubject(subject: KaoyanSubject) {
        dataStore.edit { it[KEY_KAOYAN_SUBJECT] = subject.name }
    }

    /** 解析 DataStore 存储的字符串到枚举；未知值（旧版本字段被删/改名）按默认处理。 */
    private fun String.toInputModeOrDefault(): InputMode = try {
        InputMode.valueOf(this)
    } catch (e: IllegalArgumentException) {
        InputMode.Default
    }

    private fun String.toUseSceneOrDefault(): UseScene = try {
        UseScene.valueOf(this)
    } catch (e: IllegalArgumentException) {
        UseScene.Default
    }

    companion object {
        private const val DATASTORE_NAME = "app_prefs"
        private val KEY_REFRESH_HOUR              = intPreferencesKey("daily_refresh_hour_of_day")
        private val KEY_REFRESH_MINUTE            = intPreferencesKey("daily_refresh_minute_of_hour")
        private val KEY_TARGET_EXAM_DATE          = longPreferencesKey("target_exam_date")
        private val KEY_FIRST_LAUNCH_COMPLETED_AT = longPreferencesKey("first_launch_completed_at")
        private val KEY_INPUT_MODE                = stringPreferencesKey("input_mode")
        private val KEY_USE_SCENE                 = stringPreferencesKey("use_scene")
        private val KEY_KAOYAN_SUBJECT            = stringPreferencesKey("kaoyan_subject")

        private val Context.appDataStore: DataStore<Preferences>
            by preferencesDataStore(name = DATASTORE_NAME)
    }
}
