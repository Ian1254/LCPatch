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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
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
            assertEquals("The official pack's twelve stage notes are not missing characters",0,report.unresolvedSpeakers)
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
    true, true, "1.119.0", false, "已啟用", "已授權", "已連接", true, true, 2383, "PC 翻譯與目前漢化一致", "2026111401")

@Composable private fun ShizukuFlowFixture(dark: Boolean, writing: Boolean) = SystemUiTheme(SystemUiStyle.HyperOS4, if(dark) "dark" else "light") {
    val progress=if(writing) ApplyProgress("正在寫入語言資源", 1100, 3838) else null
    val state=sampleOverview.copy(rootMode=false,enabled=writing,busy=writing,loadStatus="Stellar · 已授權",loadReady=true)
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SystemTranslationPanel(state, {}, {}, {}, progress, waitingForGame=!writing)
        SystemDownloadedPackCard("零協會漢化", "2026111401", applied = writing)
        SystemEnvironmentPanel(state,{}, {}, {}, "授權／檢查 Stellar")
    }
}

@PreviewTest @Preview(name="ShizukuWaiting",widthDp=393,heightDp=852)
@Composable fun ShizukuWaiting()=ShizukuFlowFixture(true,false)

@PreviewTest @Preview(name="ShizukuWritingLight",widthDp=393,heightDp=852)
@Composable fun ShizukuWritingLight()=ShizukuFlowFixture(false,true)

@Composable private fun AuthorizationStatesFixture(dark: Boolean) = SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
    Column(Modifier.background(MiuixTheme.colorScheme.surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val state=sampleOverview.copy(name="日文字形漢化（日本語）",rootMode=false,packVersion="v1.119.0",loadReady=false)
        SystemTranslationPanel(state.copy(enabled=false),{},{},{})
        SystemEnvironmentPanel(state,{},{},{},"授權 Stellar")
        SystemTranslationPanel(state.copy(enabled=true),{},{},{})
        SystemEnvironmentPanel(state.copy(loadReady=true),{},{},{},"檢查 Stellar")
    }
}

@PreviewTest @Preview(name="StatusDark",widthDp=393,heightDp=1000)
@Composable fun StatusDark()=AuthorizationStatesFixture(true)
@PreviewTest @Preview(name="StatusLight",widthDp=393,heightDp=1000)
@Composable fun StatusLight()=AuthorizationStatesFixture(false)
@PreviewTest @Preview(name="StatusFont200",widthDp=360,heightDp=1600,fontScale=2f)
@Composable fun StatusFont200()=AuthorizationStatesFixture(true)

