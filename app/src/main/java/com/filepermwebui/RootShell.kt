package com.lcpatch

import java.util.concurrent.TimeUnit

internal data class RootCommandResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int?,
    val truncated: Boolean = false,
    val timedOut: Boolean = false
)

internal object RootShell {
    private val operationLock = Any()

    fun run(
        command: String,
        timeoutMs: Long? = null,
        maxOutputChars: Int = 256 * 1024,
        keepTail: Boolean = true,
        executable: String = "su"
    ): RootCommandResult = synchronized(operationLock) {
        try {
            val process = ProcessBuilder(executable, "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            var truncated = false
            val outputLock = Any()
            val reader = Thread({
                try {
                    process.inputStream.bufferedReader().use { source ->
                        val buffer = CharArray(8 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            synchronized(outputLock) {
                                if (keepTail) {
                                    output.append(buffer, 0, count)
                                    val overflow = output.length - maxOutputChars
                                    if (overflow > 0) { output.delete(0, overflow); truncated = true }
                                } else {
                                    val accepted = minOf(count, (maxOutputChars - output.length).coerceAtLeast(0))
                                    output.append(buffer, 0, accepted)
                                    if (accepted < count) truncated = true
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // The process can close the stream while being forcibly terminated.
                }
            }, "LCPatch-root-reader").apply {
                isDaemon = true
                start()
            }

            val finished = if (timeoutMs == null) {
                process.waitFor()
                true
            } else {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            }
            if (!finished) process.destroyForcibly()
            reader.join(1_000)
            val exitCode = if (finished) process.exitValue() else null
            RootCommandResult(
                success = finished && exitCode == 0,
                output = synchronized(outputLock) { output.toString() },
                exitCode = exitCode,
                truncated = synchronized(outputLock) { truncated },
                timedOut = !finished
            )
        } catch (error: Throwable) {
            RootCommandResult(false, error.message.orEmpty(), null)
        }
    }
}
