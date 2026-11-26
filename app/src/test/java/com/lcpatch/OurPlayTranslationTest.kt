package com.lcpatch

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OurPlayTranslationTest {
    @Test fun standardAndCommunityUseIndependentCatalogKeysAndRequestVariants() {
        val standard = OurPlayTranslation.Variant.STANDARD
        val community = OurPlayTranslation.Variant.COMMUNITY
        val standardEntry = OurPlayTranslation.entry(standard)
        val communityEntry = OurPlayTranslation.entry(community)
        assertEquals("OurPlay 一般漢化", standardEntry.name)
        assertEquals("OurPlay 神人漢化", communityEntry.name)
        assertTrue(standardEntry.url != communityEntry.url)
        assertTrue(standard.sourceId != community.sourceId)
        assertEquals(standard, OurPlayTranslation.variantFor(standardEntry.copy(version = "200")))
        assertEquals(community, OurPlayTranslation.variantFor(communityEntry.copy(version = "189")))
        assertEquals(null, OurPlayTranslation.variantFor(standardEntry.copy(url = communityEntry.url)))
        assertTrue(OurPlayTranslation.requestForm(standard).contains("&is_unofficial=2&"))
        assertTrue(OurPlayTranslation.requestForm(community).contains("&is_unofficial=3&"))
    }

    @Test fun readsTheCommunityVersionAndRejectsUnexpectedDownloadHosts() {
        val response = """{"code":1,"data":{"versionCode":189,"size":9232641,"md5":"69105abcdf2190f631941c087848fc0e","url":"https://cdnvc.excean.com/xspace/news/community.language"}}"""
        assertEquals("189", OurPlayTranslation.parseAsset(response).version)
        assertThrows(IllegalArgumentException::class.java) {
            OurPlayTranslation.parseAsset(response.replace("cdnvc.excean.com", "cdnvc.excean.com.example.net"))
        }
    }

    @Test fun matchesRowsByIdAndShapeAndLeavesAmbiguousFilesAlone() = runBlocking {
        val root = Files.createTempDirectory("ourplay-overlay").toFile()
        try {
            val reference = File(root, "reference").apply { mkdir() }
            val translated = File(reference, "Example.json").apply {
                writeText("""{"dataList":[{"id":"a","text":"原文"},{"id":"b","text":"保留"}]}""")
            }
            val ambiguous = File(reference, "Ambiguous.json").apply {
                writeText("""{"dataList":[{"id":"x","text":"原文"}]}""")
            }
            val archive = File(root, "community.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                val bundles = listOf(
                    """{"dataList":[{"id":"a","text":"翻譯"}]}""",
                    """{"dataList":[{"id":"b","other":"錯誤分類"}]}""",
                    """{"dataList":[{"id":"x","text":"候選一"}]}""",
                    """{"dataList":[{"id":"x","text":"候選二"}]}"""
                )
                bundles.forEachIndexed { i, text ->
                    zip.putNextEntry(ZipEntry("com.ProjectMoon.LimbusCompany\\${i.toString().padStart(32, '0')}"))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
            assertEquals(1, OurPlayTranslation.overlay(archive, reference) {})
            val rows = JSONObject(translated.readText()).getJSONArray("dataList")
            assertEquals("翻譯", rows.getJSONObject(0).getString("text"))
            assertEquals("保留", rows.getJSONObject(1).getString("text"))
            assertTrue(ambiguous.readText().contains("原文"))
        } finally { root.deleteRecursively() }
    }

    @Test fun matchesThePublicArchivesWhenFixturesAreProvided() = runBlocking {
        val community = System.getenv("LCPATCH_OURPLAY_ZIP")?.let(::File)
        val source = System.getenv("LCPATCH_OURPLAY_REFERENCE_ZIP")?.let(::File)
        assumeTrue(community?.isFile == true && source?.isFile == true)
        val root = Files.createTempDirectory("ourplay-fixtures").toFile()
        try {
            ZipFile(source!!).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    val path = localizeRelativePath(entry.name) ?: return@forEach
                    val file = File(root, path)
                    require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
            }
            val matches = OurPlayTranslation.overlay(community!!, File(root, "Localize")) {}
            println("Matched $matches OurPlay text files")
            assertTrue("OurPlay text coverage: $matches", matches > 1_500)
        } finally { root.deleteRecursively() }
    }
}
