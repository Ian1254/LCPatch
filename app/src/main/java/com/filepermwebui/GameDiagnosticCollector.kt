package com.lcpatch

import java.time.Instant

internal enum class DiagnosticStatus(val label: String) {
    COLLECTED("已取得"), NO_MATCH("未找到相關記錄"), TRUNCATED("已取得但截斷"),
    DENIED("權限不足"), TIMED_OUT("收集逾時"), UNAVAILABLE("無法取得"), SKIPPED("未收集")
}

internal data class DiagnosticSection(
    val source: String,
    val status: DiagnosticStatus,
    val time: String,
    val detail: String,
    val text: String = ""
)

internal data class DiagnosticRequest(val command: String, val maxChars: Int, val keepTail: Boolean = true)

internal data class NativeBreadcrumb(val time: Long, val level: String, val code: String, val message: String, val key: String)
internal data class NativeImportBatch(val events: List<NativeBreadcrumb>, val latestTime: Long, val latestKeys: Set<String>)

internal fun newNativeBreadcrumbs(raw: String, previous: Long, previousKeys: Set<String>, now: Long = System.currentTimeMillis()): NativeImportBatch {
    val seen = previousKeys.toMutableSet()
    val events = raw.lineSequence().mapNotNull { line ->
        val parts = line.split('|', limit = 4)
        if (parts.size != 4) return@mapNotNull null
        val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
        if (timestamp < previous || timestamp <= 0 || timestamp > now + 60_000 || timestamp == previous && line in previousKeys || !seen.add(line)) return@mapNotNull null
        NativeBreadcrumb(timestamp, parts[1], parts[2], parts[3], line)
    }.toList()
    val latest = maxOf(previous, events.maxOfOrNull { it.time } ?: previous)
    val keys = events.filter { it.time == latest }.map { it.key }.toMutableSet()
    if (latest == previous) keys += previousKeys
    return NativeImportBatch(events, latest, keys)
}

internal object GameDiagnosticParser {
    const val GAME = "com.ProjectMoon.LimbusCompany"
    private val target = Regex("com\\.ProjectMoon\\.LimbusCompany(?=[:,\\s/)>]|$)", RegexOption.IGNORE_CASE)
    private val pidPatterns = listOf(
        Regex("Process:\\s*com\\.ProjectMoon\\.LimbusCompany[^,]*,\\s*PID:\\s*(\\d+)"),
        Regex("Start proc\\s+(\\d+):com\\.ProjectMoon\\.LimbusCompany(?=[: \\s/)>]|$)"),
        Regex("Process com\\.ProjectMoon\\.LimbusCompany[^ ]* \\(pid (\\d+)\\)")
    )
    private val threadtime = Regex("^\\d{2}-\\d{2}\\s+\\S+\\s+(\\d+)\\s+\\d+\\s+[VDIWEF]\\s+")

    fun gamePids(raw: String): Set<String> = pidPatterns.flatMap { pattern ->
        pattern.findAll(raw).filter { target.containsMatchIn(it.value) }.map { it.groupValues[1] }.toList()
    }.toSet()

    fun logcatPids(raw: String): Set<String> = gamePids(raw) + raw.lineSequence().filter {
        target.containsMatchIn(it) && (it.contains("Cmdline:") || it.contains("Process:"))
    }.mapNotNull { threadtime.find(it)?.groupValues?.get(1) }.toSet()

