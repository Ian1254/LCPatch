package com.lcpatch

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import com.android.tools.screenshot.PreviewTest
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop

class PcTranslationSupplementTest {
    @Test fun upstreamLatinNamesAreAlignedRatherThanReportedAsMissingTranslation() {
        val target = JSONObject("""{"dataList":[{"id":1,"name":"E.G.O"}]}""")
        val report = CoverageReport()
        supplementJson(target, JSONObject(target.toString()), report)
        assertEquals(1, report.alignedFields)
        assertEquals(0, report.unresolvedFields)
        assertEquals(0, report.filledFields)
    }
    @Test fun preservesMatchingPlaceholdersAndMarkupWhileFillingDisplayFields() {
        val target = JSONObject("""{"dataList":[{"id":1,"dlg":"Use {0} <b>now</b>","simpleDesc":"未翻譯の文章","messageDesc":"Download {1}"}]}""")
        val reference = JSONObject("""{"dataList":[{"id":1,"dlg":"立即使用 {0} <b>道具</b>","simpleDesc":"簡短中文說明","messageDesc":"下載 {1}"}]}""")
        val report = CoverageReport()
        supplementJson(target, reference, report)
        val row = target.getJSONArray("dataList").getJSONObject(0)
        assertEquals("立即使用 {0} <b>道具</b>", row.getString("dlg"))
        assertEquals("簡短中文說明", row.getString("simpleDesc"))
        assertEquals("下載 {1}", row.getString("messageDesc"))
        assertEquals(3, report.filledFields)
        assertEquals(0, report.placeholderConflicts)
    }
    @Test fun addsMissingIdsOnlyInIdAddressedResourceLists() {
        val target = JSONObject("""{"dataList":[{"id":"a","content":"既有中文"}],"other":[{"id":"a","content":"既有中文"}]}""")
        val reference = JSONObject("""{"dataList":[{"id":"a","content":"原譯"},{"id":"b","content":"新增譯文"}],"other":[{"id":"b","content":"不應加入"}]}""")
        val report = CoverageReport()
        supplementJson(target, reference, report)
        assertEquals(2, target.getJSONArray("dataList").length())
        assertEquals(1, target.getJSONArray("other").length())
        assertEquals(1, report.addedRecords)
    }
    @Test fun matchesIdsPreservesChineseAndNeverReplacesByPosition() {
        val target = JSONObject("""{"dataList":[{"id":"b","content":"Already English"},{"id":"a","content":"既有譯文"},{"id":"c","content":"日本語の文章"}]}""")
        val pc = JSONObject("""{"dataList":[{"id":"a","content":"不同中文"},{"id":"b","content":"補齊中文"}]}""")
        val report = CoverageReport()
        supplementJson(target, pc, report)
        val rows = target.getJSONArray("dataList")
        assertEquals("補齊中文", rows.getJSONObject(0).getString("content"))
        assertEquals("既有譯文", rows.getJSONObject(1).getString("content"))
        assertEquals("日本語の文章", rows.getJSONObject(2).getString("content"))
        assertEquals(1, report.filledFields)
        assertEquals(1, report.unresolvedFields)
    }
    @Test fun blocksPlaceholderChangesAndAmbiguousIds() {
        val target = JSONObject("""{"dataList":[{"id":1,"content":"Download {0}"},{"id":2,"content":"Other"}]}""")
        val pc = JSONObject("""{"dataList":[{"id":1,"content":"下載 {1}"},{"id":2,"content":"甲"},{"id":2,"content":"乙"}]}""")
        val report = CoverageReport()
        supplementJson(target, pc, report)
        assertEquals("Download {0}", target.getJSONArray("dataList").getJSONObject(0).getString("content"))
        assertEquals(0, report.filledFields)
        assertEquals(1, report.placeholderConflicts)
    }
}

