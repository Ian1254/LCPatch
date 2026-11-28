package com.lcpatch

import org.junit.Assert.*
import org.junit.Test

class PackUpdateTest {
    @Test fun downloadedReplacementRemainsPendingUntilTheInstalledVersionMatches() {
        val pack = DownloadedTranslation("零協會漢化", "/packs/llc", version = "2026112801")
        assertEquals(pack, pendingDownloadedUpdate(listOf(pack), pack.path, pack.name, "2026111901"))
        assertNull(pendingDownloadedUpdate(listOf(pack), pack.path, pack.name, "2026112801"))
        assertNull(pendingDownloadedUpdate(listOf(pack), pack.path, pack.name, "2026112901"))
    }

    @Test fun downloadedUpdatesMustMatchTheActivePack() {
        val pack = DownloadedTranslation("零協會漢化", "/packs/llc", version = "2026112801")
        assertNull(pendingDownloadedUpdate(listOf(pack), "/packs/other", pack.name, "2026111901"))
        assertNull(pendingDownloadedUpdate(listOf(pack), null, "OurPlay 神人漢化", "192"))
        assertEquals(pack, pendingDownloadedUpdate(listOf(pack), null, pack.name, "2026111901"))
        assertNull(pendingDownloadedUpdate(listOf(pack), pack.path, pack.name, null))
    }

    @Test fun applyMustReadBackTheExpectedRuntimeVersion() {
        requireAppliedPackVersion("2026112801", "2026112801")
        assertThrows(IllegalArgumentException::class.java) { requireAppliedPackVersion("2026112801", "2026111901") }
        assertThrows(IllegalArgumentException::class.java) { requireAppliedPackVersion("2026112801", null) }
    }

}
