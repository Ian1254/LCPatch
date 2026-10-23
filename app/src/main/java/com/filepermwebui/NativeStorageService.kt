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

    private fun stopGame() {
        val process = ProcessBuilder("am", "force-stop", "--user", "current", "com.ProjectMoon.LimbusCompany")
            .redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("無法關閉遊戲，未變更文件")
        }
        check(process.exitValue() == 0) { "無法關閉遊戲，未變更文件" }
    }

    @Synchronized override fun apply(archive: ParcelFileDescriptor, language: String): String = try {
        require(language in setOf("jp", "kr"))
        stopGame()
        ParcelFileDescriptor.AutoCloseInputStream(archive).use { installer().apply(it, language) }
        ""
    } catch (error: Exception) {
        archive.close()
        error.message ?: "原生文件寫入失敗"
    }

    @Synchronized override fun restore(): String = try {
        stopGame()
        installer().restore()
        ""
    } catch (error: Exception) { error.message ?: "原始文件還原失敗" }

    override fun hasBackup(): Boolean = installer().hasBackup()
    override fun destroy() { kotlin.system.exitProcess(0) }
}
