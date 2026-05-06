package com.example.formulamaster.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.AppPreference
import com.example.formulamaster.data.RecognizerPreference
import com.example.formulamaster.domain.InputMode
import kotlinx.coroutines.launch

/**
 * Sprint 2 Task 2.5 — Onboarding 引导专用 ViewModel。
 *
 * - 用户在引导各步选择的值由 Composable 自身的 state 持有
 * - 提交时调用 [completeAndPersist]：把可选值落库 + 标记引导完成
 * - 跳过时调用 [skip]：只标记引导完成（已选过的值不写入）
 *
 * 不持有持久化的 settings 流；那些由 SettingsScreen / 其他业务消费方负责。
 */
class OnboardingViewModel(
    private val appPreference: AppPreference,
    private val recognizerPreference: RecognizerPreference
) : ViewModel() {

    /**
     * 完成引导：批量写入用户在向导中输入的所有非默认值（非空才写）。
     *
     * Sprint 3 Task 3.3：新增 [inputMode] 参数——用户在"输入方式"页的选择。
     * 非 null 时写入 [AppPreference]（即使是默认值 Handwriting 也写，确保持久化状态明确）。
     */
    fun completeAndPersist(
        targetExamDate: Long?,
        dailyRefreshHour: Int?,
        dailyRefreshMinute: Int?,
        simpleTexToken: String?,
        inputMode: InputMode?
    ) {
        viewModelScope.launch {
            if (targetExamDate != null && targetExamDate > 0L) {
                appPreference.setTargetExamDate(targetExamDate)
            }
            if (dailyRefreshHour != null || dailyRefreshMinute != null) {
                val h = (dailyRefreshHour ?: 8).coerceIn(0, 23)
                val m = (dailyRefreshMinute ?: 0).coerceIn(0, 59)
                appPreference.setDailyRefreshTime(h, m)
            }
            if (!simpleTexToken.isNullOrBlank()) {
                recognizerPreference.setSimpleTexToken(simpleTexToken.trim())
            }
            // Sprint 3 Task 3.3：持久化用户在引导中选择的输入方式
            if (inputMode != null) {
                appPreference.setInputMode(inputMode)
            }
            appPreference.setFirstLaunchCompletedAt(System.currentTimeMillis())
        }
    }

    /** 跳过引导：仅标记完成，不写入用户在前几步输入的值。 */
    fun skip() {
        viewModelScope.launch {
            appPreference.setFirstLaunchCompletedAt(System.currentTimeMillis())
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                return OnboardingViewModel(
                    appPreference = AppContainer.appPreference(app),
                    recognizerPreference = AppContainer.recognizerPreference(app)
                ) as T
            }
        }
    }
}
