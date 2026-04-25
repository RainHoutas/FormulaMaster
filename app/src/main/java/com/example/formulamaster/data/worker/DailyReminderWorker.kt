package com.example.formulamaster.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.formulamaster.MainActivity
import com.example.formulamaster.R
import com.example.formulamaster.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Task 6.1：每日复习提醒 Worker。
 *
 * - 每天早上 8:00 触发（首次调度时计算 initialDelay 对齐到下个 8:00）
 * - 查询今日待复习队列；若非空则发送系统通知
 * - 通知点击后打开 MainActivity 并跳转到复习 Tab
 * - 通知频道：review_reminder（IMPORTANCE_DEFAULT）
 *
 * 调度入口：[schedule]。在 MainActivity.onCreate 中调用一次即可；
 * WorkManager 以 [WORK_NAME] 做唯一任务保证，重复调用不会创建多个任务。
 */
class DailyReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(appContext)
        val nowMs = System.currentTimeMillis()

        // 取今日待复习队列第一次值（Flow → 一次性读取）
        val queue = db.studyStateDao().getTodayReviewQueue(nowMs).first()
        if (queue.isEmpty()) return Result.success()

        // 创建通知频道 + 发送通知
        ensureNotificationChannel()
        sendNotification(count = queue.size)
        return Result.success()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "每日复习提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "提醒你完成今日的公式复习任务"
            }
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(count: Int) {
        // 点击通知 → 打开 MainActivity 并携带"跳转到复习 Tab"的额外信息
        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(EXTRA_START_TAB, TAB_REVIEW)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (count == 1) {
            "有 1 道公式等待复习，保持节奏！"
        } else {
            "还有 $count 道公式等待复习，保持节奏！"
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("今日复习提醒")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "daily_review_reminder"
        const val EXTRA_START_TAB = "start_tab"
        const val TAB_REVIEW = "review"

        private const val CHANNEL_ID = "review_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 0

        /**
         * 在 MainActivity 中调用一次。
         * 以 [ExistingPeriodicWorkPolicy.KEEP] 保证不重复调度（不重置计时）。
         */
        fun schedule(context: Context) {
            val initialDelay = delayToNextEightAM()

            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** 距下一个 08:00:00 的毫秒数。若当前时间已过今天 8 点，则目标为明天 8 点。 */
        private fun delayToNextEightAM(): Long {
            val now = LocalDateTime.now()
            val targetToday = now.toLocalDate().atTime(8, 0)
            val target = if (targetToday.isAfter(now)) targetToday else targetToday.plusDays(1)
            val zone = ZoneId.systemDefault()
            return Duration.between(
                now.atZone(zone).toInstant(),
                target.atZone(zone).toInstant()
            ).toMillis().coerceAtLeast(0L)
        }
    }
}