/** The shell and panels below are the same composables used by MainActivity. */
@Composable private fun ScreenFixture(style: SystemUiStyle, dark: Boolean, page: Int = 2, scrolled: Boolean = false, atEnd: Boolean = false, floating: Boolean = true) {
    SystemUiTheme(style, if (dark) "dark" else "light") {
        val backdrop = rememberBarBackdrop()
        // Snapshot the settled state without Layoutlib's initial backward-fill pass.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex=if(scrolled) 2 else 0)
        val collapse = if(style == SystemUiStyle.HyperOS4 || scrolled || atEnd || listState.firstVisibleItemIndex > 0) 1f else
            (listState.firstVisibleItemScrollOffset / with(LocalDensity.current) { 64.dp.toPx() }).coerceIn(0f,1f)
        SystemCanvas {
            Scaffold(containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { TopLevelProgressiveBar(backdrop, pageTitles = listOf("LCPatch", "日誌", "設定"),
                    pagePosition = page.toFloat(), collapseFraction = collapse,actions={if(page==1) SystemLogOverflow(false,{},{},{})}) },
                bottomBar = { SystemBottomNavigation(page, listOf("概觀", "日誌", "設定"),
                    listOf(MiuixIcons.Home, Icons.Default.List, MiuixIcons.Settings),
                    floating, backdrop = backdrop, onSelected = {}) }
            ) { padding ->
                Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier).background(MiuixTheme.colorScheme.surface)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentPadding = PaddingValues(start = systemPageGutter(),
                        end = systemPageGutter(),
                        top = padding.calculateTopPadding()+12.dp,
                        bottom = padding.calculateBottomPadding()+16.dp), verticalArrangement = Arrangement.spacedBy(if(page==1) 0.dp else 12.dp)) {
                    if (style != SystemUiStyle.HyperOS4) item { Spacer(Modifier.height(if(scrolled || atEnd) 0.dp else 64.dp)) }
                    when (page) {
                        0 -> {
                            item { SystemTranslationPanel(sampleOverview, {}, {}, {}) }
                            item { SystemQuickActions({}, {}) }
                            item { SystemSectionLabel("運作狀態") }
                            item { SystemEnvironmentPanel(sampleOverview, {}, {}, {}, "選擇資料夾") }

                        }
                        1 -> {
                            item { SystemLogGuide(500,5,1) }
                            listOf(
                                LogEvent("2026-11-13T17:36:24.402Z", "ERROR", "APP_ACTION", "套用失敗：PC 翻譯處理未完成，尚未寫入遊戲", ""),
                                LogEvent("2026-11-13T17:36:03.271Z", "ERROR", "translation.install_failed", "無法處理漢化內容，請分享診斷文件查看完整原因。", ""),
                                LogEvent("2026-11-13T17:36:03.264Z", "INFO", "download.completed", "漢化資源已下載完成", ""),
                                LogEvent("2026-11-13T17:35:03.264Z", "INFO", "translation.textasset.index", "已建立內建文字資源索引", "")
                            ).let { events -> groupLogEventsByDate(events,java.time.ZoneId.systemDefault()).forEach { group ->
                                item { Box(Modifier.padding(top=20.dp,bottom=10.dp)) {SystemSectionLabel(group.label)} }
                                group.events.forEachIndexed {i,event -> item {SystemLogRecord(event,i==0,i==group.events.lastIndex)} }
                            } }
                        }
                        else -> {
                            item { SystemSectionLabel("運作環境") }
                            item { SystemCard { SystemDropdownPreference("運作模式", listOf("無 root", "Root ＋ LSPosed"), 1, {}); SystemModeHelp() } }
                            item { SystemSectionLabel("漢化") }
                            item { SystemCard {
                                SystemDropdownPreference("覆蓋語言文件", listOf("日本語", "한국어", "English"), 0, {})
                                SystemActiveConversion("繁體",true,{})
                                SystemArrowPreference("匯入漢化包", value = "ZIP 檔案", onClick = {})
                                SystemArrowPreference("更換字型", last=true,value = "內建中文字型", onClick = {})
                            } }
                            item { SystemSectionLabel("應用程式") }
                            item { SystemCard {
                                SystemArrowPreference("介面與顯示", summary = "深淺模式、底欄與模糊效果", onClick = {})
                                SystemArrowPreference("診斷文件儲存位置", value = "預設位置", onClick = {})
                                SystemArrowPreference("應用程式更新", onClick = {})
                                SystemArrowPreference("關於", last=true,onClick = {})
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
    SystemCard(Modifier.padding(12.dp)) { SystemDropdownPreference("深淺模式",listOf("跟隨系統","淺色","深色"),selected,{}) }
}
@PreviewTest @Preview(name="FirstSelected",widthDp=393,heightDp=240)
@Composable fun FirstSelected()=MenuFixture(0,false)
@PreviewTest @Preview(name="MiddleSelected",widthDp=393,heightDp=240)
@Composable fun MiddleSelected()=MenuFixture(1,true)
@PreviewTest @Preview(name="LastSelected",widthDp=393,heightDp=240)
@Composable fun LastSelected()=MenuFixture(2,false)

@Composable private fun SheetFixture(dark: Boolean)=SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
        SystemSheetSurface("環境與權限",Modifier.padding(12.dp)) {
            SystemEnvironmentDialogBody(sampleOverview,{},"選擇資料夾",onClose={})
        }
    }
}
@PreviewTest @Preview(name="ExpandedSheet",widthDp=393,heightDp=700)
@Composable fun ExpandedSheet()=SheetFixture(true)
@PreviewTest @Preview(name="DetailLight",widthDp=393,heightDp=700)
@Composable fun DetailLight()=SheetFixture(false)
@PreviewTest @Preview(name="SheetFont200",widthDp=360,heightDp=800,fontScale=2f)
@Composable fun SheetFont200()=SheetFixture(true)

@PreviewTest @Preview(name="Actions",widthDp=393,heightDp=900)
@Composable fun ActionStates()=SystemUiTheme(SystemUiStyle.HyperOS4,"light") {
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SystemProgressActionButton("下載漢化",onClick={})
        SystemProgressActionButton("正在下載 · 42%",.42f,true,onClick={})
        SystemProgressActionButton("正在整理完整翻譯…",busy=true,onClick={})
        SystemProgressActionButton("尚未選擇漢化包",enabled=false,onClick={})
        SystemNotice("下載未完成，請稍後重試。", true, {})
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
    Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),
        topBar={SystemInnerTopBar("介面與顯示",{})}) { padding ->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=12.dp,end=12.dp,
            top=padding.calculateTopPadding()+12.dp,bottom=16.dp)) {
            item { SystemDisplaySettingsPanel("dark",SystemUiStyle.HyperOS4,{},"floating",{},true,{},false,{},{}) }
        }
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
        CompositionLocalProvider(LocalSystemPanelBackdrop provides scene, LocalSystemWindowBackdrop provides scene) {
            SystemCard(Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top=72.dp,end=12.dp).width(260.dp)) {
                SystemDropdownPreference("深淺模式",listOf("跟隨系統","淺色","深色"),2,{})
            }
            SystemSheetSurface("漢化詳情",Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp)) {
                Text("零協會完整翻譯與角色名稱已自動整理。",Modifier.padding(24.dp),fontSize=16.sp)
                SystemDialogDone({})
            }
        }
    }
}

