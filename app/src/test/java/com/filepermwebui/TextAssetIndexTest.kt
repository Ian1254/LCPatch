package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class TextAssetIndexTest {
    @Test fun namesResolveWithoutLanguagePrefixAndAmbiguousBasenamesAreExcluded() {
        val root = Files.createTempDirectory("asset-index").toFile()
        try {
            java.io.File(root, "JP_LoginUIText.json").writeText("{}")
            listOf("a", "b").forEach { java.io.File(root, "$it/JP_Common.json").apply { parentFile!!.mkdirs(); writeText("{}") } }
            writeTextAssetIndex(root)
            val text = java.io.File(root, ".lcpatch-text-assets.tsv").readText()
            assertTrue(text.contains("LoginUIText\tJP_LoginUIText.json\n"))
            assertTrue(text.contains("a/Common\ta/JP_Common.json\n"))
            assertFalse(text.lineSequence().any { it.startsWith("Common\t") })
        } finally { root.deleteRecursively() }
    }
}
