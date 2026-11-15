package com.lcpatch

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** App-owned packs; replacing a pack preserves the previous copy on failure. */
internal class LocalPackStorage(private val root: File) {
    fun list(): List<File> = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }.orEmpty()

    fun delete(path: String) {
        val target=validatedPackPath(root,path)
        if(!java.nio.file.Files.exists(target.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        java.nio.file.Files.walkFileTree(target.toPath(),object: java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path,attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(directory: java.nio.file.Path,error: java.io.IOException?): java.nio.file.FileVisitResult {
                if(error!=null) throw error
                java.nio.file.Files.delete(directory)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        check(!target.exists()) {"無法刪除漢化包"}
    }

    fun replace(source: File, name: String): File {
        require(name.isNotBlank() && name !in setOf(".", "..") && '/' !in name && '\\' !in name)
        require(root.mkdirs() || root.isDirectory)
        val target = File(root, name)
        val next = File(root, ".$name.next")
        val old = File(root, ".$name.old")
        next.deleteRecursively()
        source.copyRecursively(next, overwrite = true)
        old.deleteRecursively()
        if (target.exists()) check(target.renameTo(old)) { "無法保留原有漢化" }
        if (!next.renameTo(target)) {
            if (old.exists()) check(old.renameTo(target)) { "無法還原原有漢化" }
            error("無法儲存漢化")
        }
        old.deleteRecursively()
        return target
    }
}

internal fun zipDirectory(directory: File, output: OutputStream) {
    ZipOutputStream(output.buffered()).use { zip ->
        directory.walkTopDown().filter(File::isFile).forEach { file ->
            zip.putNextEntry(ZipEntry(file.relativeTo(directory).invariantSeparatorsPath))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** Only direct, non-hidden pack directories may be deleted; reject aliases and escapes. */
internal fun validatedPackPath(root: File,path: String): File {
    val base=root.canonicalFile
    val candidate=File(path).absoluteFile
    require(candidate.name.isNotBlank() && !candidate.name.startsWith(".")) {"無效的漢化包路徑"}
    require(candidate.parentFile==root.absoluteFile && candidate.parentFile?.canonicalFile==base && candidate.canonicalFile==File(base,candidate.name)) {"漢化包路徑超出下載目錄"}
    return candidate
}
