package com.lcpatch

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object CrashDiagnostics {
    private var lastCaptureElapsedRealtime: Long? = null
    private const val MIN_CAPTURE_INTERVAL_MS = 60_000L

    @Synchronized
    @JvmOverloads
    fun capture(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val last = lastCaptureElapsedRealtime
        if (!force && last != null && now - last in 0 until MIN_CAPTURE_INTERVAL_MS) return
        val sections = GameDiagnosticCollector { request ->
            RootShell.run(request.command, timeoutMs = 6_000L, maxOutputChars = request.maxChars, keepTail = request.keepTail)
        }.collect(TranslationRepository(context).rootMode())
        val previous = readSections(context, "game-diagnostics.json")
        val history = readSections(context, "game-diagnostics-history.json").associateBy { it.source }.toMutableMap()
        previous.filter { it.text.isNotBlank() && it.status in setOf(DiagnosticStatus.COLLECTED, DiagnosticStatus.TRUNCATED) }
            .forEach { history[it.source] = it }
        writeSections(context, "game-diagnostics-history.json", history.values.toList())
        writeSections(context, "game-diagnostics.json", sections)
        sections.firstOrNull { it.source == "原生模組事件" && it.text.isNotBlank() }?.let { importNative(context, it.text) }
        val digest = MessageDigest.getInstance("SHA-256").digest(sections.joinToString { "${it.source}|${it.status}|${it.text}|${it.detail}" }.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        if (prefs.getString("last_capture_hash", null) != digest) {
            LogRepository.append(context, "INFO", "diagnostics.capture", sections.joinToString("；") { "${it.source}：${it.status.label}" })
            prefs.edit().putString("last_capture_hash", digest).apply()
        }
        lastCaptureElapsedRealtime = SystemClock.elapsedRealtime()
    }

    @Synchronized
    internal fun latest(context: Context): List<DiagnosticSection> {
        val current = readSections(context, "game-diagnostics.json")
        val history = readSections(context, "game-diagnostics-history.json").filter { old ->
            current.none { it.source == old.source && it.text.isNotBlank() }
        }.map { it.copy(source = "${it.source}（上次可用快照）", detail = "歷史資料，不是本次收集結果。${it.detail}") }
        return current + history
    }

    @Synchronized
    fun clear(context: Context, clearEvents: () -> Unit) {
        val nativeResult = if (TranslationRepository(context).rootMode()) {
            RootShell.run("if [ -f '${GameDiagnosticCollector.NATIVE_LOG}' ]; then truncate -s 0 '${GameDiagnosticCollector.NATIVE_LOG}'; fi", timeoutMs = 6_000L)
        } else null
        listOf("game-diagnostics.json", "game-diagnostics-history.json").forEach {
            AtomicFile(File(context.filesDir, it)).delete()
        }
        lastCaptureElapsedRealtime = null
        context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit().remove("last_capture_hash").apply()
        clearEvents()
        if (nativeResult != null && !nativeResult.success) {
            LogRepository.append(context, "WARN", "diagnostics.clear_partial", "App 記錄已清除，原生檔案未能清除：${nativeResult.output.take(1000)}")
        }
    }

    private fun importNative(context: Context, raw: String) {
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val previous = prefs.getLong("last_native_time", 0L)
        val previousKeys = prefs.getStringSet("last_native_keys", emptySet()).orEmpty()
        val batch = newNativeBreadcrumbs(raw, previous, previousKeys)
        batch.events.forEach { row -> LogRepository.append(context, row.level, row.code, row.message, GameDiagnosticParser.GAME, row.time) }
        prefs.edit().putLong("last_native_time", batch.latestTime).putStringSet("last_native_keys", batch.latestKeys).apply()
    }

    private fun readSections(context: Context, name: String): List<DiagnosticSection> = try {
        val file = AtomicFile(File(context.filesDir, name))
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            DiagnosticSection(row.getString("source"), DiagnosticStatus.valueOf(row.getString("status")),
                row.getString("time"), row.optString("detail"), row.optString("text"))
        }
    } catch (_: Exception) { emptyList() }

    private fun writeSections(context: Context, name: String, sections: List<DiagnosticSection>) {
        val array = JSONArray()
        sections.forEach { row -> array.put(JSONObject().put("source", row.source).put("status", row.status.name)
            .put("time", row.time).put("detail", row.detail).put("text", row.text)) }
        val file = AtomicFile(File(context.filesDir, name))
        val stream = file.startWrite()
        try { stream.write(array.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
