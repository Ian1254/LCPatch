package com.lcpatch

import android.os.Process
import androidx.annotation.Keep
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** One authenticated ADB exec stream per apply; no socket listener or general command API. */
@Keep
object AdbFileService {
    @JvmStatic fun main(args: Array<String>) {
        Thread({ Thread.sleep(185_000); kotlin.system.exitProcess(2) }, "LCPatch-file-deadline")
            .apply { isDaemon = true; start() }
        try {
            check(Process.myUid() == 2000) { "文件服務需要 ADB shell 身分" }
            require(args.size == 1 && args[0] in setOf("jp", "kr"))
            val process = ProcessBuilder("am", "get-current-user").redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("無法取得目前 Android 使用者")
            }
            val user = process.inputStream.bufferedReader().use { it.readText().trim().toIntOrNull() }
            check(process.exitValue() == 0 && user != null && user >= 0) { "無法確認遊戲資料目錄" }
            val directory = File("/storage/emulated/$user/Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize")
            val input = DataInputStream(System.`in`)
            val archive = AdbWire.LimitedInput(input, input.readLong())
            val transfer = java.nio.file.Files.createTempFile(File("/data/local/tmp").toPath(), "lcpatch-adb-", ".zip").toFile()
            try {
                // Receive the complete frame before changing any game files.
                transfer.outputStream().use { archive.copyTo(it) }
                transfer.inputStream().use { zip ->
                    NativeFileInstaller(directory).applyOverlay(zip, args[0]) { done, total ->
                        println("LCPATCH PROGRESS $done $total")
                        System.out.flush()
                    }
                }
            } finally {
                transfer.delete()
            }
            println("LCPATCH OK")
            System.out.flush()
            kotlin.system.exitProcess(0)
        } catch (error: Throwable) {
            println("LCPATCH ERROR ${AdbWire.encodeError(error.message ?: "寫入失敗")}")
            System.out.flush()
            kotlin.system.exitProcess(1)
        }
    }
}
