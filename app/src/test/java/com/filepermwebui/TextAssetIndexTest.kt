package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
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

class RuntimeTextAssetIndexRepairTest {
    private fun snapshot(paths: String, truncated: Boolean = false) = RootCommandResult(true, "1:2:3\n$paths$TEXT_ASSET_INVENTORY_END", 0, truncated)

    @Test fun missingCommandTrailerRejectsAnOtherwiseCompleteFilePrefix() {
        val temporary = Files.createTempDirectory("index-prefix").toFile()
        var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                repairRuntimeTextAssetIndex("/runtime", temporary) {
                    calls++
                    RootCommandResult(true, "1:2:3\n./JP_LoginUIText.json\u0000", 0)
                }
            }
            assertEquals(1, calls)
        } finally { temporary.deleteRecursively() }
    }

    @Test fun truncatedInventoryNeverPublishesAPartialIndex() {
        var calls = 0
        val temporary = Files.createTempDirectory("index-bounded").toFile()
        try {
            assertThrows(IllegalStateException::class.java) {
                repairRuntimeTextAssetIndex("/runtime", temporary) { calls++; snapshot("./JP_LoginUIText.json\u0000", true) }
            }
            assertEquals(1, calls)
            assertTrue(temporary.listFiles()!!.isEmpty())
        } finally { temporary.deleteRecursively() }
    }

    @Test fun invalidOrIncompleteInventoryNeverPublishes() {
        listOf("./../JP_Login.json\u0000", "./JP_Login.json", "./JP_Line\nBreak.json\u0000").forEach { paths ->
            var calls = 0
            val temporary = Files.createTempDirectory("index-paths").toFile()
            try {
                assertThrows(Exception::class.java) {
                    repairRuntimeTextAssetIndex("/runtime", temporary) { calls++; snapshot(paths) }
                }
                assertEquals(1, calls)
            } finally { temporary.deleteRecursively() }
        }
    }

    @Test fun bilingualCollisionsAreExcludedAndFileCountIsBounded() {
        val index = buildTextAssetIndex(sequenceOf("JP_Login.json", "KR_Login.json", "a/JP_Unique.json"))
        assertFalse(index.text.contains("Login\t"))
        assertTrue(index.text.contains("Unique\ta/JP_Unique.json\n"))
        assertThrows(IllegalArgumentException::class.java) {
            buildTextAssetIndex((0..TEXT_ASSET_FILE_LIMIT).asSequence().map { "JP_$it.json" })
        }
    }

    @Test fun nativeByteLimitAndUnicodePathLengthAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) {
            buildTextAssetIndex(sequenceOf("JP_" + "漢".repeat(400) + ".json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildTextAssetIndex((0..5000).asSequence().map { "JP_" + "$it" + "a".repeat(500) + ".json" })
        }
    }

    private fun runShell(command: String): RootCommandResult {
        val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return RootCommandResult(process.waitFor() == 0, output, process.exitValue())
    }

    @Test fun installedPackIsRepairedIdempotentlyAndFailedPublicationKeepsOriginal() {
        assumeTrue(File("/bin/sh").canExecute())
        val base = Files.createTempDirectory("runtime-index").toFile()
        val runtime = File(base, "disabled pack's cache").apply { mkdirs() }
        val staging = File(base, "staging")
        val destination = File(runtime, TEXT_ASSET_INDEX_NAME)
        try {
            File(runtime, "JP_LoginUIText.json").writeText("unchanged-json")
            val first = repairRuntimeTextAssetIndex(runtime.path, staging, ::runShell)!!
            assertTrue(first.changed)
            val original = destination.readText()
            Files.setPosixFilePermissions(destination.toPath(), setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
            assertFalse(repairRuntimeTextAssetIndex(runtime.path, staging, ::runShell)!!.changed)
            assertTrue(Files.getPosixFilePermissions(destination.toPath()).contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ))
            File(runtime, "JP_LoadingUIText.json").writeText("untouched-json")
            listOf("chmod 0644" to "false; chmod 0644", "mv -f" to "mv --lcpatch-invalid-option").forEach { (before, after) ->
                assertThrows(IllegalStateException::class.java) {
                    repairRuntimeTextAssetIndex(runtime.path, staging) { runShell(it.replace(before, after)) }
                }
                assertEquals(original, destination.readText())
                assertFalse(runtime.listFiles()!!.any { it.extension == "tmp" })
            }
            assertEquals("unchanged-json", File(runtime, "JP_LoginUIText.json").readText())
            assertTrue(runtime.isDirectory)
            val unrelated = File(base, "unrelated.tsv").apply { writeText(original) }
            assertTrue(destination.delete())
            Files.createSymbolicLink(destination.toPath(), unrelated.toPath())
            assertTrue(repairRuntimeTextAssetIndex(runtime.path, staging, ::runShell)!!.changed)
            assertFalse(Files.isSymbolicLink(destination.toPath()))
            assertEquals(original, unrelated.readText())
        } finally { base.deleteRecursively() }
    }

    @Test fun concurrentPackSwapRejectsOldInventory() {
        assumeTrue(File("/bin/sh").canExecute())
        val base = Files.createTempDirectory("runtime-swap").toFile()
        val runtime = File(base, "cn").apply { mkdirs() }
        File(runtime, "JP_Old.json").writeText("{}")
        var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                repairRuntimeTextAssetIndex(runtime.path, File(base, "staging")) { command ->
                    if (++calls == 2) {
                        assertTrue(runtime.renameTo(File(base, "old")))
                        runtime.mkdirs()
                        File(runtime, "JP_New.json").writeText("{}")
                    }
                    runShell(command)
                }
            }
            assertFalse(File(runtime, TEXT_ASSET_INDEX_NAME).exists())
        } finally { base.deleteRecursively() }
    }
}
