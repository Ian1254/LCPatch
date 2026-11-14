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

class AutomaticLlcPreparationTest {
    @Test fun mapsExactModelsPreservesNarrativeTitlesAndRejectsAmbiguousNames() {
        val root=Files.createTempDirectory("lcpatch-speakers").toFile()
        try {
            File(root,"JP_ScenarioModelCodes-AutoCreated.json").writeText("""{"dataList":[{"id":"A","name":"唐吉訶德","nickName":"騎士"},{"id":"B","name":"甲"},{"id":"B","name":"乙"}]}""")
            val story=File(root,"StoryData/JP_Test.json").apply { parentFile.mkdirs() }
            story.writeText("""{"dataList":[{"id":1,"model":"A","content":"既有劇情"},{"id":2,"model":"A","content":"既有文字","teller":"保留譯名","title":"Narration"},{"id":3,"model":"B","content":"不猜測"},{"id":4,"model":"missing","content":"不猜測"},{"id":5,"model":"A","content":"隱藏身份","teller":"？？？","title":"？？？"}]}""")
            val report=completeScenarioSpeakers(root)
            val rows=JSONObject(story.readText()).getJSONArray("dataList")
            assertEquals("唐吉訶德",rows.getJSONObject(0).getString("teller"))
            assertEquals("騎士",rows.getJSONObject(0).getString("title"))
            assertEquals("A",rows.getJSONObject(0).getString("model"))
            assertEquals("既有劇情",rows.getJSONObject(0).getString("content"))
            assertEquals("保留譯名",rows.getJSONObject(1).getString("teller"))
            assertEquals("Narration",rows.getJSONObject(1).getString("title"))
            assertFalse(rows.getJSONObject(2).has("teller"))
            assertFalse(rows.getJSONObject(3).has("title"))
            assertEquals("？？？",rows.getJSONObject(4).getString("teller"))
            assertEquals("？？？",rows.getJSONObject(4).getString("title"))
            assertEquals(2,report.fields); assertEquals(2,report.unresolved)
            val bytes=story.readBytes()
            assertEquals(0,completeScenarioSpeakers(root).fields)
            assertArrayEquals(bytes,story.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun preservesPlaceholderContractsAndLiteralSettingsResourceName() {
        val root=Files.createTempDirectory("lcpatch-speaker-format").toFile()
        try {
            File(root,"ScenarioModelCodes-AutoCreated.json").writeText("""{"dataList":[{"id":"A","name":"角色{1}"}]}""")
            val story=File(root,"StoryData/Test.json").apply { parentFile.mkdirs() }
            story.writeText("""{"dataList":[{"id":1,"model":"A","content":"內容","teller":"NPC{0}"}]}""")
            completeScenarioSpeakers(root)
            assertEquals("NPC{0}",JSONObject(story.readText()).getJSONArray("dataList").getJSONObject(0).getString("teller"))
            assertEquals("JP_kr_settings-ui-donttranslate.json",correctedLanguageFileName("kr_settings-ui-donttranslate.json","JP"))
            assertNull(localizeRelativePath("LimbusCompany_Data/Lang/LLC_zh-CN/Info/versions.json"))
            assertTrue(isOfficialLlcUrl("https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/v1/LimbusLocalize.zip"))
            assertFalse(isOfficialLlcUrl("https://github.com/Ian1254/another/releases/download/v1/a.zip"))
        } finally { root.deleteRecursively() }
    }

    @Test fun officialPackPreparationCoversBuilderWithoutASeparatePreparedRelease() {
        val fixture=System.getenv("LCPATCH_PACK_FIXTURES")
        assumeTrue("Official and builder ZIP fixtures are optional",fixture!=null)
        val dir=File(fixture!!)
        assumeTrue(File(dir,"official.zip").isFile && File(dir,"builder.zip").isFile)
        val temp=Files.createTempDirectory("lcpatch-direct-official").toFile()
        try {
            val reference=File(temp,"reference").apply { mkdirs() }
            val target=File(temp,"prepared").apply { mkdirs() }
            ZipFile(File(dir,"official.zip")).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("LimbusCompany_Data/Lang/LLC_zh-CN/") && it.name.endsWith(".json") }.forEach { entry ->
                    val out=File(reference,entry.name.removePrefix("LimbusCompany_Data/Lang/LLC_zh-CN/")).canonicalFile
                    require(out.path.startsWith(reference.canonicalPath+File.separator))
                    out.parentFile.mkdirs(); zip.getInputStream(entry).use { input -> out.outputStream().use(input::copyTo) }
                }
            }
            val report=supplementTranslationDirectory(target,reference,"JP",{it},{it})
            assertTrue("Should resolve story speaker fields directly",report.speakerFields>0)
            fun compare(expected: Any?,actual: Any?,path: String,key: String="") {
                when(expected) {
                    is JSONObject -> {
                        assertTrue(path,actual is JSONObject)
                        expected.keys().forEach { field ->
                            assertTrue("$path/$field",(actual as JSONObject).has(field))
                            compare(expected.opt(field),actual.opt(field),"$path/$field",field)
                        }
                    }
                    is org.json.JSONArray -> {
                        assertTrue(path,actual is org.json.JSONArray)
                        actual as org.json.JSONArray
                        assertEquals(path,expected.length(),actual.length())
                        for(i in 0 until expected.length()) compare(expected.opt(i),actual.opt(i),"$path/$i",key)
                    }
                    is Number -> {
                        assertTrue(path,actual is Number)
                        assertEquals(path,0,expected.toString().toBigDecimal().compareTo(actual.toString().toBigDecimal()))
                    }
                    else -> if(expected!=actual) {
                        val improvement=key=="teller" && expected is String && actual is String &&
                            !Regex("[\u3400-\u9fff]").containsMatchIn(expected) && Regex("[\u3400-\u9fff]").containsMatchIn(actual)
                        assertTrue("Different existing field: $path",improvement)
                    }
                }
            }
            ZipFile(File(dir,"builder.zip")).use { zip ->
                val files=zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("Localize/jp/") && it.name.endsWith(".json") }.toList()
                assertEquals(files.map { it.name.removePrefix("Localize/jp/") }.toSet(),target.walkTopDown().filter { it.isFile && it.extension=="json" }.map { it.relativeTo(target).invariantSeparatorsPath }.toSet())
                files.forEach { entry ->
                    val expected=zip.getInputStream(entry).bufferedReader().use { org.json.JSONTokener(it.readText()).nextValue() }
                    val actual=org.json.JSONTokener(File(target,entry.name.removePrefix("Localize/jp/")).readText()).nextValue()
                    compare(expected,actual,entry.name)
                }
                println("DIRECT_OFFICIAL: ${files.size} resources cover builder; $report")
            }
            val repeat=supplementTranslationDirectory(target,reference,"JP",{it},{it})
            assertEquals(0,repeat.addedFiles);assertEquals(0,repeat.addedRecords);assertEquals(0,repeat.speakerFields)
        } finally { temp.deleteRecursively() }
    }
}

