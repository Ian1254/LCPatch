package com.lcpatch

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object NativeStorageClient {
    fun status(): String = runCatching {
        when {
            !Shizuku.pingBinder() -> "未啟動"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "已授權"
            else -> "尚未授權"
        }
    }.getOrDefault("未啟動")

    suspend fun <T> use(context: Context, block: (INativeStorageService) -> T): T {
        check(status() == "已授權") { "請先啟動 Shizuku 並在 LCPatch 授予權限" }
        val args = Shizuku.UserServiceArgs(ComponentName(context, NativeStorageService::class.java))
            .daemon(false).processNameSuffix("native-storage").debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE).tag("lcpatch-native-storage")
        var connection: ServiceConnection? = null
        try {
            val service = withTimeout(15_000L) {
                suspendCancellableCoroutine<INativeStorageService> { continuation ->
                    val callback = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (!continuation.isActive) return
                            if (binder == null || !binder.pingBinder()) continuation.resumeWithException(IllegalStateException("Shizuku 文件服務無法連接"))
                            else continuation.resume(INativeStorageService.Stub.asInterface(binder))
                        }
                        override fun onServiceDisconnected(name: ComponentName?) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Shizuku 文件服務已中斷"))
                        }
                    }
                    connection = callback
                    Shizuku.bindUserService(args, callback)
                }
            }
            return withContext(Dispatchers.IO) { block(service) }
        } finally {
            connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
        }
    }
}
