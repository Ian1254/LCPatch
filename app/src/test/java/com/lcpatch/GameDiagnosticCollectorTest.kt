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
    @Test fun adjacentPrivateSystemRowsAreExcludedButReporterStackSurvives() {
        val raw = """
            10-27 00:30:00.000 800 800 I PrivateApp: unrelated secret before
            10-27 00:30:00.001 900 900 F DEBUG: Cmdline: $game
            10-27 00:30:00.002 800 800 I PrivateApp: unrelated secret interleaved
            10-27 00:30:00.003 900 900 F DEBUG: signal 11 (SIGSEGV)
            10-27 00:30:00.004 900 900 F DEBUG: #00 pc libil2cpp.so
        """.trimIndent()
        val selected = GameDiagnosticParser.relatedLogcat(raw)
        assertFalse(selected.contains("secret"))
        assertTrue(selected.contains("signal 11"))
        assertTrue(selected.contains("libil2cpp.so"))
    }

    @Test fun exitInfoPidKeepsExitWithoutPackageNameAndDoesNotMatchPidPrefix() {
        val pids = setOf("12345")
        val raw = """
            10-27 00:30:00.000 12345 12345 I libc: exit_self
            10-27 00:30:00.001 800 800 I libprocessgroup: killed pid 12345
            10-27 00:30:00.002 123456 123456 I PrivateApp: unrelated secret
        """.trimIndent()
        val selected = GameDiagnosticParser.relatedLogcat(raw, knownPids = pids)
        assertTrue(selected.contains("exit_self"))
        assertTrue(selected.contains("killed pid 12345"))
        assertFalse(selected.contains("secret"))
        assertTrue(GameDiagnosticParser.gamePids("Start proc 123456:${game}OtherApp").isEmpty())
    }

    @Test fun collectorFiltersBeforeBoundsAndRefetchesReporterContext() {
        val requests = mutableListOf<DiagnosticRequest>()
        val result = collect { request ->
            requests += request
            when {
                request.command.startsWith("tail") -> RootCommandResult(true, "${System.currentTimeMillis()}|INFO|core.start|pid=12345 tid=12345", 0)
                request.command.startsWith("dumpsys") -> RootCommandResult(true, "ApplicationExitInfo pid=12345 reason=SIGNALED status=9", 0)
                request.command.contains("logcat") && request.command.contains("(12345|900)") -> RootCommandResult(true,
                    "10-27 00:30:00.002 900 900 F DEBUG: signal 11\n10-27 00:30:00.003 900 900 F DEBUG: #00 pc libil2cpp.so", 0)
                request.command.contains("logcat") -> RootCommandResult(true, "10-27 00:30:00.001 900 900 F DEBUG: Cmdline: $game", 0)
                else -> RootCommandResult(true, "", 0)
            }
        }
        val logcatRequests = requests.filter { it.command.contains("logcat") }
        assertTrue(logcatRequests.all { Regex(" -t '[0-9]+[.][0-9]{3}'").containsMatchIn(it.command) })
        assertTrue(logcatRequests.all { it.command.contains("| { grep -E") && it.maxChars <= 96 * 1024 })
        assertTrue(logcatRequests.first().command.contains("12345"))
        assertTrue(result.first { it.source == "crash buffer" }.text.contains("signal 11"))
        assertTrue(result.first { it.source == "crash buffer" }.text.contains("Cmdline: $game"))
    }

    @Test fun shellFilterRetainsEarlyExitAfterThousandsOfUnrelatedRows() {
        org.junit.Assume.assumeTrue(java.io.File("/bin/bash").canExecute())
        val fixture = java.nio.file.Files.createTempFile("game-logcat", ".txt").toFile()
        try {
            fixture.writeText("10-27 00:30:00.000 12345 12345 I libc: exit_self\n" +
                (1..10000).joinToString("\n") { "10-27 00:30:00.001 800 800 I PrivateApp: unrelated secret $it" })
            val path = "'" + fixture.path.replace("'", "'\\''") + "'"
            val command = "logcat() { cat $path; }; " + GameDiagnosticCollector.logcatCommand("-b main", setOf("12345"), "1794643268.678")
            val process = ProcessBuilder("/bin/bash", "-c", command).redirectErrorStream(true).start()
            val selected = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            assertTrue(selected.contains("exit_self"))
            assertFalse(selected.contains("secret"))
            assertTrue(selected.length < 1024)
        } finally { fixture.delete() }
    }
    @Test fun latestNativeLaunchUsesEpochAndExcludesHistoricalPids() {
        val now = 1794643312284L
        val native = "1794642000000|INFO|core.start|pid=1111 tid=1111\n1794643273678|INFO|core.start|pid=2734 tid=2734"
        val exits = """
            ApplicationExitInfo #0:
              timestamp=2026-11-14 16:01:25.188 pid=2734 realUid=10368
              process=$game reason=2 status=9
            ApplicationExitInfo #1:
              timestamp=2026-11-14 16:01:15.906 pid=3180 realUid=90106
              process=$game:ip_service reason=13 status=0
            ApplicationExitInfo #2:
              timestamp=2026-11-14 15:00:03.770 pid=31255 realUid=10368
              process=$game reason=2 status=9
        """.trimIndent()
        val window = GameDiagnosticParser.recentWindow(native, exits, now, java.time.ZoneId.of("Asia/Taipei"))
        assertEquals("1794643268.678", window.logcatTime)
        assertEquals(setOf("2734"), window.pids)
        assertEquals("latest_native_launch", window.origin)
        // Native epoch is absolute; parsing on a machine in another zone does not shift the query.
        assertEquals(window.logcatTime, GameDiagnosticParser.recentWindow(native, "", now, java.time.ZoneId.of("UTC")).logcatTime)
    }

    @Test fun recentExitFallbackAndStaleDataHaveStrictLookbackBounds() {
        val now = 1794643312284L
        val exits = """
            ApplicationExitInfo #0:
              timestamp=2026-11-14 16:01:25.188 pid=2734 realUid=10368
              process=$game reason=2 status=9
        """.trimIndent()
        val window = GameDiagnosticParser.recentWindow("", exits, now, java.time.ZoneId.of("Asia/Taipei"))
        assertEquals(1794643255188L, window.sinceMillis)
        assertEquals(setOf("2734"), window.pids)
        val previousLaunch = GameDiagnosticParser.recentWindow("${now - 90000}|INFO|core.start|pid=1111", exits, now, java.time.ZoneId.of("Asia/Taipei"))
        assertEquals(setOf("2734"), previousLaunch.pids)
        assertEquals("latest_game_exit", previousLaunch.origin)
        val stale = GameDiagnosticParser.recentWindow("1794642000000|INFO|core.start|pid=1111", exits, now + 3600000L, java.time.ZoneId.of("Asia/Taipei"))
        assertEquals(now + 3600000L - 60000L, stale.sinceMillis)
        assertTrue(stale.pids.isEmpty())
        val bounded = GameDiagnosticParser.recentWindow("${now - 119999}|INFO|core.start|pid=2734", "", now)
        assertEquals(now - 120000L, bounded.sinceMillis)
    }

    @Test fun timeoutKeepsOnlyRelatedPartialOutputAndNeverLeaksRawDetail() {
        val sections = collect { request ->
            when {
                request.command.startsWith("tail") -> RootCommandResult(true, "${System.currentTimeMillis()}|INFO|core.start|pid=2734", 0)
                request.command.contains("logcat") -> RootCommandResult(false,
                    "11-14 16:01:25.001 800 800 I PrivateApp: private secret\n11-14 16:01:25.002 2734 2734 I libc: exit_self", null, timedOut = true)
                else -> RootCommandResult(true, "", 0)
            }
        }
        val section = sections.first { it.source == "遊戲與程序退出 logcat" }
        assertEquals(DiagnosticStatus.TIMED_OUT, section.status)
        assertFalse(section.detail.contains("secret"))
        assertFalse(section.text.contains("secret"))
        assertTrue(section.text.contains("exit_self"))
    }
}
