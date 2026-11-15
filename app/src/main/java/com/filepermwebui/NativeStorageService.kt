package com.lcpatch

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.TimeUnit

/** Shizuku constructs this Binder in a shell process; no general shell execution is exposed. */
@Keep
class NativeStorageService : INativeStorageService.Stub() {
    private fun installer(): NativeFileInstaller {
        val process = ProcessBuilder("am", "get-current-user").redirectErrorStream(true).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("無法取得目前 Android 使用者")
        }
        val user = process.inputStream.bufferedReader().use { it.readText().trim().toIntOrNull() }
        require(process.exitValue() == 0 && user != null && user >= 0) { "無法確認儲存空間" }
        return NativeFileInstaller(File("/storage/emulated/$user/Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize"))
    }

    @Synchronized override fun apply(
        archive: ParcelFileDescriptor,
        language: String,
        callback: INativeProgressCallback
    ): String = try {
        require(language in setOf("jp", "kr"))
        ParcelFileDescriptor.AutoCloseInputStream(archive).use { input ->
            installer().applyOverlay(input, language) { completed, total ->
                // Notification UI must not be able to interrupt a file transaction.
                runCatching { callback.onProgress(completed, total) }
            }
        }
        ""
    } catch (error: Exception) {
        runCatching { archive.close() }
        error.message ?: "原生文件寫入失敗"
    }

    override fun destroy() { kotlin.system.exitProcess(0) }
}