private val sampleOverview = OverviewUiState("零協會漢化", "繁體中文", "日本語", true,
    true, true, "1.119.0", false, "已啟用", "已授權", "已連接", true, true, 2383, "PC 翻譯與目前漢化一致")

/** The shell and panels below are the same composables used by MainActivity. */
@Composable private fun ScreenFixture(style: SystemUiStyle, dark: Boolean, page: Int = 2, scrolled: Boolean = false, atEnd: Boolean = false, floating: Boolean = true) {
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
                    floating, backdrop = backdrop, onSelected = {}) }
            ) { padding ->
                Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier).background(MiuixTheme.colorScheme.surface)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(bottom=padding.calculateBottomPadding()).clipToBounds(),
                    contentPadding = PaddingValues(start = systemPageGutter(),
                        end = systemPageGutter(),
                        top = padding.calculateTopPadding()+12.dp,
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
                                SystemArrowPreference("更換字型", value = "內建中文字型", onClick = {})
                            } }
                            item { SystemSectionLabel("應用程式") }
                            item { SystemCard {
                                SystemArrowPreference("介面與顯示", summary = "深淺模式、底欄與模糊效果", onClick = {})
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


@PreviewTest @Preview(name="HyperLight",widthDp=393,heightDp=852)
@Composable fun HyperLight()=ScreenFixture(SystemUiStyle.HyperOS4,false)
@PreviewTest @Preview(name="HyperDark",widthDp=393,heightDp=852)
@Composable fun HyperDark()=ScreenFixture(SystemUiStyle.HyperOS4,true)
@PreviewTest @Preview(name="StandardLight",widthDp=393,heightDp=852)
@Composable fun StandardLight()=ScreenFixture(SystemUiStyle.HyperOS4,false,floating=false)
@PreviewTest @Preview(name="StandardDark",widthDp=393,heightDp=852)
@Composable fun StandardDark()=ScreenFixture(SystemUiStyle.HyperOS4,true,floating=false)
@PreviewTest @Preview(name="Overview",widthDp=393,heightDp=852)
@Composable fun HyperOverview()=ScreenFixture(SystemUiStyle.HyperOS4,false,0)
@PreviewTest @Preview(name="Logs",widthDp=393,heightDp=852)
@Composable fun HyperLogs()=ScreenFixture(SystemUiStyle.HyperOS4,true,1)
@PreviewTest @Preview(name="Small",widthDp=320,heightDp=720)
@Composable fun HyperSmall()=ScreenFixture(SystemUiStyle.HyperOS4,false)
@PreviewTest @Preview(name="Font200",widthDp=360,heightDp=1000,fontScale=2f)
@Composable fun HyperFont200()=ScreenFixture(SystemUiStyle.HyperOS4,true)
@PreviewTest @Preview(name="StandardFont200",widthDp=360,heightDp=1000,fontScale=2f)
@Composable fun StandardFont200()=ScreenFixture(SystemUiStyle.HyperOS4,false,floating=false)
@PreviewTest @Preview(name="Landscape",widthDp=852,heightDp=393)
@Composable fun HyperLandscape()=ScreenFixture(SystemUiStyle.HyperOS4,false,0)

@Composable private fun MenuFixture(selected: Int,dark: Boolean) = SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
    Box(Modifier.padding(24.dp)) { SystemChoiceMenuSurface(listOf("跟隨系統","淺色","深色"),selected,{}) }
}
@PreviewTest @Preview(name="FirstSelected",widthDp=393,heightDp=240)
@Composable fun FirstSelected()=MenuFixture(0,false)
@PreviewTest @Preview(name="MiddleSelected",widthDp=393,heightDp=240)
@Composable fun MiddleSelected()=MenuFixture(1,true)
@PreviewTest @Preview(name="LastSelected",widthDp=393,heightDp=240)
@Composable fun LastSelected()=MenuFixture(2,false)

@Composable private fun SheetFixture(expanded: Boolean,dark: Boolean)=SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
        SystemSheetSurface("環境與權限",Modifier.padding(12.dp),expanded=expanded,onToggle={}) {
            Column(Modifier.padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Limbus Company 1.119.0 (492)",fontSize=14.sp)
                Text("模組作用域：已啟用",fontSize=14.sp)
                Text("Root 權限：已授權",fontSize=14.sp)
            }
            SystemTextButton("檢查模組作用域",{},Modifier.fillMaxWidth())
            SystemTextButton("檢查 Root 權限",{},Modifier.fillMaxWidth())
            SystemTextButton("完成",{},Modifier.fillMaxWidth())
        }
    }
}
@PreviewTest @Preview(name="ExpandedSheet",widthDp=393,heightDp=700)
@Composable fun ExpandedSheet()=SheetFixture(true,true)
@PreviewTest @Preview(name="CollapsedSheet",widthDp=393,heightDp=700)
@Composable fun CollapsedSheet()=SheetFixture(false,false)
@PreviewTest @Preview(name="SheetFont200",widthDp=360,heightDp=800,fontScale=2f)
@Composable fun SheetFont200()=SheetFixture(true,true)