@PreviewTest @Preview(name="ScrolledFloating",widthDp=393,heightDp=852)
@Composable fun ScrolledFloating()=ScreenFixture(SystemUiStyle.HyperOS4,true,scrolled=true)

@PreviewTest @Preview(name="CompactNotices",widthDp=393,heightDp=320)
@Composable fun CompactNotices()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SystemNotice("漢化已套用", false, null)
        SystemNotice("下載未完成，請稍後重試。", true, {})
        SystemNotice("無法完成下載，點擊通知可查看完整原因。原先已套用的漢化仍會保留；可稍後重新下載或分享診斷文件。", true, {})
    }
}
@PreviewTest @Preview(name="CompactNoticesLarge",widthDp=360,heightDp=480,fontScale=2f)
@Composable fun CompactNoticesLarge()=SystemUiTheme(SystemUiStyle.HyperOS4,"light") {
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SystemNotice("漢化已套用", false, null)
        SystemNotice("下載未完成，請稍後重試。", true, {})
    }
}
@PreviewTest @Preview(name="StandardNeutral",widthDp=393,heightDp=200)
@Composable fun StandardNeutral()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf(0,2).forEach { index ->
            SystemStandardBottomNavigation(index,listOf("概觀","日誌","設定"),listOf(MiuixIcons.Home,Icons.Default.List,MiuixIcons.Settings),{})
        }
    }
}