/** Optional audit against downloaded public release ZIPs; no game/device required. */
class PcPackArchiveIntegrationTest {
    @Test fun realPcAndBuilderArchivesRemainIdenticalAndRepairDeliberateGaps() {
        val fixturePath = System.getenv("LCPATCH_PACK_FIXTURES")
        assumeTrue("Set LCPATCH_PACK_FIXTURES to a directory with official.zip and builder.zip", fixturePath != null)
        val fixtures = File(fixturePath!!)
        assumeTrue(File(fixtures, "official.zip").isFile && File(fixtures, "builder.zip").isFile)
        val temporary = Files.createTempDirectory("lcpatch-real-pc").toFile()
        try {
            val reference = File(temporary, "reference").apply { mkdirs() }
            val target = File(temporary, "target").apply { mkdirs() }
            unpackJson(File(fixtures, "official.zip"), "LimbusCompany_Data/Lang/LLC_zh-CN/", reference)
            unpackJson(File(fixtures, "builder.zip"), "Localize/jp/", target)
            val complete = supplementTranslationDirectory(target, reference, "JP", { it }, { it })
            assertEquals(0, complete.addedFiles)
            assertEquals(0, complete.addedRecords)
            assertEquals(0, complete.filledFields)

            val login = File(target, "JP_LoginUIText.json")
            val loginJson = JSONObject(login.readText())
            val rows = loginJson.getJSONArray("dataList")
            val accountIndex = (0 until rows.length()).single { rows.getJSONObject(it).getString("id") == "switch_account" }
            rows.remove(accountIndex)
            login.writeText(loginJson.toString())
            File(target, "JP_FileDownloadDesc.json").delete()
            val hints = File(target, "JP_BattleHint.json")
            val hintJson = JSONObject(hints.readText())
            hintJson.getJSONArray("dataList").getJSONObject(0).put("content", "未翻譯の文章")
            hints.writeText(hintJson.toString())

            val repaired = supplementTranslationDirectory(target, reference, "JP", { it }, { it })
            assertEquals(1, repaired.addedFiles)
            assertEquals(1, repaired.addedRecords)
            assertEquals(1, repaired.filledFields)
            val restoredRows = JSONObject(login.readText()).getJSONArray("dataList")
            assertTrue((0 until restoredRows.length()).any { restoredRows.getJSONObject(it).getString("id") == "switch_account" })
            val pcHint = JSONObject(File(reference, "BattleHint.json").readText()).getJSONArray("dataList").getJSONObject(0).getString("content")
            assertEquals(pcHint, JSONObject(hints.readText()).getJSONArray("dataList").getJSONObject(0).getString("content"))
            writeTextAssetIndex(target)
            val index = File(target, ".lcpatch-text-assets.tsv").readText()
            listOf("LoginUIText", "FileDownloadDesc", "BattleHint", "BattleHint_AbnorBattle", "BattleHint_NormalBattle").forEach {
                assertTrue(index.contains("$it\tJP_$it.json\n"))
            }
            println("REAL_ARCHIVES: complete=$complete repaired=$repaired; key resource allowlist entries present")
        } finally { temporary.deleteRecursively() }
    }

    private fun unpackJson(archive: File, prefix: String, destination: File) {
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".json") }.forEach { entry ->
                val relative = entry.name.removePrefix(prefix)
                if (relative.startsWith("Info/")) return@forEach
                val output = File(destination, relative).canonicalFile
                require(output.path.startsWith(destination.canonicalPath + File.separator))
                output.parentFile!!.mkdirs()
                zip.getInputStream(entry).use { input -> output.outputStream().use(input::copyTo) }
            }
        }
    }
}

private val sampleOverview = OverviewUiState("零協會漢化（LCPatch 整理版）", "繁體中文", "日本語", true,
    true, true, "1.119.0", false, "已啟用", "已授權", "已連接", true, true, 2383, "PC 翻譯與目前漢化一致")

