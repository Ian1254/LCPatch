package com.lcpatch

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * A native language overlay: copy only the translated JSON files, never exchange
 * the game's live language directory. A durable per-file baseline supports manual restore.
 */
internal class NativeFileInstaller(private val localize: File) {
    private val backup = File(localize, ".lcpatch-native-backup")
    private val stage = File(localize, ".lcpatch-native-stage")
    private val next = File(localize, ".lcpatch-native-next")
    private val old = File(localize, ".lcpatch-native-old")

    fun hasBackup(): Boolean = backup.isDirectory && File(backup, "language").isFile

    private fun languageDirectory(language: String): File {
        require(language in setOf("jp", "kr")) { "只支援 jp／kr 原生語言目錄" }
        return File(localize, language)
    }

    private fun checkedFile(directory: File, path: String): File {
        val parts = path.split('/')
        require(parts.all { it.isNotBlank() && it !in setOf(".", "..") && '\\' !in it &&
            it.none { char -> char.code < 32 } }) { "無效的漢化檔案路徑" }
        val root = directory.canonicalFile
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "漢化檔案超出語言目錄" }
        return file
    }

    private fun atomicCopy(source: File, destination: File) {
        require(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true) {
            "無法建立語言檔案目錄"
        }
        val temporary = File(destination.parentFile, ".${destination.name}.lcpatch-writing")
        try {
            FileOutputStream(temporary).use { output ->
                source.inputStream().use { it.copyTo(output, 64 * 1024) }
                output.fd.sync()
            }
            temporary.setReadable(true, false)
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun restore() {
        if (!hasBackup()) return
        val language = File(backup, "language").readText().trim()
        val target = languageDirectory(language)
        // The first successful apply keeps a complete baseline. Restoring the whole
        // directory also removes any files introduced by earlier reapplications.
        // This is only called on explicit user disable or an unsuccessful write.
        val original = File(backup, "original")
        check(original.isDirectory && original.listFiles()?.isNotEmpty() == true) {
            "原始語言目錄備份不完整；為避免資料遺失，未進行還原"
        }
        check(next.deleteRecursively()) { "無法清除還原暫存目錄" }
        check(original.copyRecursively(next, overwrite = true)) { "無法準備原始文件還原" }
        swapLegacy(next, target)
        check(backup.deleteRecursively()) { "原始文件已還原，但無法清理備份" }
    }

    private fun swapLegacy(source: File, target: File) {
        if (!target.exists() && old.exists()) check(old.renameTo(target))
        check(old.deleteRecursively())
        if (target.exists()) check(target.renameTo(old))
        if (!source.renameTo(target)) {
            if (old.exists()) check(old.renameTo(target))
            error("無法還原舊版原始語言目錄")
        }
        check(old.deleteRecursively())
    }

    fun applyOverlay(archive: InputStream, language: String, progress: (Int, Int) -> Unit = { _, _ -> }) {
        val target = languageDirectory(language)
        require(target.isDirectory && target.listFiles()?.isNotEmpty() == true) {
            "請先在遊戲下載 ${language.uppercase()} 語言資源"
        }
        val existingBackup = hasBackup()
        if (existingBackup) {
            val previousLanguage = File(backup, "language").readText().trim()
            check(previousLanguage == language) {
                "備份為 ${previousLanguage.uppercase()}；請先手動停用舊漢化才能切換語言"
            }
            val original = File(backup, "original")
            check(original.isDirectory && original.listFiles()?.isNotEmpty() == true) {
                "原始語言備份不完整；為避免覆蓋原始文件，已停止套用"
            }
        } else if (backup.exists()) {
            // No recovery marker means the previous operation did not begin writing.
            check(backup.deleteRecursively()) { "無法清理未完成的備份" }
        }
        check(stage.deleteRecursively()) { "無法清理暫存檔案" }
        check(stage.mkdirs()) { "無法準備漢化暫存目錄" }

        var startedWrites = false
        try {
            val paths = linkedSetOf<String>()
            var totalBytes = 0L
            ZipInputStream(archive.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "原生漢化 ZIP 不應包含目錄項目" }
                    val relative = entry.name
                    val filename = relative.substringAfterLast('/')
                    require(filename.startsWith("${language.uppercase()}_") &&
                        filename.endsWith(".json", ignoreCase = true)) { "漢化檔名與語言不符" }
                    require(paths.add(relative)) { "漢化包存在重複檔案" }
                    require(paths.size <= 30_000) { "漢化文件數過多" }
                    val output = checkedFile(stage, relative)
                    check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                    output.outputStream().use { destination ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 512L * 1024 * 1024) { "漢化文件過大" }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
            }
            require(paths.isNotEmpty()) { "漢化包沒有 JSON 文件" }
            for (relative in paths) {
                val destination = checkedFile(target, relative)
                require(!destination.exists() || destination.isFile) { "語言資源路徑不是文件" }
            }
            if (!existingBackup) {
                check(backup.mkdirs()) { "無法建立原始文件備份" }
                // Do not overwrite this baseline on any later reapply.
                val originalDirectory = File(backup, "original")
                check(target.copyRecursively(originalDirectory, overwrite = true)) { "無法完整備份原始語言目錄" }
                File(backup, "mode").writeText("overlay")
                File(backup, "language").writeText(language)
            }
            progress(0, paths.size)
            paths.forEachIndexed { index, relative ->
                startedWrites = true
                atomicCopy(checkedFile(stage, relative), checkedFile(target, relative))
                if ((index + 1) % 10 == 0 || index + 1 == paths.size) {
                    progress(index + 1, paths.size)
                }
            }
        } catch (error: Exception) {
            if (startedWrites && hasBackup()) {
                runCatching { restore() }.onFailure { error.addSuppressed(it) }
            } else if (!existingBackup && backup.exists()) {
                check(backup.deleteRecursively()) { "無法清理套用失敗的暫存備份" }
            }
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }
}