@PreviewTest @Preview(name="LogOverflow",widthDp=300,heightDp=280)
@Composable fun LogOverflow()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    Box(Modifier.padding(16.dp)) {SystemCard {SystemLogMenuItems(false,{})}}
}
@PreviewTest @Preview(name="LanguageProgress",widthDp=393,heightDp=260)
@Composable fun LanguageProgress()=SystemUiTheme(SystemUiStyle.HyperOS4,"light") {
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SystemCard { SystemLanguageProgress(ApplyProgress("正在讀取已下載漢化",0,0)) }
        SystemCard { SystemLanguageProgress(ApplyProgress("正在整理日本語覆蓋文件",64,128)) }
    }
}

class LanguagePreparationProgressTest {
    @Test fun unknownWorkUsesActivityAndCountedWorkReportsActualFraction() {
        assertNull(ApplyProgress("正在讀取已下載漢化",0,0).fraction)
        assertNull(ApplyProgress("正在寫入遊戲目錄",0,0).fraction)
        assertEquals(.5f,ApplyProgress("正在整理覆蓋文件",64,128).fraction!!,0f)
    }
    @Test fun actualLanguageCopyReportsBatchesAndKeepsFontAndNestedResources() = kotlinx.coroutines.runBlocking {
        val temp=Files.createTempDirectory("language-progress").toFile()
        try {
            val source=File(temp,"source").apply {mkdirs()}
            val target=File(temp,"target")
            repeat(35) {i ->File(source,"JP_File$i.json").writeText("{\"id\":$i}")}
            File(source,"ChineseFont.ttf").writeText("font-fixture")
            val reports=mutableListOf<ApplyProgress>()
            copyLanguageResources(source,target,OverrideLanguage("kr","韓國語","KR"),true) {reports+=it}
            assertEquals(listOf(0,16,32,36),reports.map {it.current})
            assertTrue(reports.all {it.total==36})
            assertEquals(1f,reports.last().fraction!!,0f)
            assertEquals("{\"id\":0}",File(target,"KR_File0.json").readText())
            assertEquals("font-fixture",File(target,"ChineseFont.ttf").readText())
            assertEquals(36,target.walkTopDown().count {it.isFile})
        } finally {temp.deleteRecursively()}
    }
    @Test fun nativeCopyCountsOnlyJsonAndPreservesNestedLayout() = kotlinx.coroutines.runBlocking {
        val temp=Files.createTempDirectory("native-language-progress").toFile()
        try {
            val source=File(temp,"source/sub").apply {mkdirs()}.parentFile
            File(source,"sub/JP_Battle.json").writeText("[]")
            File(source,"ChineseFont.ttf").writeText("excluded")
            val target=File(temp,"target")
            val reports=mutableListOf<ApplyProgress>()
            copyLanguageResources(source,target,OverrideLanguage("kr","韓國語","KR"),false) {reports+=it}
            assertEquals(listOf(0,1),reports.map {it.current})
            assertTrue(reports.all {it.total==1})
            assertEquals("[]",File(target,"sub/KR_Battle.json").readText())
            assertFalse(File(target,"ChineseFont.ttf").exists())
        } finally {temp.deleteRecursively()}
    }
}

