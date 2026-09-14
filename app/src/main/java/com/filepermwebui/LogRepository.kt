package com.lcpatch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.File
import java.time.Instant

data class LogEvent(val time: String, val level: String, val code: String, val message: String, val process: String)

class LogRepository(private val context: Context) {
    private val uri = Uri.parse("content://${LogProvider.AUTHORITY}")
    private val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    fun read(): List<LogEvent> {
        val raw = context.contentResolver.call(uri, "read", null, null)?.getString("events") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LogEvent(item.optString("time"), item.optString("level"), item.optString("code"), item.optString("message"), item.optString("process"))
        }.reversed()
    }

    fun clear() {
        context.contentResolver.call(uri, "clear", null, null)
        Thread({ runCatching { ProcessBuilder("su", "-c", "truncate -s 0 '$NATIVE_LOG'").start().waitFor() } },
            "LCPatch-clear-native-log").start()
    }

    fun observe(onChange: () -> Unit): ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onChange()
    }.also { context.contentResolver.registerContentObserver(uri, true, it) }

    fun stopObserving(observer: ContentObserver) {
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun selectedFolder(): Uri? = prefs.getString("tree", null)?.let(Uri::parse)

    fun selectedFolderLabel(): String = selectedFolder()?.let {
        DocumentFile.fromTreeUri(context, it)?.name ?: it.lastPathSegment ?: "已選擇的資料夾"
    } ?: "未指定（分享時使用暫存檔）"

    fun setSelectedFolder(value: Uri) {
        context.contentResolver.takePersistableUriPermission(value, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString("tree", value.toString()).apply()
    }

    fun resetSelectedFolder() { prefs.edit().remove("tree").apply() }

    fun diagnosticText(events: List<LogEvent>): String {
        val game = try {
            val info = context.packageManager.getPackageInfo(LogProvider.GAME, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) { "未安裝或無法讀取" }
        val module = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("LCPatch 診斷文件")
            appendLine("產生時間：${Instant.now()}")
            appendLine("模組版本：${module.versionName} (${module.longVersionCode})")
            appendLine("遊戲版本：$game")
            appendLine("裝置：${Build.MANUFACTURER} ${Build.MODEL} / ${Build.DEVICE}")
            appendLine("系統：Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("--- events (${events.size}) ---")
            events.asReversed().forEach { appendLine("${it.time} [${it.level}] ${it.code}: ${it.message}${if (it.process.isBlank()) "" else " (${it.process})"}") }
        }
    }

    fun saveDiagnostic(events: List<LogEvent>): Uri {
        val name = "LCPatch-log-${System.currentTimeMillis()}.txt"
        val bytes = diagnosticText(events).toByteArray()
        val tree = selectedFolder()
        if (tree != null) {
            val folder = DocumentFile.fromTreeUri(context, tree) ?: error("無法開啟已選資料夾")
            val file = folder.createFile("text/plain", name) ?: error("無法建立診斷文件")
            context.contentResolver.openOutputStream(file.uri, "w")!!.use { it.write(bytes) }
            return file.uri
        }
        val folder = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(folder, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "com.lcpatch.files", file)
    }

    fun shareIntent(events: List<LogEvent>): Intent {
        val output = saveDiagnostic(events)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LCPatch 日誌")
            putExtra(Intent.EXTRA_STREAM, output)
            putExtra(Intent.EXTRA_TEXT, "LCPatch 診斷文件，可附到 Codex 對話分析。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享日誌")
    }

    companion object {
        private const val NATIVE_LOG = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log"
        fun append(context: Context, level: String, code: String, message: String, process: String = "") {
            context.contentResolver.call(Uri.parse("content://${LogProvider.AUTHORITY}"), "append", null, Bundle().apply {
                putString("level", level); putString("code", code); putString("message", message); putString("process", process)
            })
        }
    }
}