/** The shell and panels below are the same composables used by MainActivity. */
@Composable private fun ScreenFixture(style: SystemUiStyle, dark: Boolean, page: Int = 2, scrolled: Boolean = false, atEnd: Boolean = false) {
    SystemUiTheme(style, if (dark) "dark" else "light") {
        val backdrop = rememberBarBackdrop()
        // Snapshot the settled state without Layoutlib's initial backward-fill pass.
        val listState = rememberLazyListState()
        val collapse = if(style == SystemUiStyle.HyperOS4 || scrolled || atEnd || listState.firstVisibleItemIndex > 0) 1f else
            (listState.firstVisibleItemScrollOffset / with(LocalDensity.current) { 64.dp.toPx() }).coerceIn(0f,1f)
        SystemCanvas {
            Scaffold(containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { TopLevelProgressiveBar(backdrop, pageTitles = listOf("LCPatch", "日誌", "設定"),
                    pagePosition = page.toFloat(), collapseFraction = collapse) },
                bottomBar = { SystemBottomNavigation(page, listOf("概觀", "日誌", "設定"),
                    listOf(MiuixIcons.Home, Icons.Default.List, MiuixIcons.Settings),
                    true, backdrop = backdrop, onSelected = {}) }
            ) { padding ->
                Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier).background(MiuixTheme.colorScheme.surface)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()).clipToBounds(),
                    contentPadding = PaddingValues(start = systemPageGutter(),
                        end = systemPageGutter(),
                        top = if (style == SystemUiStyle.HyperOS4) 12.dp else 0.dp,
                        bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (style != SystemUiStyle.HyperOS4) item { Spacer(Modifier.height(if(scrolled || atEnd) 0.dp else 64.dp)) }
                    when (page) {
                        0 -> {
                            item { SystemTranslationPanel(sampleOverview, {}, {}, {}) }
                            item { SystemQuickActions({}, {}) }
                            item { SystemSectionLabel("運作狀態") }
                            item { SystemEnvironmentPanel(sampleOverview, {}, {}, {}, "選擇資料夾") }
                            item { Text("遊戲語言請設為「日本語」", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                        }
                        1 -> {
                            item { SystemLogActions(false, {}, {}, {}) }
                            item { SystemCard(insideMargin = PaddingValues(18.dp)) {
                                Text("遊戲閃退診斷", fontSize = 16.sp)
                                Text("分享或儲存時會收集遊戲記錄，方便確認載入與套用狀態。", Modifier.padding(top = 8.dp),
                                    fontSize = 13.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            } }
                            item { SystemSectionLabel("最近事件 · 500 筆") }
                            listOf(
                                LogEvent("2026-11-13T17:36:24.402Z", "ERROR", "APP_ACTION", "套用失敗：PC 翻譯處理未完成，尚未寫入遊戲", ""),
                                LogEvent("2026-11-13T17:36:03.271Z", "ERROR", "translation.install_failed", "無法處理漢化內容，請分享診斷文件查看完整原因。", ""),
                                LogEvent("2026-11-13T17:36:03.264Z", "INFO", "download.completed", "漢化資源已下載完成", ""),
                                LogEvent("2026-11-13T17:35:03.264Z", "INFO", "translation.textasset.index", "已建立內建文字資源索引", "")
                            ).forEach { event -> item { SystemLogEntry(event) } }
                        }
                        else -> {
                            item { SystemSectionLabel("運作環境") }
                            item { SystemCard { SystemDropdownPreference("運作模式", listOf("無 root", "Root ＋ LSPosed"), 1, {}) } }
                            item { SystemSectionLabel("漢化") }
                            item { SystemCard {
                                SystemDropdownPreference("覆蓋語言文件", listOf("日本語", "한국어", "English"), 0, {})
                                SystemArrowPreference("匯入漢化包", value = "ZIP 檔案", onClick = {})
                                SystemSwitchPreference("補齊 PC 版翻譯", "套用時補入缺少的翻譯，保留既有中文", checked = true, onCheckedChange = {})
                                SystemArrowPreference("更換字型", value = "內建中文字型", onClick = {})
                            } }
                            item { SystemSectionLabel("應用程式") }
                            item { SystemCard {
                                SystemArrowPreference("介面與顯示", value = style.label, onClick = {})
                                SystemArrowPreference("診斷文件儲存位置", value = "預設位置", onClick = {})
                                SystemArrowPreference("應用程式更新", onClick = {})
                                SystemArrowPreference("關於", onClick = {})
                            } }
                        }
                    }
                }
                }
            }
        }
    }
}

