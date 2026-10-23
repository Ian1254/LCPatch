package com.lcpatch

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Native language directory transaction. Only jp/kr are accepted; other game files stay untouched. */
internal class NativeFileInstaller(private val localize: File) {
    private val backup = File(localize, ".lcpatch-native-backup")
    private val next = File(localize, ".lcpatch-native-next")
    private val old = File(localize, ".lcpatch-native-old")

    fun hasBackup(): Boolean = backup.isDirectory && File(backup, "language").isFile

    private fun languageDirectory(language: String): File {
        require(language in setOf("jp", "kr")) { "只支援 jp／kr 原生語言目錄" }
        return File(localize, language)
    }

    fun restore() {
        if (!hasBackup()) return
        val language = File(backup, "language").readText().trim()
        val target = languageDirectory(language)
        val original = File(backup, "original")
        // A stopped process may leave a completed restore with only cleanup pending.
        if (original.isDirectory) {
            next.deleteRecursively()
            original.copyRecursively(next, overwrite = true)
            swap(next, target)
        }
        check(backup.deleteRecursively()) { "原始文件已還原，無法清理備份" }
    }

    private fun swap(source: File, target: File) {
        // Recover a previous swap interrupted after the first rename.
        if (!target.exists() && old.exists()) check(old.renameTo(target)) { "無法復原中斷的目錄交換" }
        check(old.deleteRecursively()) { "無法清理上次的目錄交換" }
        if (target.exists()) check(target.renameTo(old)) { "無法保留原語言目錄" }
        if (!source.renameTo(target)) {
            if (old.exists()) check(old.renameTo(target)) { "無法還原語言目錄，請保留備份並重試" }
            error("無法更新遊戲語言目錄")
        }
        check(old.deleteRecursively()) { "已更新語言文件，但無法清理舊目錄" }
    }

    fun apply(archive: InputStream, language: String) {
        val target = languageDirectory(language)
        require(target.isDirectory && target.listFiles()?.isNotEmpty() == true) {
            "請先在遊戲下載 ${language.uppercase()} 語言資源"
        }
        // Restore the baseline before switching either the pack or target language.
        restore()
        check(backup.deleteRecursively()) { "無法清理未完成的備份" }
        check(backup.mkdirs() || backup.isDirectory) { "無法建立遊戲文件備份" }
        target.copyRecursively(File(backup, "original"), overwrite = true)
        // Publish only after the original is fully backed up.
        File(backup, "language").writeText(language)
        try {
            check(next.deleteRecursively())
            target.copyRecursively(next, overwrite = true)
            var count = 0
            var total = 0L
            ZipInputStream(archive.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "原生漢化 ZIP 不應包含目錄項目" }
                    val parts = entry.name.split('/')
                    require(parts.all { it.isNotBlank() && it !in setOf(".", "..") && '\\' !in it }) { "漢化包包含不安全路徑" }
                    require(parts.last().startsWith("${language.uppercase()}_") && parts.last().endsWith(".json", true)) { "漢化檔名與語言不符" }
                    require(++count <= 30_000) { "漢化文件數過多" }
                    val file = File(next, entry.name).canonicalFile
                    require(file.path.startsWith(next.canonicalPath + File.separator))
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            total += n; require(total <= 512L * 1024 * 1024) { "漢化文件過大" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
            require(count > 0) { "漢化包沒有 JSON 文件" }
            swap(next, target)
        } catch (error: Exception) {
            runCatching { restore() }.onFailure { error.addSuppressed(it) }
            throw error
        }
    }
}
