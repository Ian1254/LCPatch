package com.lcpatch

import org.junit.Assert.*
import org.junit.Test

class GameDiagnosticCollectorTest {
    private val game = GameDiagnosticParser.GAME
    private fun collect(response: (DiagnosticRequest) -> RootCommandResult) = GameDiagnosticCollector {
        if (it.command == "id -u") RootCommandResult(true, "0\n", 0) else response(it)
    }.collect(true)

    @Test fun vendorVersionMessagesAreNotGameCrashes() {
        val supplied = """
            --------- beginning of crash
            10-26 14:54:14.104 1239 1239 I libpq_sec: mtk-brm-commit-id:abcdef
            10-26 14:54:14.413 1518 1518 I libmtkgpuserv: mtk-brm-change-id:xyz
        """.trimIndent()
        assertEquals("", GameDiagnosticParser.relatedLogcat(supplied))
        val result = collect { RootCommandResult(true, supplied, 0) }
        assertEquals(DiagnosticStatus.NO_MATCH, result.first { it.source == "crash buffer" }.status)
        assertTrue(GameDiagnosticParser.render(result).contains("不代表遊戲沒有閃退"))
    }

    @Test fun nativeCrashKeepsProcessAndBacktrace() {
        val raw = """
            10-27 00:30:00.000 900 900 F DEBUG: *** *** *** ***
            10-27 00:30:00.001 900 900 F DEBUG: Cmdline: $game
            10-27 00:30:00.002 900 900 F DEBUG: signal 11 (SIGSEGV), code 1
            10-27 00:30:00.003 900 900 F DEBUG: Abort message: runtime failure
            10-27 00:30:00.004 900 900 F DEBUG: backtrace:
            10-27 00:30:00.005 900 900 F DEBUG: #00 pc 000123 /lib/arm64/liblcpatch_core.so
            10-27 00:30:00.006 900 900 F DEBUG: #01 pc 000456 /lib/arm64/libil2cpp.so
        """.trimIndent()
        val selected = GameDiagnosticParser.relatedLogcat(raw)
        assertTrue(selected.contains("signal 11"))
        assertTrue(selected.contains("Cmdline: $game"))
        assertTrue(selected.contains("#01 pc 000456"))
    }

    @Test fun systemProcessDeathIsKeptWithoutFatalException() {
        val raw = "10-27 00:30:00.001 800 800 I ActivityManager: Process $game (pid 12345) has died: fg TOP"
        val section = collect { RootCommandResult(true, raw, 0) }.first { it.source == "遊戲與程序退出 logcat" }
        assertEquals(DiagnosticStatus.COLLECTED, section.status)
        assertTrue(section.text.contains("has died"))
    }

    @Test fun unrelatedGameNamePrefixDoesNotMatch() {
        assertEquals("", GameDiagnosticParser.relatedLogcat("Process: ${game}OtherApp, PID: 99"))
    }

    @Test fun nonRootModeNeverRequestsRoot() {
        val result = GameDiagnosticCollector { error("Root must not be invoked") }.collect(false)
        assertTrue(result.all { it.status == DiagnosticStatus.SKIPPED })
    }

    @Test fun deniedRootIsReportedInsteadOfEmptySuccess() {
        var requests = 0
        val result = GameDiagnosticCollector { requests++; RootCommandResult(false, "Permission denied", 1) }.collect(true)
        assertEquals(1, requests)
        assertTrue(result.all { it.status == DiagnosticStatus.DENIED })
        assertTrue(GameDiagnosticParser.render(result).contains("Permission denied"))
    }

    @Test fun timeoutIsDifferentFromNoMatch() {
        val result = collect { RootCommandResult(false, "", null, timedOut = true) }
        assertTrue(result.drop(1).all { it.status == DiagnosticStatus.TIMED_OUT })
    }

    @Test fun permissionsAndAbsentTombstonesAreDistinguished() {
        val missing = collect { RootCommandResult(false, "", 3) }.first { it.source == "tombstone" }
        val denied = collect { RootCommandResult(false, "Permission denied", 13) }.first { it.source == "tombstone" }
        assertEquals(DiagnosticStatus.NO_MATCH, missing.status)
        assertEquals(DiagnosticStatus.DENIED, denied.status)
    }

    @Test fun truncatedTombstoneKeepsHeaderAndReportsLimit() {
        val body = "Cmdline: $game\nsignal 11\nbacktrace:\n#00 liblcpatch_core.so"
        val section = collect { request ->
            if (!request.keepTail && request.command.contains("tombstones")) RootCommandResult(true, body, 0, truncated = true)
            else RootCommandResult(true, "", 0)
        }.first { it.source == "tombstone" }
        assertEquals(DiagnosticStatus.TRUNCATED, section.status)
        assertTrue(section.text.startsWith("Cmdline:"))
        assertTrue(section.text.contains("signal 11"))
    }

    @Test fun unsupportedExitInfoIsNotAnExitRecord() {
        val section = collect { RootCommandResult(true, "Bad activity command: exit-info", 0) }.last()
        assertEquals(DiagnosticStatus.UNAVAILABLE, section.status)
        assertFalse(GameDiagnosticParser.hasExitRecords("ACTIVITY MANAGER PROCESS EXIT INFO\nNo records found"))
        assertTrue(GameDiagnosticParser.hasExitRecords("timestamp=2026-10-27 reason=SIGNALED status=9"))
    }

    @Test fun nativeEventsRetainOriginalTimeAndSameMillisecondRows() {
        val first = "1000|INFO|core.start|first"
        val second = "1000|WARN|core.unity.timeout|second"
        val batch = newNativeBreadcrumbs("$first\n$second\n$second", 0, emptySet(), now = 2000)
        assertEquals(2, batch.events.size)
        assertTrue(batch.events.all { it.time == 1000L })
        assertEquals(1000L, batch.latestTime)
        assertEquals(2, batch.latestKeys.size)
        assertTrue(newNativeBreadcrumbs("$first\n$second", batch.latestTime, batch.latestKeys, now = 3000).events.isEmpty())
        val third = "1000|INFO|core.started|third"
        assertEquals(listOf("third"), newNativeBreadcrumbs("$first\n$third", batch.latestTime, batch.latestKeys, now = 3000).events.map { it.message })
    }

    @Test fun malformedAndFutureNativeRowsDoNotPoisonCursor() {
        val result = newNativeBreadcrumbs("broken\n999999|INFO|event|future\n1500|INFO|event|valid", 1000, emptySet(), now = 2000)
        assertEquals(1500L, result.latestTime)
        assertEquals(listOf("valid"), result.events.map { it.message })
    }
}
