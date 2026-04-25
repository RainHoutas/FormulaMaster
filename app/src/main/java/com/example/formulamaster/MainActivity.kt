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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.formulamaster.data.local.AppDatabase
import com.example.formulamaster.data.repository.FormulaRepository
import com.example.formulamaster.data.worker.DailyReminderWorker
import com.example.formulamaster.domain.SprintModeManager
import com.example.formulamaster.ui.component.WebViewPool
import com.example.formulamaster.ui.screen.MainScreen
import com.example.formulamaster.ui.theme.FormulaMasterTheme
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

        val db = AppDatabase.getInstance(applicationContext)
        val repository = FormulaRepository(applicationContext, db.formulaDao())
        lifecycleScope.launch {
            // 1. 首次启动：预加载公式数据
            repository.seedIfEmpty()
            // 2. 检查是否进入考前冲刺模式（满足条件才写库）
            SprintModeManager.applyIfNeeded(db.studyStateDao())
        }

        // Task 6.1-a：请求通知权限（Android 13+）
        requestNotificationPermissionIfNeeded()

        // Task 6.1-b：调度每日 8:00 提醒（KEEP 策略，幂等）
        DailyReminderWorker.schedule(applicationContext)

        // Task 6.2：预热 WebView 复用池（容量 3，提前建好 1 个消除首帧白屏）
        // 放在主线程空闲时执行：Window 完成首次布局后才会触发 post，
        // 不阻塞 setContent 的渲染流水线。
        window.decorView.post { WebViewPool.warmUp(applicationContext, count = 1) }

        // Task 6.1-c：读取通知携带的导航目标（冷启动场景）
        navTarget = intent?.getStringExtra(DailyReminderWorker.EXTRA_START_TAB)

        setContent {
            FormulaMasterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(navTarget = navTarget)
                }
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
