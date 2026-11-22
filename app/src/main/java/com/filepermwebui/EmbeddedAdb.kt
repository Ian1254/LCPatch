package com.lcpatch

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Only connects to this device; no external manager or exported command endpoint. */
internal object NativeStorageClient {
    @Volatile private var connection: AbsAdbConnectionManager? = null
    @Volatile private var authorized = false
    private val operationLock = Any()
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "LCPatch-adb-deadline").apply { isDaemon = true }
    }

    @Suppress("UNUSED_PARAMETER")
    fun status(context: Context): String = when {
        Build.VERSION.SDK_INT < 30 -> "內建無線 ADB 需要 Android 11 以上"
        authorized -> "已授權"
        else -> "未連線"
    }

    suspend fun refreshStatus(context: Context): String = withContext(Dispatchers.IO) {
        if (connection?.isConnected != true) authorized = false
        status(context)
    }

    private fun manager(context: Context): AbsAdbConnectionManager {
        check(Build.VERSION.SDK_INT >= 30) { "內建無線 ADB 需要 Android 11 以上" }
        return connection ?: AdbIdentity.create(context.applicationContext).also { connection = it }
    }

    private fun openStream(client: AbsAdbConnectionManager, destination: String): AdbStream {
        // Upstream waits indefinitely for an OPEN acknowledgement. Bound that wait too.
        val executor = Executors.newSingleThreadExecutor()
        val opening = executor.submit<AdbStream> { client.openStream(destination) }
        try {
            return opening.get(15, TimeUnit.SECONDS)
        } catch (error: Exception) {
            opening.cancel(true)
            authorized = false
            runCatching { client.disconnect() }
            throw IllegalStateException("ADB 服務無法啟動或逾時，請重新連線", error)
        } finally { executor.shutdownNow() }
    }

    suspend fun pair(context: Context, port: Int, code: String) = withContext(Dispatchers.IO) {
        synchronized(operationLock) {
            require(port in 1..65535) { "配對連接埠須為 1–65535" }
            require(code.matches(Regex("[0-9]{6}"))) { "請輸入六位數配對碼" }
            try {
                check(manager(context).pair("127.0.0.1", port, code))
            } catch (error: Exception) {
                throw IllegalStateException("配對失敗或逾時；請保留系統配對視窗，重新輸入目前的連接埠與六位數配對碼", error)
            }
        }
    }

    suspend fun connect(context: Context, port: Int) = withContext(Dispatchers.IO) {
        synchronized(operationLock) {
            require(port in 1..65535) { "連線連接埠須為 1–65535" }
            val client = manager(context)
            authorized = false
            client.disconnect()
            try {
                check(client.connect("127.0.0.1", port)) { "無法連線，請確認使用無線偵錯首頁的連接埠" }
                openStream(client, "exec:id -u").use { stream ->
                    val timeout = deadlines.schedule({ runCatching { stream.close() } }, 10, TimeUnit.SECONDS)
                    try {
                        check(stream.openInputStream().bufferedReader().readText().trim() == "2000") {
                            "ADB 未以 shell 身分連線"
                        }
                    } finally { timeout.cancel(false) }
                }
                authorized = true
            } catch (error: Exception) { client.disconnect(); throw error }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        synchronized(operationLock) { authorized = false; connection?.disconnect() }
    }

    suspend fun apply(context: Context, archive: File, language: String, progress: (Int, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            synchronized(operationLock) {
                require(language in setOf("jp", "kr"))
                check(status(context) == "已授權") { "請先在「無線 ADB」連線" }
                val client = manager(context)
                check(client.isConnected) { "ADB 已中斷，請重新連線" }
                val apk = context.applicationInfo.sourceDir
                val command = "exec:CLASSPATH=${AdbWire.quote(apk)} app_process /system/bin com.lcpatch.AdbFileService $language"
                openStream(client, command).use { stream ->
                    val timeout = deadlines.schedule({ runCatching { stream.close() } }, 180, TimeUnit.SECONDS)
                    val sender = Executors.newSingleThreadExecutor()
                    val writing = sender.submit {
                        val length = archive.length()
                        require(length in 1..AdbWire.MAX_ARCHIVE)
                        val output = DataOutputStream(stream.openOutputStream())
                        output.writeLong(length)
                        archive.inputStream().use { it.copyTo(output) }
                        output.flush()
                    }
                    try {
                        var complete = false
                        val reader = stream.openInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            when {
                                line == "LCPATCH OK" -> { complete = true; break }
                                line.startsWith("LCPATCH ERROR ") -> error(AdbWire.decodeError(line.removePrefix("LCPATCH ERROR ")))
                                line.startsWith("LCPATCH PROGRESS ") -> {
                                    val counts = line.removePrefix("LCPATCH PROGRESS ").split(' ')
                                    require(counts.size == 2)
                                    val done = counts[0].toInt()
                                    val total = counts[1].toInt()
                                    require(total in 1..30_000 && done in 0..total)
                                    progress(done, total)
                                }
                            }
                        }
                        writing.get(5, TimeUnit.SECONDS)
                        check(complete) { "ADB 寫檔服務中斷或逾時；請重新連線後套用" }
                    } finally {
                        timeout.cancel(false)
                        runCatching { stream.close() }
                        writing.cancel(true)
                        sender.shutdownNow()
                    }
                }
            }
        }
}