class InstalledIdentityAndLogDateTest {
    @Test fun installedVersionNeverUsesUnrelatedOrMovingRelease() {
        assertNull(installedPackVersion(null,null))
        assertNull(installedPackVersion("{\"schema\":1,\"url\":\"https://example.com/other/repo/releases/download/123/a.zip\"}",null))
        assertNull(installedPackVersion("{\"schema\":1,\"version\":\"latest\"}",null))
        assertNull(installedPackVersion("{\"schema\":2,\"version\":\"123\"}",null))
    }
    @Test fun installedMetadataWinsOverLegacyCoverage() {
        val source="來源：https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/2026111401/a.zip"
        assertNull(installedPackVersion(null,source+"\nPC 比對結果"))
        val coverage="零協會完整資源與劇情角色名稱已自動整理\n"+source
        assertEquals("2026111401",installedPackVersion(null,coverage))
        assertEquals("20261001",installedPackVersion("{\"schema\":1,\"version\":\"20261001\"}",coverage))
        assertEquals("2026111401",installedPackVersion("{\"schema\":1,\"url\":\"https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/2026111401/a.zip\"}",null))
    }
    @Test fun dateGroupingUsesLocalDayAndPreservesEveryRecord() {
        val first=LogEvent("2026-11-14T17:00:00Z","INFO","x","same","")
        val second=first.copy(time="2026-11-14T15:59:59Z")
        val events=listOf(first,first,second)
        val groups=groupLogEventsByDate(events,java.time.ZoneId.of("Asia/Taipei"))
        assertEquals(listOf("2026/11/15","2026/11/14"),groups.map {it.label})
        assertEquals(events,groups.flatMap {it.events})
        assertEquals(2,groups.first().events.size)
        assertEquals(1,groupLogEventsByDate(events,java.time.ZoneId.of("UTC")).size)
    }
    @Test fun invalidTimestampIsRetained() {
        val event=LogEvent("unknown","INFO","x","text","")
        val group=groupLogEventsByDate(listOf(event),java.time.ZoneId.of("UTC")).single()
        assertEquals("日期未知",group.label)
        assertEquals(listOf(event),group.events)
    }
}

@PreviewTest @Preview(name="Download",widthDp=393,heightDp=852)
@Composable fun DownloadResources()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    SystemCanvas {
        Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
            Text("下載漢化",Modifier.padding(start=24.dp,top=24.dp,bottom=20.dp),fontSize=20.sp)
            SystemDownloadRefresh(false,{},0.dp) {
                LazyColumn(contentPadding=PaddingValues(14.dp)) {
                    item { SystemDownloadEntry(TranslationEntry("官方","零協會漢化","都市零協會",
                        "官方版本 2026111401；自動補齊 PC 資源及劇情角色名稱，無須另選整理版","fixture")) {
                        SystemProgressActionButton("下載並套用",null,false,true,{})
                    } }
                }
            }
        }
    }
}

@PreviewTest @Preview(name = "PackPopupReview", widthDp = 393, heightDp = 852)
@Composable fun PackPopupReview() = SystemUiTheme(SystemUiStyle.HyperOS4, "dark") {
    val packs = listOf(
        DownloadedTranslation("OurPlay 神人漢化", "ourplay", version = "189"),
        DownloadedTranslation("零協會漢化", "llc", version = "2026111901")
    )
    val state = sampleOverview.copy(name = packs.first().name, packVersion = packs.first().version)
    Scaffold(containerColor = MiuixTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LCPatch", Modifier.padding(start = 14.dp, top = 30.dp, bottom = 8.dp), fontSize = 26.sp)
                SystemTranslationPanel(state, {}, {}, {}, onChoosePack = {}, hasPackUpdate = true)
                SystemQuickActions({}, {})
                SystemSectionLabel("運作狀態")
                SystemEnvironmentPanel(state, {}, {}, {}, "授權／檢查 Stellar")
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
            Box(Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(top = 212.dp, start = 28.dp)
                .widthIn(min = 208.dp, max = 260.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)) {
                SystemPackMenuItems(packs, false, "ourplay", state.name, state.packVersion, {}, {})
            }
        }
    }
}

