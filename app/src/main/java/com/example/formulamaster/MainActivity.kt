package com.example.formulamaster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.formulamaster.data.AppContainer
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.data.worker.DailyReminderWorker
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.ui.component.WebViewPool
import com.example.formulamaster.ui.screen.MainScreen
import com.example.formulamaster.ui.screen.OnboardingScreen
import com.example.formulamaster.ui.theme.FormulaMasterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Task 6.1：通知点击时传递的"打开复习 Tab"请求。
    // Compose observes mutableStateOf，onNewIntent 更新后 MainScreen 的 LaunchedEffect 自动响应。
    private var navTarget by mutableStateOf<String?>(null)

    // Task 6.1：POST_NOTIFICATIONS 运行时权限申请（Android 13+）
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // isGranted 为 true 时用户已授权；拒绝时静默——不强制，不阻断主流程
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sprint 2 Task 2.1 修复 D 配套：通过 AppContainer 取数据库单例，
        // 与 ViewModel 工厂共享同一实例
        val db = AppContainer.appDatabase(applicationContext)
        val repository = FormulaRepository(applicationContext, db.formulaDao())
        lifecycleScope.launch {
            // 1. 首次启动：预加载公式数据
            repository.seedIfEmpty()
            // 2. 检查是否进入考前冲刺模式（满足条件才写库）
            //    Sprint 2 Task 2.4：考试日期改读 AppPreference（用户可在设置页修改）
            val target = try {
                AppContainer.appPreference(applicationContext).settings.first().effectiveTargetExamDate
            } catch (_: Exception) {
                com.example.formulamaster.data.AppSettings.defaultTargetExamDate()
            }
            SprintModeManager.applyIfNeeded(db.studyStateDao(), target)
        }

        // Sprint 2 Task 2.1 后台冷启动预热：把"首次进 Tab 才付钱"的开销前置到 App 启动时
        // ── 重点是 RecognizerPreference 内部 Tink AEAD 的首次初始化（约 100ms 一次性）
        //    和 DataStore 第一次读盘。在 IO dispatcher 后台跑，不阻塞 setContent
        lifecycleScope.launch(Dispatchers.IO) {
            val pref = AppContainer.recognizerPreference(applicationContext)
            // 用 first() 消费一次 DataStore Flow（hot stream，collect 不会自然结束），
            // 这次访问会触发：DataStore 读盘 → Tink lazy aead 注册 → keyset 解密
            // 之后 SettingsScreen / TestScreen 进入时直接命中已就绪的实例
            try {
                pref.settings.first()
            } catch (_: Exception) {
                // 预热失败不影响主流程；真实使用路径有自己的错误兜底
            }
            // Sprint 2 Task 2.3：同步预热 AppPreference（每日刷新时刻 DataStore 读盘）
            val (hourOfDay, minute) = try {
                val s = AppContainer.appPreference(applicationContext).settings.first()
                s.dailyRefreshHourOfDay to s.dailyRefreshMinuteOfHour
            } catch (_: Exception) { 8 to 0 }
            // Task 6.1-b：调度每日复习提醒，按用户配置的刷新时刻（默认 08:00）
            // UPDATE 策略：重启时按最新时刻调整，幂等
            DailyReminderWorker.schedule(applicationContext, hourOfDay, minute)
        }

        // Task 6.1-a：请求通知权限（Android 13+）
        requestNotificationPermissionIfNeeded()

        // Task 6.2：预热 WebView 复用池（容量 3）
        // Sprint 2 Task 2.1 调整：从 1 提升到 2 —— 详情页同时挂载 2 个 MathFormulaView，
        // 1 个不够导致第一个进详情页时仍会即时新建第二个 WebView（logcat 已确认）
        window.decorView.post { WebViewPool.warmUp(applicationContext, count = 2) }

        // Task 6.1-c：读取通知携带的导航目标（冷启动场景）
        navTarget = intent?.getStringExtra(DailyReminderWorker.EXTRA_START_TAB)

        setContent {
            FormulaMasterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(navTarget = navTarget)
                }
            }
        }
    }

    /**
     * Sprint 2 Task 2.5：引导/主屏网关。
     *
     * - 等 [com.example.formulamaster.data.AppPreference.isLoaded] 为 true 才决策（避免初始默认 0L 误判）
     * - `firstLaunchCompletedAt == 0L` → 弹 [OnboardingScreen]，完成后切到 [MainScreen]
     * - 否则直接挂 [MainScreen]
     */
    @Composable
    private fun AppRoot(navTarget: String?) {
        val context = LocalContext.current
        val pref = remember(context) { AppContainer.appPreference(context) }
        val isLoaded by pref.isLoaded.collectAsState()
        val settings by pref.settings.collectAsState()

        // 内存态：完成 onCompleted 后立即切屏，不等 DataStore Flow 回灌（避免 1-2 帧空白）
        var onboardingCompleted by remember { mutableStateOf(false) }

        when {
            !isLoaded -> {
                // 极短的 splash（DataStore 第一次读盘 ≈ 几十 ms）
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !onboardingCompleted && !settings.hasCompletedOnboarding -> {
                OnboardingScreen(onCompleted = { onboardingCompleted = true })
            }
            else -> {
                MainScreen(navTarget = navTarget)
            }
        }
    }

    /**
     * Task 6.1：App 已在后台运行时收到通知点击（热启动 / onNewIntent 场景）。
     * 更新 navTarget → Compose 重组 → MainScreen.LaunchedEffect 触发导航。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navTarget = intent.getStringExtra(DailyReminderWorker.EXTRA_START_TAB)
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────────────

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // API < 33 无需运行时权限，Manifest 静态声明已够
    }
}
