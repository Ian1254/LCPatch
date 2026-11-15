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
 * the game's live language directory. No original-file backup or restoration is performed.
 */
internal class NativeFileInstaller(private val localize: File) {
    private val stage = File(localize, ".lcpatch-native-stage")

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

    fun applyOverlay(archive: InputStream, language: String, progress: (Int, Int) -> Unit = { _, _ -> }) {
        val target = languageDirectory(language)
        require(target.isDirectory && target.listFiles()?.isNotEmpty() == true) {
            "請先在遊戲下載 ${language.uppercase()} 語言資源"
        }
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
            for (relative in paths) {
                val destination = checkedFile(target, relative)
                require(!destination.exists() || destination.isFile) { "語言資源路徑不是文件" }
            }
            progress(0, paths.size)
            paths.forEachIndexed { index, relative ->
                atomicCopy(checkedFile(stage, relative), checkedFile(target, relative))
                if ((index + 1) % 10 == 0 || index + 1 == paths.size) {
                    progress(index + 1, paths.size)
                }
            }
        } finally {
            stage.deleteRecursively()
        }
    }
}