@PreviewTest @Preview(name = "DownloadReview", widthDp = 393, heightDp = 960)
@Composable fun DownloadReview() = SystemUiTheme(SystemUiStyle.HyperOS4, "dark") {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SystemInnerTopBar("下載漢化", {})
        val official = TranslationEntry("官方", "零協會漢化", "都市零協會", "自動補齊 PC 資源及劇情角色名稱。", "llc", version = "2026111901")
        val ourPlay = OurPlayTranslation.entry.copy(version = "190")
        val native = TranslationEntry("原生", "日文字形漢化", "pzwboy / ghcruise", "透過遊戲原生語言文件載入。", "native", version = "v1.119.0")
        SystemDownloadEntry(official) {
            SystemProgressActionButton(downloadPackAction(official, null), onClick = {})
        }
        SystemDownloadEntry(ourPlay) {
            SystemProgressActionButton(downloadPackAction(ourPlay, DownloadedTranslation(ourPlay.name, "ourplay", version = "189")), onClick = {})
        }
        SystemDownloadEntry(native) {
            SystemProgressActionButton(downloadPackAction(native, DownloadedTranslation(native.name, "native", version = native.version)), onClick = {})
        }
        SystemProgressActionButton("正在下載…", busy = true, onClick = {})
        SystemProgressActionButton("正在準備漢化", busy = true, onClick = {})
        SystemProgressActionButton("正在寫入語言資源 · 64/100", progress = .64f, busy = true, onClick = {})
    }
}

@PreviewTest @Preview(name = "AppUpdateReview", widthDp = 393, heightDp = 852)
@Composable fun AppUpdateReview() = SystemUiTheme(SystemUiStyle.HyperOS4, "dark") {
    Box(Modifier.fillMaxSize()) {
        ProposalOverviewContent()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
        SystemSheetSurface("應用程式更新", Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 24.dp)) {
            SystemAppUpdatePromptContent("1.2.5-beta.2", "1.2.5", "beta",
                "- 可在概觀切換或重新套用已下載的漢化。\n- 下載列表顯示版本與套用進度。\n- 新增可在設定關閉的更新提示。",
                suppress = true, {}, {}, {})
        }
    }
}

@PreviewTest @Preview(name = "UpdateSettingReview", widthDp = 393, heightDp = 430)
@Composable fun UpdateSettingReview() = SystemUiTheme(SystemUiStyle.HyperOS4, "dark") {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("設定", Modifier.padding(start = 14.dp, top = 20.dp), fontSize = 26.sp)
        SystemSectionLabel("應用程式")
        SystemCard {
            SystemArrowPreference("介面與顯示", "深淺模式、底欄與模糊效果", onClick = {})
            SystemArrowPreference("應用程式更新", "更新渠道與檢查更新", onClick = {})
            SystemSwitchPreference("顯示更新提示", checked = true, onCheckedChange = {})
            SystemArrowPreference("關於", "版本與元件", last = true, onClick = {})
        }
    }
}

// Visual proposal only: deletion/update actions are intentionally inert here.
@Composable private fun ProposalDivider() {
    Box(Modifier.fillMaxWidth().height(.5.dp).background(MiuixTheme.colorScheme.dividerLine))
}

@Composable private fun ProposalPack(name: String,active: Boolean=false,version: String?="2026111401") {
    SystemDownloadedPackCard(name, version, applied = active)
}

@Composable private fun ProposalDownloadedContent() {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=14.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {ProposalPack("都市零協會官方簡體漢化",version="2026111401")}
        item {ProposalPack("零協會漢化",true)}
        item {ProposalPack("匯入漢化包",version="sha256:"+"a".repeat(64))}
    }
}

@Composable private fun ProposalHeader(title: String) {
    Box(Modifier.fillMaxWidth().height(56.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
        Text(title,fontSize=20.sp,lineHeight=28.sp)
        Box(Modifier.align(androidx.compose.ui.Alignment.CenterStart).padding(start=12.dp).size(48.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
            top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Back,"返回",Modifier.size(26.dp),tint=MiuixTheme.colorScheme.onSurface)
        }
    }
}

@Composable private fun ProposalScreen(title: String,dark: Boolean=true,content: @Composable ()->Unit) {
    SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
        SystemCanvas {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(26.dp))
                ProposalHeader(title)
                content()
            }
        }
    }
}

@PreviewTest @Preview(name="DownloadedProposal",widthDp=393,heightDp=852)
@Composable fun DownloadedProposal()=PackManagementFixture(true,false)

