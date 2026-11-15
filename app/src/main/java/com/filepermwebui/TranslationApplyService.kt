package com.lcpatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * User-initiated native overlay, independent of MainActivity's lifecycle.
 * No game-exit watcher or automatic restore is installed.
 */
class TranslationApplyService : Service() {
    companion object {
        private const val ACTION_APPLY = "com.lcpatch.APPLY_NATIVE_OVERLAY"
        private const val EXTRA_PACK_PATH = "pack_path"
        private const val CHANNEL_ID = "lcpatch_translation_apply"
        private const val PROGRESS_ID = 12401
        private const val RESULT_ID = 12402

        fun start(context: Context, packPath: String) {
            val intent = Intent(context, TranslationApplyService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_PACK_PATH, packPath)
            }
            context.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private var running = false
    private var lastUpdate = 0L
    private var completed = 0
    private var total = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "漢化套用進度", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun baseNotification(title: String, detail: String): Notification.Builder =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)

    private fun progressNotification(done: Int, count: Int): Notification {
        val text = if (count > 0) "正在寫入日語／韓語資源 · $done / $count 個檔案"
            else "正在準備與備份漢化文件"
        return baseNotification("正在套用漢化", text)
            .setOngoing(true)
            .setProgress(count, done, count <= 0)
            .build()
    }

    @Synchronized private fun reportProgress(done: Int, count: Int) {
        completed = done
        total = count
        val now = SystemClock.elapsedRealtime()
        if (done < count && now - lastUpdate < 250L) return
        lastUpdate = now
        manager.notify(PROGRESS_ID, progressNotification(done, count))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packPath = intent?.getStringExtra(EXTRA_PACK_PATH)
        if (intent?.action != ACTION_APPLY || packPath.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        manager.cancel(RESULT_ID)
        val notification = progressNotification(0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PROGRESS_ID, notification)
        }

        scope.launch {
            var resultTitle = "漢化套用完成"
            var resultDetail = ""
            try {
                val repository = TranslationRepository(this@TranslationApplyService)
                check(!repository.rootMode() && repository.nativeBackend() == "shizuku") {
                    "目前不是 Shizuku 無 Root 模式"
                }
                val entry = repository.downloaded().firstOrNull { it.path == packPath }
                    ?: error("找不到指定的已下載漢化包")
                repository.apply(entry, nativeProgress = { done, count ->
                    reportProgress(done, count)
                })
                resultDetail = "已寫入 $completed / $total 個 JSON；可以切回原本的遊戲"
                LogRepository.append(this@TranslationApplyService, "INFO",
                    "translation.background_applied", resultDetail)
            } catch (error: CancellationException) {
                resultTitle = "漢化套用中斷"
                resultDetail = "工作已中斷，請回 LCPatch 查看備份狀態"
            } catch (error: Throwable) {
                resultTitle = "漢化套用失敗"
                resultDetail = error.message ?: error.javaClass.simpleName
                LogRepository.append(this@TranslationApplyService, "ERROR",
                    "translation.background_apply_failed", resultDetail)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                manager.notify(RESULT_ID, baseNotification(resultTitle, resultDetail)
                    .setAutoCancel(true)
                    .setStyle(Notification.BigTextStyle().bigText(resultDetail))
                    .build())
                running = false
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