    fun exitPids(gameExitInfo: String): Set<String> =
        Regex("\\bpid\\s*[=:]\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .findAll(gameExitInfo).map { it.groupValues[1] }.filter { it.toLongOrNull() in 1..Int.MAX_VALUE.toLong() }.take(64).toSet()

    fun relatedLogcat(raw: String, maxChars: Int = 96 * 1024, knownPids: Set<String> = emptySet()): String {
        val lines = raw.lines()
        val pids = gamePids(raw) + knownPids
        // A debuggerd/AndroidRuntime reporter is separate from the crashed process.
        // Its own PID is accepted only with an explicit game process declaration.
        val reporters = lines.filter { target.containsMatchIn(it) &&
            (it.contains("Cmdline:") || it.contains("Process:")) }
            .mapNotNull { threadtime.find(it)?.groupValues?.get(1) }.toSet()
        val selectedPids = pids + reporters
        val exitTags = Regex("lmkd|ActivityManager|libprocessgroup|Zygote|DEBUG|tombstoned|crash_dump|AndroidRuntime")
        val referencedPid = selectedPids.takeIf { it.isNotEmpty() }?.let { Regex("\\bpid[ =:]+(" + it.joinToString("|") { Regex.escape(it) } + ")(?![0-9])") }
        return lines.filter { line ->
            target.containsMatchIn(line) || line.contains("liblcpatch_core") || line.contains("LCPatchCore") ||
                threadtime.find(line)?.groupValues?.get(1) in selectedPids ||
                (referencedPid?.containsMatchIn(line) == true && exitTags.containsMatchIn(line))
        }.joinToString("\n").takeLast(maxChars).trim()
    }

    fun hasExitRecords(raw: String): Boolean =
        Regex("reason\\s*[=:]|ApplicationExitInfo|timestamp\\s*[=:]", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    fun render(sections: List<DiagnosticSection>): String = buildString {
        appendLine("--- 遊戲程序診斷 ---")
        appendLine("未找到記錄、逾時或無權限，都不代表遊戲沒有閃退。")
        if (sections.isEmpty()) appendLine("尚未收集系統診斷資料。")
        sections.forEach { section ->
            appendLine("[${section.source}] ${section.status.label} · ${section.time}")
            appendLine(section.detail)
            if (section.text.isNotBlank()) appendLine(section.text)
            appendLine()
        }
    }
}

internal class GameDiagnosticCollector(private val run: (DiagnosticRequest) -> RootCommandResult) {
    companion object {
        const val NATIVE_LOG = "/storage/emulated/0/Android/data/com.ProjectMoon.LimbusCompany/cache/lcpatch-native.log"
        val sources = listOf("Root 權限", "原生模組事件", "crash buffer", "遊戲與程序退出 logcat", "tombstone", "系統程序退出資訊")
        // Filter the retained ring buffer before RootShell applies its output bound.
        // No -t/-T: a busy system must not evict the game's exit from our snapshot first.
        fun logcatCommand(buffers: String, pids: Set<String>): String {
            val numeric = pids.filter { it.matches(Regex("[1-9][0-9]{0,9}")) }.take(128)
            val pattern = buildString {
                append("com[.]ProjectMoon[.]LimbusCompany([: ,/)>]|$)|LCPatchCore|liblcpatch_core")
                if (numeric.isNotEmpty()) {
                    val ids = numeric.joinToString("|")
                    append("|[[:space:]]($ids)[[:space:]]+[0-9]+[[:space:]]+[VDIWEF][[:space:]]")
                    append("|(lmkd|ActivityManager|libprocessgroup|Zygote|DEBUG|tombstoned|crash_dump|AndroidRuntime).*pid[ =:]+($ids)([^0-9]|$)")
                }
            }
            return "set -o pipefail; logcat $buffers -d -v threadtime | { grep -E '$pattern'; status=\$?; " +
                "if [ \"\$status\" = 1 ]; then exit 0; else exit \"\$status\"; fi; }"
        }
        val tombstoneCommand = """
            if [ ! -d /data/tombstones ]; then echo 'tombstone directory unavailable'; exit 4; fi
            names=${'$'}(ls -t /data/tombstones 2>&1) || { printf '%s\n' "${'$'}names"; exit 13; }
            count=0
            for name in ${'$'}names; do
              case "${'$'}name" in tombstone_[0-9][0-9]) ;; *) continue ;; esac
              count=${'$'}((count+1)); [ "${'$'}count" -le 32 ] || break
              file="/data/tombstones/${'$'}name"
              header=${'$'}(head -n 40 "${'$'}file" 2>&1) || { printf '%s\n' "${'$'}header"; exit 13; }
              if printf '%s\n' "${'$'}header" | grep -Fq '${GameDiagnosticParser.GAME}'; then
                printf 'Source: %s\n' "${'$'}file"
                cat "${'$'}file"
                exit ${'$'}?
              fi
            done
            exit 3
        """.trimIndent()
    }

    fun collect(rootMode: Boolean): List<DiagnosticSection> {
        val now = Instant.now().toString()
        if (!rootMode) return sources.map { DiagnosticSection(it, DiagnosticStatus.SKIPPED, now, "無 root 模式未要求 Root 權限；未取得其他 App 的系統記錄。") }
        val root = run(DiagnosticRequest("id -u", 2048))
        if (!root.success || root.output.trim() != "0") {
            val status = when {
                root.timedOut -> DiagnosticStatus.TIMED_OUT
                root.exitCode == null -> DiagnosticStatus.UNAVAILABLE
                else -> DiagnosticStatus.DENIED
            }
            return sources.map { DiagnosticSection(it, status,
                now, "未取得 Root 權限，無法完成遊戲診斷。${root.output.take(1000)}") }
        }
        val result = mutableListOf(DiagnosticSection(sources[0], DiagnosticStatus.COLLECTED, now, "Root 指令身分：uid=0"))
        result += capture(sources[1], DiagnosticRequest("tail -n 300 '$NATIVE_LOG'", 96 * 1024)) { it.trim() }
        val exits = capture(sources[5], DiagnosticRequest("dumpsys activity exit-info ${GameDiagnosticParser.GAME}", 64 * 1024, keepTail = false)) {
            if (GameDiagnosticParser.hasExitRecords(it)) it.trim() else ""
        }
        val historicPids = GameDiagnosticParser.exitPids(exits.text)
        result += collectLogcat(sources[2], "-b crash", historicPids)
        result += collectLogcat(sources[3], "-b main -b system -b events", historicPids)
        result += capture(sources[4], DiagnosticRequest(tombstoneCommand, 96 * 1024, keepTail = false)) { it.trim() }
        result += exits
        return result
    }

    private fun collectLogcat(name: String, buffers: String, historicPids: Set<String>): DiagnosticSection {
        val seedRequest = DiagnosticRequest(logcatCommand(buffers, historicPids), 64 * 1024)
        val seed = run(seedRequest)
        if (!seed.success) return captureResponse(name, seedRequest, seed) { GameDiagnosticParser.relatedLogcat(it, knownPids = historicPids) }
        val pids = historicPids + GameDiagnosticParser.logcatPids(seed.output)
        if (pids.isEmpty()) return captureResponse(name, seedRequest, seed) { GameDiagnosticParser.relatedLogcat(it) }
        val request = DiagnosticRequest(logcatCommand(buffers, pids), 96 * 1024)
        val response = run(request)
        // Preserve exit anchors separately from a potentially busy game's PID tail.
        val anchors = GameDiagnosticParser.relatedLogcat(seed.output, 32 * 1024, historicPids)
        val section = captureResponse(name, request, response) { GameDiagnosticParser.relatedLogcat(it, 64 * 1024, pids) }
        val text = listOf(anchors, section.text).filter { it.isNotBlank() }.distinct().joinToString("\n").take(96 * 1024)
        val limited = seed.truncated || response.truncated || seed.output.length > 32 * 1024 || response.output.length > 64 * 1024
        val status = when {
            section.status == DiagnosticStatus.COLLECTED && limited -> DiagnosticStatus.TRUNCATED
            section.status == DiagnosticStatus.NO_MATCH && text.isNotEmpty() -> if (limited) DiagnosticStatus.TRUNCATED else DiagnosticStatus.COLLECTED
            else -> section.status
        }
        return section.copy(text = text, status = status)
    }

    private fun capture(name: String, request: DiagnosticRequest, select: (String) -> String): DiagnosticSection {
        return captureResponse(name, request, run(request), select)
    }

    private fun captureResponse(name: String, request: DiagnosticRequest, response: RootCommandResult, select: (String) -> String): DiagnosticSection {
        val time = Instant.now().toString()
        if (!response.success) {
            val status = when {
                response.timedOut -> DiagnosticStatus.TIMED_OUT
                response.exitCode == 3 -> DiagnosticStatus.NO_MATCH
                response.exitCode == 13 || Regex("permission denied|not permitted|SecurityException", RegexOption.IGNORE_CASE).containsMatchIn(response.output) -> DiagnosticStatus.DENIED
                else -> DiagnosticStatus.UNAVAILABLE
            }
            return DiagnosticSection(name, status, time, "指令退出碼：${response.exitCode ?: "無"}。${response.output.take(1500)}")
        }
        if (Regex("Unknown command|Bad activity command", RegexOption.IGNORE_CASE).containsMatchIn(response.output)) {
            return DiagnosticSection(name, DiagnosticStatus.UNAVAILABLE, time, "系統不支援此診斷指令。${response.output.take(1000)}")
        }
        val selected = select(response.output)
        val truncated = response.truncated || selected.length >= 96 * 1024
        val status = when {
            truncated -> DiagnosticStatus.TRUNCATED
            selected.isBlank() -> DiagnosticStatus.NO_MATCH
            else -> DiagnosticStatus.COLLECTED
        }
        return DiagnosticSection(name, status, time,
            "有界快照；${if (request.keepTail) "保留最近資料" else "保留開頭與程序識別資訊"}。" +
                if (selected.isBlank()) "未找到遊戲相關內容，不能據此判定沒有閃退。" else "", selected)
    }
}
