package com.lcpatch

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** App-owned packs; replacing a pack preserves the previous copy on failure. */
internal class LocalPackStorage(private val root: File) {
    fun list(): List<File> = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }.orEmpty()

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
