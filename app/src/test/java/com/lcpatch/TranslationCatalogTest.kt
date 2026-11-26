package com.lcpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCatalogTest {
    @Test fun recognizesOnlyTheOldRootSimplifiedPack() {
        assertTrue(isLegacyRootSimplified("Root 简体汉化"))
        assertTrue(isLegacyRootSimplified("Root简体汉化"))
        assertFalse(isLegacyRootSimplified("Root 繁體漢化"))
        assertFalse(isLegacyRootSimplified("My Root 简体汉化"))
    }

    @Test fun acceptsXpSectionsAndExcludesNormalMode() {
        val source = """
            [汉化-XP]
            Root简体汉化 | 作者甲 | 简体中文 | https://example.com/a.zip
            [漢化-普通]
            繁體日語 | 作者乙 | 繁體中文 | https://example.com/b.zip
            [漢化-XP]
            Root繁體漢化 | 作者丁 | 繁體中文 | https://example.com/d.zip
            [模组]
            不應出現 | 作者丙 | 模組 | https://example.com/c.zip
        """.trimIndent()

        val result = parseTranslationCatalog(source)
        assertEquals(listOf("Root简体汉化", "Root繁體漢化"), result.map { it.name })
    }

    @Test fun normalizesNestedAndroidLocalizationFolders() {
        assertEquals("Localize/jp/JP_Test.json", localizeRelativePath("Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/jp/JP_Test.json"))
        assertEquals("Localize/cn/Test.json", localizeRelativePath("Localize/cn/Test.json"))
        assertEquals(
            "Localize/cn/BattleAnnouncerDlg/Announcer_Aengdu_26.json",
            localizeRelativePath("LimbusCompany_Data/Lang/LLC_zh-CN/BattleAnnouncerDlg/Announcer_Aengdu_26.json")
        )
        assertNull(localizeRelativePath("BepInEx/plugins/plugin.dll"))
    }

    @Test fun normalizesLctaTextOnly() {
        assertEquals("Localize/cn/StoryData/S1000.json", localizeRelativePath("LLc-CN-LCTA/StoryData/S1000.json"))
        assertEquals("Localize/cn/Skills.json", localizeRelativePath("LLc-CN-LCTA/Skills.json"))
        assertNull(localizeRelativePath("LLc-CN-LCTA/Info/version.json"))
        assertNull(localizeRelativePath("LLc-CN-LCTA/install.exe"))
    }

    @Test fun lctaReleaseUsesExactZipAndDigest() {
        val tag = "2026111901"
        val digest = "sha256:" + "a".repeat(64)
        val url = "https://github.com/HZBHZB1234/LCTA_auto_update/releases/download/$tag/LLc-CN-LCTA-$tag.zip"
        val asset = org.json.JSONObject().put("name", "LLc-CN-LCTA-$tag.zip")
            .put("browser_download_url", url).put("size", 5994399).put("digest", digest)
        val release = org.json.JSONObject().put("tag_name", tag).put("assets", org.json.JSONArray().put(asset))
        val entry = parseLctaRelease(release)
        assertEquals(tag, entry.version)
        assertEquals(url, entry.url)
        assertEquals(digest, entry.sourceDigest)
        assertFalse(isNewerPackVersion(tag, tag))
        assertTrue(isNewerPackVersion("2026111902", tag))
        asset.put("digest", "")
        assertTrue(runCatching { parseLctaRelease(release) }.isFailure)
    }

    @Test fun correctsLanguageFilePrefixes() {
        assertEquals("KR_Story.json", correctedLanguageFileName("JP_Story.json", "KR"))
        assertEquals("EN_Test.json", correctedLanguageFileName("kr_Test.json", "en"))
        assertEquals("JP_Common.json", correctedLanguageFileName("Common.json", "JP"))
        assertEquals("LICENSE", correctedLanguageFileName("LICENSE", "JP"))
    }

    @Test fun systemBackFollowsMenuHierarchy() {
        assertEquals(0, parentPage(2)) // logs -> overview
        assertEquals(1, parentPage(3)) // about -> settings
        assertEquals(0, parentPage(4)) // download -> overview
        assertEquals(1, parentPage(5)) // onboarding -> settings
        assertEquals(0, parentPage(6)) // downloaded -> overview
        assertEquals(1, parentPage(7)) // display -> settings
        assertEquals(0, parentPage(1)) // settings -> overview
    }
}
