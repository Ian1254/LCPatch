package com.lcpatch

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest

object CrashDiagnostics {
    private const val GAME = "com.ProjectMoon.LimbusCompany"
    private const val NATIVE_LOG = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log"
    private const val MAX_CHARS = 48 * 1024
    private const val MIN_CAPTURE_INTERVAL_MS = 2 * 60 * 1000L

    @Volatile
    private var lastCaptureElapsedRealtime = 0L

    fun capture(context: Context) {
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastCaptureElapsedRealtime < MIN_CAPTURE_INTERVAL_MS) return
            lastCaptureElapsedRealtime = now
        }

        captureNativeBreadcrumbs(context)
        val crashBuffer = runRootCapture("logcat -b crash -d -v threadtime -t 1200")
        val report = extractLatestGameCrash(crashBuffer).ifBlank {
            extractLatestGameCrash(runRootCapture("for f in \$(ls -t /data/tombstones/tombstone_* 2>/dev/null | head -n 8); do if grep -q '$GAME' \"\$f\"; then tail -n 500 \"\$f\"; break; fi; done"))
        }
        if (report.isBlank()) return

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(report.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        if (prefs.getString("last_crash_hash", null) == digest) return
        prefs.edit().putString("last_crash_hash", digest).apply()
        LogRepository.append(context, "ERROR", "game.crash", report, GAME)
    }

    private fun captureNativeBreadcrumbs(context: Context) {
        val raw = runRootCapture("tail -n 200 '$NATIVE_LOG'")
        if (raw.isBlank()) return
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        var latest = prefs.getLong("last_native_time", 0L)
        raw.lineSequence().forEach { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size != 4) return@forEach
            val timestamp = parts[0].toLongOrNull() ?: return@forEach
            if (timestamp <= latest) return@forEach
            LogRepository.append(context, parts[1], parts[2], parts[3], GAME)
            latest = maxOf(latest, timestamp)
        }
        prefs.edit().putLong("last_native_time", latest).apply()
    }

    private fun runRootCapture(command: String): String =
        RootShell.run(command, timeoutMs = 8_000L, maxOutputChars = MAX_CHARS).output

    private fun extractLatestGameCrash(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.lineSequence().toList()
        val hit = lines.indexOfLast {
            it.contains(GAME, ignoreCase = true) ||
                it.contains("liblcpatch_core", ignoreCase = true) ||
                it.contains("LCPatchCore", ignoreCase = true)
        }
        if (hit < 0) return ""
        var start = hit
        while (start > 0 && hit - start < 40 &&
            !lines[start].contains("beginning of crash") &&
            !lines[start].startsWith("*** ***")) start--
        val end = minOf(lines.size, hit + 180)
        return lines.subList(start, end).joinToString("\n").take(MAX_CHARS).trim()
    }
}