@PreviewTest @Preview(name = "SoftLightLight", widthDp = 393, heightDp = 852)
@Composable fun SoftLightSettingsLight() = ScreenFixture(SystemUiStyle.HyperOS4, false)
@PreviewTest @Preview(name = "SoftLightDark", widthDp = 393, heightDp = 852)
@Composable fun SoftLightSettingsDark() = ScreenFixture(SystemUiStyle.HyperOS4, true)
@PreviewTest @Preview(name = "AcrylicLight", widthDp = 393, heightDp = 852)
@Composable fun AcrylicSettingsLight() = ScreenFixture(SystemUiStyle.ColorOS17, false)
@PreviewTest @Preview(name = "AcrylicDark", widthDp = 393, heightDp = 852)
@Composable fun AcrylicSettingsDark() = ScreenFixture(SystemUiStyle.ColorOS17, true)
@PreviewTest @Preview(name = "SoftLightOverview", widthDp = 393, heightDp = 852)
@Composable fun SoftLightOverview() = ScreenFixture(SystemUiStyle.HyperOS4, false, 0)
@PreviewTest @Preview(name = "AcrylicOverview", widthDp = 393, heightDp = 852)
@Composable fun AcrylicOverview() = ScreenFixture(SystemUiStyle.ColorOS17, true, 0)
@PreviewTest @Preview(name = "SoftLightLogs", widthDp = 393, heightDp = 852)
@Composable fun SoftLightLogs() = ScreenFixture(SystemUiStyle.HyperOS4, false, 1)
@PreviewTest @Preview(name = "AcrylicLogsScrolled", widthDp = 393, heightDp = 852)
@Composable fun AcrylicLogsScrolled() = ScreenFixture(SystemUiStyle.ColorOS17, true, 1, true)
@PreviewTest @Preview(name = "SmallLargeFont", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable fun SmallPhoneLargeFont() = ScreenFixture(SystemUiStyle.HyperOS4, false)
@PreviewTest @Preview(name = "Landscape", widthDp = 852, heightDp = 393)
@Composable fun LandscapeOverview() = ScreenFixture(SystemUiStyle.ColorOS17, false, 0)
@PreviewTest @Preview(name = "StyleChoices", widthDp = 393, heightDp = 350)
@Composable fun StyleChoices() = SystemUiTheme(SystemUiStyle.HyperOS4, "light") { SystemCard { SystemStylePicker(SystemUiStyle.HyperOS4, {}) } }

@Composable private fun SheetFixture(style: SystemUiStyle, dark: Boolean) {
    SystemUiTheme(style, if (dark) "dark" else "light") {
        Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.Bottom) {
            SystemSheetSurface("覆蓋語言文件") {
                listOf("日本語", "한국어").forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(value, Modifier.weight(1f), fontSize = 16.sp)
                        if (index == 0) top.yukonga.miuix.kmp.basic.Icon(SystemGlyph.Check.vector(), null,
                            Modifier.size(22.dp), tint = MiuixTheme.colorScheme.primary)
                    }
                }
                SystemTextButton("取消", {}, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
        }
    }
}
@PreviewTest @Preview(name = "SoftLightSheet", widthDp = 393, heightDp = 420)
@Composable fun SoftLightSelectionPanel() = SheetFixture(SystemUiStyle.HyperOS4, false)
@PreviewTest @Preview(name = "AcrylicSheet", widthDp = 393, heightDp = 420)
@Composable fun AcrylicSelectionPanel() = SheetFixture(SystemUiStyle.ColorOS17, true)

