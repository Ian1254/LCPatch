package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

class PackVersionIdentityTest {
    @Test fun androidReleaseUrlProvidesItsActualSourceVersion() {
        assertEquals("v1.119.0",installedPackVersion("""{"schema":1,"url":"https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid/releases/download/v1.119.0/jp.zip"}""",null))
        assertNull(releaseAssetVersion("https://github.com.evil.invalid/a/b/releases/download/v1/a.zip"))
        assertNull(releaseAssetVersion("https://github.com/a/b/releases/download/latest/a.zip"))
        assertNull(releaseAssetVersion("http://github.com/a/b/releases/download/v1/a.zip"))
    }

    @Test fun legacyPackGetsAContentIdentityAndNewContentGetsADifferentIdentity() {
        val root=Files.createTempDirectory("pack-identity").toFile()
        try {
            val file=File(root,"Localize/jp/JP_Test.json").apply {parentFile.mkdirs();writeText("[]")}
            val first=ensurePackIdentity(root)
            assertTrue(first.matches(Regex("sha256:[a-f0-9]{64}")))
            assertEquals(first,ensurePackIdentity(root))
            file.writeText("[1]")
            assertNotEquals(first,ensurePackIdentity(root))
            assertFalse(packVersionLabel(first).contains("未知"))
        } finally {root.deleteRecursively()}
    }

    @Test fun versionManifestAndSourceTagOverrideContentFallback() {
        val root=Files.createTempDirectory("pack-manifest").toFile()
        try {
            File(root,"Localize/jp/JP_Test.json").apply {parentFile.mkdirs();writeText("[]")}
            File(root,"version.json").writeText("""{"translationVersion":"20261116"}""")
            assertEquals("20261116",ensurePackIdentity(root))
            assertEquals("v1.119.0",ensurePackIdentity(root,"https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid/releases/download/v1.119.0/jp.zip","v1.119.0"))
            assertEquals("v1.119.0",readLocalPackVersion(root))
        } finally {root.deleteRecursively()}
    }

    @Test fun archiveManifestCanBeReadWithoutGuessingFromDownloadTime() {
        val temp=Files.createTempFile("pack-version",".zip").toFile()
        try {
            ZipOutputStream(temp.outputStream()).use {zip ->zip.putNextEntry(ZipEntry("manifest.json"));zip.write("""{"version":"1.2.3"}""".toByteArray());zip.closeEntry()}
            assertEquals("1.2.3",archivePackVersion(temp))
        } finally {temp.delete()}
    }

    @Test fun actualAndroidReleaseCanIdentifyLegacyContentWithoutAManifest() {
        val input=System.getenv("LCPATCH_NATIVE_REFERENCE_ZIP")?.let(::File)
        org.junit.Assume.assumeTrue("Optional actual Android release fixture",input?.isFile==true)
        val root=Files.createTempDirectory("actual-native-version").toFile()
        try {
            val records=mutableListOf<Pair<String,String>>()
            ZipFile(input!!).use {zip ->
                zip.entries().asSequence().filter {!it.isDirectory}.forEach {entry ->
                    val relative=localizeRelativePath(entry.name) ?: return@forEach
                    val file=File(root,relative);file.parentFile.mkdirs()
                    zip.getInputStream(entry).use {source ->file.outputStream().use {source.copyTo(it)}}
                    if(file.extension.equals("json",true) && !file.name.startsWith(".")) {
                        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it)}
                        records+=relative to hash
                    }
                }
            }
            assertTrue(records.size>2000)
            assertNull(archivePackVersion(input))
            val reference=contentPackIdentity(records)
            val candidate=ensurePackIdentity(root)
            assertEquals("v1.119.0",matchedSourceVersion(candidate,reference,"v1.119.0"))
            File(root,records.first().first).appendText(" ")
            assertNull(matchedSourceVersion(directoryPackIdentity(root),reference,"v1.119.0"))
        } finally {root.deleteRecursively()}
    }
}
