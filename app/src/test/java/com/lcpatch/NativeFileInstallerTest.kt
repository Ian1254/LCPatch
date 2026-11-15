package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeFileInstallerTest {
    private fun archive(vararg items: Pair<String,String>)=ByteArrayOutputStream().also {out ->
        ZipOutputStream(out).use {zip -> items.forEach {(path,text) -> zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray());zip.closeEntry()}}
    }.toByteArray().inputStream()

    @Test fun overlayOnlyWritesRequestedFilesWithoutCreatingAnOriginalBackup() {
        val root=Files.createTempDirectory("overlay-no-restore").toFile()
        try {
            val jp=File(root,"jp").apply {mkdirs()}
            File(jp,"JP_Test.json").writeText("original")
            File(jp,"untouched.bin").writeText("game-data")
            val progress=mutableListOf<Pair<Int,Int>>()
            NativeFileInstaller(root).applyOverlay(archive("JP_Test.json" to "translated","sub/JP_New.json" to "new"),"jp") {n,total ->progress+=n to total}
            assertEquals("translated",File(jp,"JP_Test.json").readText())
            assertEquals("game-data",File(jp,"untouched.bin").readText())
            assertEquals("new",File(jp,"sub/JP_New.json").readText())
            assertEquals(listOf(0 to 2,2 to 2),progress)
            assertFalse(File(root,".lcpatch-native-backup").exists())
        } finally {root.deleteRecursively()}
    }

    @Test fun oldBackupDoesNotBlockReapplyOrLanguageSwitchAndIsNeverRead() {
        val root=Files.createTempDirectory("overlay-legacy").toFile()
        try {
            listOf("jp","kr").forEach {File(root,"$it/${it.uppercase()}_Test.json").apply {parentFile.mkdirs();writeText("original")}}
            File(root,".lcpatch-native-backup/language").apply {parentFile.mkdirs();writeText("invalid-old-marker")}
            val installer=NativeFileInstaller(root)
            installer.applyOverlay(archive("JP_Test.json" to "one"),"jp")
            installer.applyOverlay(archive("JP_Test.json" to "two"),"jp")
            installer.applyOverlay(archive("KR_Test.json" to "three"),"kr")
            assertEquals("two",File(root,"jp/JP_Test.json").readText())
            assertEquals("three",File(root,"kr/KR_Test.json").readText())
            assertEquals("invalid-old-marker",File(root,".lcpatch-native-backup/language").readText())
        } finally {root.deleteRecursively()}
    }

    @Test fun malformedArchiveIsRejectedBeforeAnyGameWrite() {
        val root=Files.createTempDirectory("overlay-invalid").toFile()
        try {
            val file=File(root,"jp/JP_Test.json").apply {parentFile.mkdirs();writeText("original")}
            assertThrows(IllegalArgumentException::class.java) {
                NativeFileInstaller(root).applyOverlay(archive("JP_Test.json" to "changed","../JP_Escape.json" to "bad"),"jp")
            }
            assertEquals("original",file.readText())
            assertFalse(File(root,"JP_Escape.json").exists())
        } finally {root.deleteRecursively()}
    }

    @Test fun failureNeverRestoresOrReplacesTheLiveDirectory() {
        val root=Files.createTempDirectory("overlay-failed").toFile()
        try {
            val file=File(root,"jp/JP_Test.json").apply {parentFile.mkdirs();writeText("original")}
            val marker=File(root,"jp/game-state.bin").apply {writeText("before")}
            assertThrows(IllegalStateException::class.java) {
                NativeFileInstaller(root).applyOverlay(archive("JP_Test.json" to "translated"),"jp") {done,_ ->
                    if(done>0) {marker.writeText("game-updated");error("injected callback failure")}
                }
            }
            assertEquals("translated",file.readText())
            assertEquals("game-updated",marker.readText())
            assertFalse(File(root,".lcpatch-native-stage").exists())
        } finally {root.deleteRecursively()}
    }
}