@PreviewTest @Preview(name="DownloadedLightProposal",widthDp=393,heightDp=852)
@Composable fun DownloadedLightProposal()=PackManagementFixture(false,false)

@Composable private fun ProposalUpdateContent(checking: Boolean=false) {
    Box(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp)) {
        SystemUpdateCard(BuildConfig.VERSION_NAME,"beta",if(checking) "正在檢查更新…" else "已是最新版本",!checking,true,{})
    }
}

@PreviewTest @Preview(name="UpdateProposal",widthDp=393,heightDp=852)
@Composable fun UpdateProposal()=ProposalScreen("應用程式更新") {ProposalUpdateContent()}

@PreviewTest @Preview(name="UpdateLightProposal",widthDp=393,heightDp=852)
@Composable fun UpdateLightProposal()=ProposalScreen("應用程式更新",false) {ProposalUpdateContent()}

@PreviewTest @Preview(name="DeleteProposal",widthDp=393,heightDp=852)
@Composable fun DeleteProposal()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Column {
            Spacer(Modifier.height(26.dp));ProposalHeader("已下載漢化");ProposalDownloadedContent()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.50f)))
        SystemSheetSurface("刪除漢化包",Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp),true) {
            Column(Modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("零協會漢化",fontSize=17.sp,lineHeight=25.sp)
                Text("確定刪除此漢化包？只會移除下載的檔案，已套用到遊戲的漢化不受影響。",
                    fontSize=14.sp,lineHeight=22.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SystemSecondaryButton("取消",{},Modifier.weight(1f))
                SystemButton({},Modifier.weight(1f)) {Text("刪除")}
            }
        }
    }
}


@PreviewTest @Preview(name="UpdateCheckingProposal",widthDp=393,heightDp=852)
@Composable fun UpdateCheckingProposal()=ProposalScreen("應用程式更新") {ProposalUpdateContent(true)}

@Composable private fun ProposalStatusRow(title: String,value: String?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),fontSize=16.sp,lineHeight=23.sp)
        if(value!=null) Text(value,fontSize=13.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.width(12.dp))
        val ink=MiuixTheme.colorScheme.onSurfaceVariantSummary
        Box(Modifier.size(14.dp).drawBehind {
            val path=androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width*.35f,size.height*.15f);lineTo(size.width*.7f,size.height*.5f);lineTo(size.width*.35f,size.height*.85f)
            }
            drawPath(path,ink,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.6.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
        })
    }
}

@Composable private fun ProposalOverviewContent() { ScreenFixture(SystemUiStyle.HyperOS4,true,page=0) }

@PreviewTest @Preview(name="OverviewProposal",widthDp=393,heightDp=852)
@Composable fun OverviewProposal()=SystemUiTheme(SystemUiStyle.HyperOS4,"dark") {ProposalOverviewContent()}

@PreviewTest @Preview(name="OverviewLightProposal",widthDp=393,heightDp=852)
@Composable fun OverviewLightProposal()=ScreenFixture(SystemUiStyle.HyperOS4,false,page=0)