// Released beta.5 component baseline, kept separate from reconstruction fixtures.
@PreviewTest @Preview(name = "PublishedBeta5", widthDp = 393, heightDp = 852)
@Composable fun PublishedBeta5Reference() {
    MiuixTheme {
        Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(56.dp))
            Text("設定", Modifier.padding(horizontal = 12.dp), fontSize = 18.sp)
            Text("運作環境", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(title = "運作模式",
                    items = listOf("無 root", "Root ＋ LSPosed"), selectedIndex = 1, onSelectedIndexChange = {})
            }
            Text("漢化", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.ArrowPreference(title = "覆蓋語言文件", summary = "日本語", onClick = {})
                top.yukonga.miuix.kmp.preference.ArrowPreference(title = "匯入漢化包", onClick = {})
                top.yukonga.miuix.kmp.preference.SwitchPreference(title = "啟用漢化", checked = true, onCheckedChange = {})
                top.yukonga.miuix.kmp.preference.ArrowPreference(title = "更換字型", summary = "內建中文字型", onClick = {})
            }
        }
    }
}
@PreviewTest @Preview(name = "ColorSmallLargeFont", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable fun ColorSmallLargeFont() = ScreenFixture(SystemUiStyle.ColorOS17, false)
@PreviewTest @Preview(name = "HyperLandscape", widthDp = 852, heightDp = 393)
@Composable fun HyperLandscape() = ScreenFixture(SystemUiStyle.HyperOS4, false, 0)

@Composable private fun ActionStatesFixture(style: SystemUiStyle, dark: Boolean) {
    SystemUiTheme(style, if(dark) "dark" else "light") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(systemPageGutter()),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            SystemPageHeading("下載與更新")
            SystemCard(insideMargin=PaddingValues(20.dp)) {
                Text("零協會漢化",fontSize=20.sp)
                Text("下載完成後即可套用漢化。",Modifier.padding(top=8.dp),fontSize=14.sp)
                Spacer(Modifier.height(20.dp))
                SystemProgressActionButton("下載漢化",onClick={})
                Spacer(Modifier.height(12.dp))
                SystemProgressActionButton("正在下載 · 42%",.42f,true,onClick={})
                Spacer(Modifier.height(12.dp))
                SystemProgressActionButton("正在處理漢化內容…",busy=true,onClick={})
                Spacer(Modifier.height(12.dp))
                SystemProgressActionButton("尚未選擇漢化包",enabled=false,onClick={})
            }
            SystemNotice("下載未完成，請檢查連線後重試。",true,{}, {})
            SystemSheetSurface("清除所有日誌？",alert=true) {
                Text("將一併清除 App 日誌與已保存的遊戲診斷快照，此操作無法復原。",Modifier.padding(24.dp),fontSize=14.sp)
                Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SystemSecondaryButton("取消",{},Modifier.weight(1f))
                    SystemButton({},Modifier.weight(1f)) { Text("確認清除",fontSize=16.sp) }
                }
            }
        }
    }
}
@PreviewTest @Preview(name="HyperActionsLight",widthDp=393,heightDp=1000)
@Composable fun HyperActionsLight()=ActionStatesFixture(SystemUiStyle.HyperOS4,false)
@PreviewTest @Preview(name="HyperActionsDark",widthDp=393,heightDp=1000)
@Composable fun HyperActionsDark()=ActionStatesFixture(SystemUiStyle.HyperOS4,true)
@PreviewTest @Preview(name="ColorActionsLight",widthDp=393,heightDp=1000)
@Composable fun ColorActionsLight()=ActionStatesFixture(SystemUiStyle.ColorOS17,false)
@PreviewTest @Preview(name="ColorActionsDark",widthDp=393,heightDp=1000)
@Composable fun ColorActionsDark()=ActionStatesFixture(SystemUiStyle.ColorOS17,true)
@PreviewTest @Preview(name="HyperFont200",widthDp=360,heightDp=1000,fontScale=2f)
@Composable fun HyperFont200()=ScreenFixture(SystemUiStyle.HyperOS4,true)
@PreviewTest @Preview(name="ColorFont200",widthDp=360,heightDp=1000,fontScale=2f)
@Composable fun ColorFont200()=ScreenFixture(SystemUiStyle.ColorOS17,false)
@PreviewTest @Preview(name="ColorAnchoredChoices",widthDp=360,heightDp=360)
@Composable fun ColorAnchoredChoices()=SystemUiTheme(SystemUiStyle.ColorOS17,"light") {
    Column(Modifier.padding(24.dp)) { SystemChoiceMenuSurface(listOf("日本語","한국어","English"),0,{}) }
}
@PreviewTest @Preview(name="ColorRadioAlert",widthDp=393,heightDp=360)
@Composable fun ColorRadioAlert()=SystemUiTheme(SystemUiStyle.ColorOS17,"dark") {
    SystemSheetSurface("覆蓋語言文件",Modifier.padding(16.dp),alert=true) {
        SystemSelectionRows(listOf("日本語","한국어","English"),0,{})
    }
}
@PreviewTest @Preview(name="HyperOpaqueFallback",widthDp=393,heightDp=420)
@Composable fun HyperOpaqueFallback()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    SystemMaterialScope(false) { SystemSheetSurface("漢化內容",Modifier.padding(16.dp)) {
        Text("已準備就緒，可以開始套用漢化。",Modifier.padding(24.dp),fontSize=16.sp)
        SystemProgressActionButton("套用漢化",onClick={})
    } }
}

