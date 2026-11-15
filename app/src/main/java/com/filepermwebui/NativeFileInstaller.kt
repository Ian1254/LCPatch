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
        if (File(backup, "mode").takeIf(File::isFile)?.readText()?.trim() == "overlay") {
            val lines = File(backup, "manifest").readLines().filter(String::isNotBlank)
            for (line in lines) {
                val fields = line.split('\t')
                require(fields.size == 2 && fields[1] in setOf("original", "added")) {
                    "無法辨識原生文件備份清單"
                }
                val destination = checkedFile(target, fields[0])
                if (fields[1] == "original") {
                    val source = checkedFile(File(backup, "original"), fields[0])
                    check(source.isFile) { "原始文件備份缺失：${fields[0]}" }
                    atomicCopy(source, destination)
                } else if (destination.exists()) {
                    check(destination.isFile && destination.delete()) { "無法清除新加入的漢化文件" }
                }
            }
        } else {
            // Compatibility with the whole-directory backup produced by 1.2.3.
            val original = File(backup, "original")
            if (original.isDirectory) {
                check(next.deleteRecursively()) { "無法清除暫存目錄" }
                original.copyRecursively(next, overwrite = true)
                swapLegacy(next, target)
            }
        }
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
        check(!hasBackup()) { "已有原生漢化備份；請先手動停用舊漢化再重新套用" }
        // An interrupted backup without its final marker cannot have modified any game file.
        if (backup.exists()) check(backup.deleteRecursively()) { "無法清理未完成的備份" }
        check(stage.deleteRecursively()) { "無法清理暫存檔案" }
        check(stage.mkdirs()) { "無法準備漢化暫存目錄" }

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
            check(backup.mkdirs()) { "無法建立原始文件備份" }
            // Keep a full baseline for safe downgrades to 1.2.3, whose restore
            // implementation replaces the directory using backup/original.
            val originalDirectory = File(backup, "original")
            check(target.copyRecursively(originalDirectory, overwrite = true)) { "無法完整備份原始語言目錄" }
            val manifest = ArrayList<String>(paths.size)
            for (relative in paths) {
                val destination = checkedFile(target, relative)
                require(!destination.exists() || destination.isFile) { "語言資源路徑不是文件" }
                manifest += "$relative\t${if (destination.isFile) "original" else "added"}"
            }
            File(backup, "manifest").writeText(manifest.joinToString("\n") + "\n")
            File(backup, "mode").writeText("overlay")
            // Publish the recovery marker only after every original is durably backed up.
            File(backup, "language").writeText(language)
            progress(0, paths.size)
            paths.forEachIndexed { index, relative ->
                atomicCopy(checkedFile(stage, relative), checkedFile(target, relative))
                if ((index + 1) % 10 == 0 || index + 1 == paths.size) {
                    progress(index + 1, paths.size)
                }
            }
        } catch (error: Exception) {
            if (hasBackup()) {
                runCatching { restore() }.onFailure { error.addSuppressed(it) }
            } else {
                backup.deleteRecursively()
            }
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }
}
