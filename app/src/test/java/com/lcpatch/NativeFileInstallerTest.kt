package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeFileInstallerTest {
    private fun archive(vararg items: Pair<String, String>): ByteArray {
        val stream = ByteArrayOutputStream()
        ZipOutputStream(stream).use { zip ->
            for ((name, content) in items) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return stream.toByteArray()
    }

    @Test fun overlayTouchesOnlySelectedFilesAndRestoresOnManualRequest() {
        val root = Files.createTempDirectory("lcpatch-overlay").toFile()
        try {
            val japanese = File(root, "jp").apply { mkdirs() }
            val original = File(japanese, "JP_MainUIText.json").apply { writeText("原文") }
            val untouched = File(japanese, "JP_BattleKeywords.json").apply { writeText("維持原狀") }
            val added = File(japanese, "JP_New.json")
            val installer = NativeFileInstaller(root)
            val progress = mutableListOf<Pair<Int, Int>>()
            archive("JP_MainUIText.json" to "翻譯", "JP_New.json" to "新增").inputStream().use {
                installer.applyOverlay(it, "jp") { done, total -> progress += done to total }
            }
            assertEquals("翻譯", original.readText())
            assertEquals("維持原狀", untouched.readText())
            assertEquals("新增", added.readText())
            assertTrue(installer.hasBackup())
            assertEquals(listOf(0 to 2, 2 to 2), progress)
            // No automatic restore occurs when the game process ends.
            assertEquals("翻譯", original.readText())

            installer.restore()
            assertEquals("原文", original.readText())
            assertEquals("維持原狀", untouched.readText())
            assertFalse(added.exists())
            assertFalse(installer.hasBackup())
            installer.restore()
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidPackLeavesOriginalFileUnmodified() {
        val root = Files.createTempDirectory("lcpatch-overlay-invalid").toFile()
        try {
            val original = File(root, "jp/JP_Test.json")
            original.parentFile!!.mkdirs()
            original.writeText("原文")
            val installer = NativeFileInstaller(root)
            assertThrows(IllegalArgumentException::class.java) {
                installer.applyOverlay(archive("KR_Test.json" to "不符").inputStream(), "jp")
            }
            assertEquals("原文", original.readText())
            assertFalse(installer.hasBackup())
        } finally { root.deleteRecursively() }
    }

    @Test fun previousVersionWholeDirectoryBackupCanStillBeRestored() {
        val root = Files.createTempDirectory("lcpatch-legacy-backup").toFile()
        try {
            File(root, "jp").mkdirs()
            File(root, "jp/JP_Test.json").writeText("覆蓋版")
            val backup = File(root, ".lcpatch-native-backup")
            File(backup, "original").mkdirs()
            File(backup, "original/JP_Test.json").writeText("原版")
            File(backup, "language").writeText("jp")
            val installer = NativeFileInstaller(root)
            installer.restore()
            assertEquals("原版", File(root, "jp/JP_Test.json").readText())
            assertFalse(installer.hasBackup())
        } finally { root.deleteRecursively() }
    }
}