/** The sampled scene is a sibling, never the ancestor recording the material itself. */
@PreviewTest @Preview(name="HyperSceneGlass",widthDp=393,heightDp=520)
@Composable fun HyperSceneGlass()=SystemUiTheme(SystemUiStyle.HyperOS4,"light") {
    val scene=rememberBarBackdrop()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if(scene!=null) Modifier.layerBackdrop(scene) else Modifier)
            .background(MiuixTheme.colorScheme.surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            repeat(6) { i -> SystemCard(insideMargin=PaddingValues(20.dp)) { Text("背景內容 ${i+1}",fontSize=24.sp); Text("漢化已啟用 · 日本語",fontSize=16.sp) } }
        }
        CompositionLocalProvider(LocalSystemPanelBackdrop provides scene) {
            SystemSheetSurface("漢化詳情",Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp)) {
                Text("目前漢化內容已準備就緒。",Modifier.padding(24.dp),fontSize=16.sp)
                SystemSecondaryButton("關閉",{},Modifier.fillMaxWidth().padding(20.dp))
            }
        }
    }
}

@Composable private fun AppearanceFixture(style: SystemUiStyle,dark: Boolean) = SystemUiTheme(style,if(dark) "dark" else "light") {
    Column(Modifier.fillMaxSize()) {
        if (style==SystemUiStyle.ColorOS17) SystemColorTopBar("介面與顯示") { top.yukonga.miuix.kmp.basic.Icon(SystemGlyph.Back.vector(),"返回",Modifier.size(24.dp)) }
        else top.yukonga.miuix.kmp.basic.SmallTopAppBar(title="介面與顯示",navigationIcon={ top.yukonga.miuix.kmp.basic.Icon(SystemGlyph.Back.vector(),"返回",Modifier.size(24.dp)) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(systemPageGutter())) {
            SystemDisplaySettingsPanel(if(dark) "dark" else "light",style,{},"floating",{},true,{},false,{},{})
        }
    }
}
@PreviewTest @Preview(name="ColorAppearance",widthDp=393,heightDp=852)
@Composable fun ColorAppearance()=AppearanceFixture(SystemUiStyle.ColorOS17,false)
@PreviewTest @Preview(name="HyperAppearance",widthDp=393,heightDp=852)
@Composable fun HyperAppearance()=AppearanceFixture(SystemUiStyle.HyperOS4,true)

@PreviewTest @Preview(name="HyperLastRow",widthDp=393,heightDp=1000)
@Composable fun HyperLastRow()=ScreenFixture(SystemUiStyle.HyperOS4,false,atEnd=true)
@PreviewTest @Preview(name="ColorLastRow",widthDp=393,heightDp=1000)
@Composable fun ColorLastRow()=ScreenFixture(SystemUiStyle.ColorOS17,true,atEnd=true)
@PreviewTest @Preview(name="ColorActionsFont200",widthDp=360,heightDp=1000,fontScale=2f)
@Composable fun ColorActionsFont200()=ActionStatesFixture(SystemUiStyle.ColorOS17,false)
@PreviewTest @Preview(name="Hyper320",widthDp=320,heightDp=720)
@Composable fun Hyper320()=ScreenFixture(SystemUiStyle.HyperOS4,false)
