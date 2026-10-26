package com.lcpatch

/** Operator-only debug entry point; absent from release and the app manifest. */
internal object DiagnosticSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val executable = args.singleOrNull() ?: "su"
        val head = RootShell.run("printf 'HEADER\\n'; seq 1 1000; printf 'FOOTER\\n'", timeoutMs = 3000, maxOutputChars = 64, keepTail = false, executable = executable)
        check(head.success && head.truncated && head.output.startsWith("HEADER")) { "Head capture lost its header: $head" }
        val tail = RootShell.run("printf 'HEADER\\n'; seq 1 1000; printf 'FOOTER\\n'", timeoutMs = 3000, maxOutputChars = 64, executable = executable)
        check(tail.success && tail.truncated && tail.output.endsWith("FOOTER\n")) { "Tail capture failed: $tail" }
        val timeout = RootShell.run("exec sleep 2", timeoutMs = 100, executable = executable)
        check(!timeout.success && timeout.timedOut) { "Timeout was not reported: $timeout" }
        val sections = GameDiagnosticCollector { request ->
            RootShell.run(request.command, timeoutMs = 6000, maxOutputChars = request.maxChars, keepTail = request.keepTail, executable = executable)
        }.collect(true)
        check(sections.first().status == DiagnosticStatus.COLLECTED) { "Root probe failed" }
        println(GameDiagnosticParser.render(sections))
        println("PASS: Android command collection, head/tail bounds and timeout classification; source statuses are explicit")
        System.out.flush()
        kotlin.system.exitProcess(0)
    }
}
