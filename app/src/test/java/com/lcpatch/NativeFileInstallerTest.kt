package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeFileInstallerTest {
    private fun archive(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray().inputStream()

    @Test fun installPreservesUnrelatedFilesAndRestoreRemovesAddedFiles() {
        val root = Files.createTempDirectory("native-install").toFile()
        try {
            val jp = File(root, "jp").apply { mkdirs() }
            File(jp, "JP_Test.json").writeText("original")
            File(jp, "unchanged.bin").writeText("untouched")
            val installer = NativeFileInstaller(root)
            installer.apply(archive("JP_Test.json" to "漢字", "Story/JP_New.json" to "new"), "jp")
            assertEquals("漢字", File(jp, "JP_Test.json").readText())
            assertEquals("untouched", File(jp, "unchanged.bin").readText())
            assertTrue(installer.hasBackup())
            installer.restore()
            assertEquals("original", File(jp, "JP_Test.json").readText())
            assertFalse(File(jp, "Story/JP_New.json").exists())
            assertFalse(installer.hasBackup())
        } finally { root.deleteRecursively() }
    }

    @Test fun malformedArchiveRollsBackWithoutLeavingPartialGameWrites() {
        val root = Files.createTempDirectory("native-rollback").toFile()
        try {
            val jp = File(root, "jp").apply { mkdirs() }
            File(jp, "JP_Test.json").writeText("original")
            val installer = NativeFileInstaller(root)
            assertThrows(IllegalArgumentException::class.java) {
                installer.apply(archive("JP_Test.json" to "changed", "../JP_Escape.json" to "bad"), "jp")
            }
            assertEquals("original", File(jp, "JP_Test.json").readText())
            assertFalse(File(root, "JP_Escape.json").exists())
            assertFalse(installer.hasBackup())
        } finally { root.deleteRecursively() }
    }

    @Test fun replacingPackAndLanguageRestoresTheOriginalBaseline() {
        val root = Files.createTempDirectory("native-switch").toFile()
        try {
            val jp = File(root, "jp").apply { mkdirs() }
            val kr = File(root, "kr").apply { mkdirs() }
            File(jp, "JP_Test.json").writeText("Japanese")
            File(kr, "KR_Test.json").writeText("Korean")
            val installer = NativeFileInstaller(root)
            installer.apply(archive("JP_OldPack.json" to "first"), "jp")
            installer.apply(archive("JP_Test.json" to "second"), "jp")
            assertFalse(File(jp, "JP_OldPack.json").exists())
            installer.apply(archive("KR_Test.json" to "third"), "kr")
            assertEquals("Japanese", File(jp, "JP_Test.json").readText())
            assertEquals("third", File(kr, "KR_Test.json").readText())
            installer.restore()
            assertEquals("Korean", File(kr, "KR_Test.json").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun unknownLanguageAndMissingGameResourcesNeverCreateBackup() {
        val root = Files.createTempDirectory("native-invalid").toFile()
        try {
            val installer = NativeFileInstaller(root)
            assertThrows(IllegalArgumentException::class.java) { installer.apply(archive("JP_Test.json" to "text"), "jp") }
            assertThrows(IllegalArgumentException::class.java) { installer.apply(archive("JP_Test.json" to "text"), "../other") }
            assertFalse(installer.hasBackup())
        } finally { root.deleteRecursively() }
    }

    @Test fun explicitRestoreRecoversAnInterruptedDirectorySwap() {
        val root = Files.createTempDirectory("native-interrupted").toFile()
        try {
            val jp = File(root, "jp").apply { mkdirs() }
            File(jp, "JP_Test.json").writeText("original")
            val installer = NativeFileInstaller(root)
            installer.apply(archive("JP_Test.json" to "translated"), "jp")
            assertTrue(jp.renameTo(File(root, ".lcpatch-native-old")))
            installer.restore()
            assertEquals("original", File(jp, "JP_Test.json").readText())
            assertFalse(installer.hasBackup())
            assertFalse(File(root, ".lcpatch-native-old").exists())
        } finally { root.deleteRecursively() }
    }
}
