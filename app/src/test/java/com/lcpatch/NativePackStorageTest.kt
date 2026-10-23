package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class NativePackStorageTest {
    private val jp = OverrideLanguage("jp", "日本語", "JP")

    @Test fun nativeFilesUseTheGameResourcesPathRatherThanTheHookCache() {
        assertEquals("Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/jp", nativeExportPath(jp))
        assertEquals("Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/kr",
            nativeExportPath(OverrideLanguage("kr", "한국어", "KR")))
        assertThrows(IllegalArgumentException::class.java) { nativeExportPath(OverrideLanguage("en", "English", "EN")) }
    }

    @Test fun wrongLanguageAndRootPacksCannotBeLoadedAsNativeJapanese() {
        val root = Files.createTempDirectory("native-format").toFile()
        try {
            File(root, "cn").mkdirs()
            assertThrows(IllegalStateException::class.java) { nativeLanguageSource(root, jp) }
            File(root, "kr").mkdirs()
            assertThrows(IllegalStateException::class.java) { nativeLanguageSource(root, jp) }
            val source = File(root, "jp").apply { mkdirs() }
            assertEquals(source, nativeLanguageSource(root, jp))
        } finally { root.deleteRecursively() }
    }

    @Test fun failedPackPreparationDoesNotEraseThePreviousDownload() {
        val root = Files.createTempDirectory("native-storage").toFile()
        try {
            val store = LocalPackStorage(File(root, "packs"))
            val source = File(root, "source").apply { mkdirs() }
            File(source, "JP_Test.json").writeText("原有漢字")
            val saved = store.replace(source, "pack")
            assertThrows(Exception::class.java) { store.replace(File(root, "missing"), "pack") }
            assertEquals("原有漢字", File(saved, "JP_Test.json").readText())
            assertEquals(listOf(saved), store.list())
            assertThrows(IllegalArgumentException::class.java) { store.replace(source, "..") }
        } finally { root.deleteRecursively() }
    }

    @Test fun exportedZipPreservesJapaneseGlyphsAndGameNativeLayout() {
        val root = Files.createTempDirectory("native-export").toFile()
        try {
            val path = "${nativeExportPath(jp)}/JP_Test.json"
            File(root, path).apply { requireNotNull(parentFile).mkdirs(); writeText("{\"content\":\"戦闘\"}") }
            val bytes = ByteArrayOutputStream()
            zipDirectory(root, bytes)
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                assertEquals(path, zip.nextEntry.name)
                assertEquals("{\"content\":\"戦闘\"}", zip.readBytes().toString(Charsets.UTF_8))
                assertNull(zip.nextEntry)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun namesCannotAddressParentOrHiddenTransactionDirectories() {
        listOf(".", "..", ".next", "...").forEach { assertEquals("translation", safePackName(it)) }
        assertEquals("pack_name", safePackName("pack/name"))
        assertEquals("pack_name", safePackName("pack\\name"))
        assertEquals("繁體日語", safePackName("繁體日語"))
    }
}