class ReleaseDataRegressionTest {
    @Test fun downloadActionDistinguishesNewInstalledAndNewerVersions() {
        val entry = OurPlayTranslation.entry.copy(version = "190")
        val downloaded = DownloadedTranslation(entry.name, "ourplay", version = "189")
        assertEquals("下載並套用", downloadPackAction(entry, null))
        assertEquals("更新並套用", downloadPackAction(entry, downloaded))
        assertEquals("重新下載並套用", downloadPackAction(entry, downloaded.copy(version = "190")))
        assertFalse(isNewerPackVersion("189", "190"))
        assertFalse(isNewerPackVersion("190", "sha256:unknown"))
        assertTrue(isNewerPackVersion("v1.120.0", "v1.119.9"))
    }
    @Test fun productionAnnotationsAreNotUnresolvedSpeakersButRealRowsRemainUnresolved() {
        val root=Files.createTempDirectory("speaker-notes").toFile()
        try {
            File(root,"ScenarioModelCodes-AutoCreated.json").writeText("{\"dataList\":[]}")
            val stories=File(root,"StoryData").apply {mkdirs()}
            val rows=org.json.JSONArray()
            repeat(11) {rows.put(JSONObject().put("id",-1).put("model","//효과음").put("content","stage sound"))}
            rows.put(JSONObject().put("id",-1).put("model","//CG").put("content","stage CG"))
            rows.put(JSONObject().put("id",10).put("model","//CG").put("content","real row"))
            rows.put(JSONObject().put("id",11).put("model","unknown").put("content","dialogue"))
            val file=File(stories,"test.json").apply {writeText(rows.toString())}
            val original=file.readText()
            val result=completeScenarioSpeakers(root)
            assertEquals(12,result.productionNotes);assertEquals(2,result.unresolved)
            assertEquals(original,file.readText())
        } finally {root.deleteRecursively()}
    }
    @Test fun deletingDownloadPreservesInstalledCopyAndRejectsEscapes() {
        val root=Files.createTempDirectory("delete-pack").toFile()
        try {
            val packs=File(root,"packs").apply {mkdirs()}
            val pack=File(packs,"pack").apply {mkdirs();File(this,"a.json").writeText("data")}
            val runtime=File(root,"runtime").apply {pack.copyRecursively(this)}
            // On hosts that permit symlinks, even nested links must not erase
            // files outside the downloaded pack during deletion.
            runCatching {Files.createSymbolicLink(File(pack,"runtime-link").toPath(),runtime.toPath())}
            val store=LocalPackStorage(packs)
            assertThrows(IllegalArgumentException::class.java) {store.delete(root.path)}
            assertThrows(IllegalArgumentException::class.java) {store.delete(File(packs,"../runtime").path)}
            assertThrows(IllegalArgumentException::class.java) {store.delete(File(packs,".internal").path)}
            store.delete(pack.path)
            assertFalse(pack.exists());assertEquals("data",File(runtime,"a.json").readText())
        } finally {root.deleteRecursively()}
    }
    @Test fun oldChannelResponseCannotOverwriteNewerSelection() {
        val requests=LatestUpdateRequest()
        val stable=requests.begin();val beta=requests.begin()
        assertFalse(requests.accepts(stable));assertTrue(requests.accepts(beta))
        val stableAgain=requests.begin()
        assertFalse(requests.accepts(beta));assertTrue(requests.accepts(stableAgain))
    }
}

@Composable private fun PackManagementFixture(dark: Boolean, selecting: Boolean) = SystemUiTheme(SystemUiStyle.HyperOS4,if(dark) "dark" else "light") {
    Scaffold(containerColor=MiuixTheme.colorScheme.surface,contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        SystemInnerTopBar(if(selecting) "2 項" else "已下載漢化",{}) {
            SystemPackDeleteActions(selecting,false,true,selecting,false,{},{},{})
        }
    }) {padding ->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=14.dp,end=14.dp,top=padding.calculateTopPadding()+12.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { SystemDownloadedPackCard("零協會漢化", "2026111401", applied = true, checked = selecting, batchMode = selecting) }
            item { SystemDownloadedPackCard("日文字形漢化（日本語）", "v1.119.0", applied = false, checked = selecting, batchMode = selecting) }
            item { SystemDownloadedPackCard("匯入漢化包", "sha256:" + "a".repeat(64), applied = false, batchMode = selecting) }
        }
    }
}

@PreviewTest @Preview(name="BatchDelete",widthDp=393,heightDp=852)
@Composable fun BatchDelete()=PackManagementFixture(true,true)

@PreviewTest @Preview(name="BatchDeleteLargeLight",widthDp=360,heightDp=852,fontScale=1.5f)
@Composable fun BatchDeleteLargeLight()=PackManagementFixture(false,true)