@PreviewTest @Preview(name="Actions",widthDp=393,heightDp=900)
@Composable fun ActionStates()=SystemUiTheme(SystemUiStyle.HyperOS4,"light") {
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SystemProgressActionButton("下載漢化",onClick={})
        SystemProgressActionButton("正在下載 · 42%",.42f,true,onClick={})
        SystemProgressActionButton("正在整理完整翻譯…",busy=true,onClick={})
        SystemProgressActionButton("尚未選擇漢化包",enabled=false,onClick={})
        SystemNotice("下載未完成，請稍後重試。",true,{}, {})
        SystemSheetSurface("清除日誌？",alert=true) {
            Text("這會清除已儲存的診斷記錄。",Modifier.padding(24.dp),fontSize=14.sp)
            Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SystemSecondaryButton("取消",{},Modifier.weight(1f))
                SystemButton({},Modifier.weight(1f)) { Text("清除") }
            }
        }
    }
}

@PreviewTest @Preview(name="Appearance",widthDp=393,heightDp=852)
@Composable fun Appearance()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        top.yukonga.miuix.kmp.basic.SmallTopAppBar(title="介面與顯示")
        SystemDisplaySettingsPanel("dark",SystemUiStyle.HyperOS4,{},"floating",{},true,{},false,{},{})
    }
}

@PreviewTest @Preview(name="BlurScene",widthDp=393,heightDp=500)
@Composable fun BlurScene()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    val scene=rememberBarBackdrop()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if(scene!=null) Modifier.layerBackdrop(scene) else Modifier)
            .background(MiuixTheme.colorScheme.surface),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            repeat(8) { i -> SystemCard(insideMargin=PaddingValues(20.dp)) { Text("背景內容 ${i+1} · 載入與套用狀態",fontSize=20.sp) } }
        }
        TopLevelProgressiveBar(scene,pageTitles=listOf("設定"),pagePosition=0f)
        CompositionLocalProvider(LocalSystemPanelBackdrop provides scene) {
            SystemSheetSurface("漢化詳情",Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp),onToggle={}) {
                Text("零協會完整翻譯與角色名稱已自動整理。",Modifier.padding(24.dp),fontSize=16.sp)
                SystemTextButton("完成",{},Modifier.fillMaxWidth())
            }
        }
    }
}
