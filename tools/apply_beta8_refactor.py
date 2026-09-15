from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"missing exact block in {path}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))


def regex_once(path: str, pattern: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text()
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"regex expected 1 match in {path}, got {count}: {pattern[:80]!r}")
    file.write_text(updated)


MAIN = "app/src/main/java/com/filepermwebui/MainActivity.kt"
TRANS = "app/src/main/java/com/filepermwebui/TranslationRepository.kt"

# MainActivity: remove the forever ~2 second polling loop. Scope state is refreshed on
# relevant page entry and on explicit user request instead.
replace_once(
    MAIN,
    '''        LaunchedEffect(Unit) {
            refreshCatalog()
            while (true) {
                ModernApp.refreshScope()
                delay(300)
                scopeStatus = when { ModernApp.scopeKnown && ModernApp.scopeGranted -> "已啟用"; ModernApp.scopeKnown -> "尚未授權遊戲"; else -> "尚未連接" }
                delay(1700)
            }
        }
''',
    '''        suspend fun refreshScopeStatus() {
            ModernApp.refreshScope()
            delay(300)
            scopeStatus = when {
                ModernApp.scopeKnown && ModernApp.scopeGranted -> "已啟用"
                ModernApp.scopeKnown -> "尚未授權遊戲"
                else -> "尚未連接"
            }
        }
        LaunchedEffect(Unit) { refreshCatalog() }
        LaunchedEffect(page) {
            if (page == OVERVIEW || page == ONBOARDING) refreshScopeStatus()
        }
'''
)

replace_once(
    MAIN,
    '''                        SukiFloatingBottomBar(
                            selectedIndex = pagerState.settledPage,
                            navigationPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, topPages.lastIndex.toFloat()),
                            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            onDragProgress = { position ->
                                val nearestPage = position.roundToInt().coerceIn(0, topPages.lastIndex)
                                pagerState.requestScrollToPage(
                                    page = nearestPage,
                                    pageOffsetFraction = (position - nearestPage).coerceIn(-0.5f, 0.5f)
                                )
                            },
                            backdrop = activeBarBackdrop
                        )
''',
    '''                        SukiFloatingBottomBar(
                            selectedIndex = pagerState.settledPage,
                            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backdrop = activeBarBackdrop
                        )
'''
)

replace_once(
    MAIN,
    '''                        onCheckScope = { ModernApp.refreshScope(); message = "正在重新檢查模組狀態" },
''',
    '''                        onCheckScope = {
                            scope.launch {
                                refreshScopeStatus()
                                message = "模組狀態已更新"
                            }
                        },
'''
)

replace_once(
    MAIN,
    '''                        (slideInHorizontally(tween(360, easing = PageTransitionEasing)) { direction * it / 10 } +
                            fadeIn(tween(300, easing = PageTransitionEasing))) togetherWith
                            (slideOutHorizontally(tween(300, easing = PageTransitionEasing)) { -direction * it / 12 } +
                                fadeOut(tween(220, easing = PageTransitionEasing)))
''',
    '''                        (slideInHorizontally(tween(380, easing = PageTransitionEasing)) { direction * it } +
                            fadeIn(tween(140, easing = PageTransitionEasing))) togetherWith
                            (slideOutHorizontally(tween(320, easing = PageTransitionEasing)) { -direction * it / 4 } +
                                fadeOut(tween(180, easing = PageTransitionEasing)))
'''
)

# Root permission check goes through the same executor as runtime install and diagnostics.
regex_once(
    MAIN,
    r'''    private fun requestRoot\(\): Boolean = try \{.*?    \} catch \(_: Throwable\) \{ false \}\n''',
    '''    private fun requestRoot(): Boolean = RootShell.run("id", timeoutMs = 15_000L).success\n'''
)
replace_once(MAIN, 'import java.util.concurrent.TimeUnit\n', '')

# About and Update are separate implementations rather than one updateOnly switch.
replace_once(
    MAIN,
    '''                    ABOUT -> about(
                        updateOnly = false,
                        game = game,
                        updateChannel = updateChannel,
                        onUpdateChannel = { value ->
                            updateChannel = value
                            appPrefs.edit().putString("update_channel", value).apply()
                            latestRelease = null
                        },
                        release = latestRelease,
                        checkingUpdate = checkingUpdate,
                        updateProgress = updateProgress,
                        updateReady = downloadedUpdate != null,
                        checkUpdate = ::checkAppUpdate,
                        downloadUpdate = { latestRelease?.let(downloadAppUpdate) },
                        installUpdate = ::installDownloadedUpdate
                    )
                    UPDATE -> about(
                        updateOnly = true,
                        game = game,
                        updateChannel = updateChannel,
                        onUpdateChannel = { value ->
                            updateChannel = value
                            appPrefs.edit().putString("update_channel", value).apply()
                            latestRelease = null
                        },
                        release = latestRelease,
                        checkingUpdate = checkingUpdate,
                        updateProgress = updateProgress,
                        updateReady = downloadedUpdate != null,
                        checkUpdate = ::checkAppUpdate,
                        downloadUpdate = { latestRelease?.let(downloadAppUpdate) },
                        installUpdate = ::installDownloadedUpdate
                    )
''',
    '''                    ABOUT -> aboutPage(game)
                    UPDATE -> updatePage(
                        updateChannel = updateChannel,
                        onUpdateChannel = { value ->
                            updateChannel = value
                            appPrefs.edit().putString("update_channel", value).apply()
                            latestRelease = null
                        },
                        release = latestRelease,
                        checkingUpdate = checkingUpdate,
                        updateProgress = updateProgress,
                        updateReady = downloadedUpdate != null,
                        checkUpdate = ::checkAppUpdate,
                        downloadUpdate = { latestRelease?.let(downloadAppUpdate) },
                        installUpdate = ::installDownloadedUpdate
                    )
'''
)

about_replacement = r'''    private fun LazyListScope.aboutPage(game: GameInfo) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(R.drawable.ic_launcher), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(CircleShape))
                Spacer(Modifier.height(14.dp))
                Text("LCPatch", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("Limbus Company 漢化與字型修正", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.weight(1f), insideMargin = PaddingValues(18.dp), colors = translucentAboutCardColors()) {
                    Text("LCPatch 版本", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp)); Text(BuildConfig.VERSION_NAME, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Card(modifier = Modifier.weight(1f), insideMargin = PaddingValues(18.dp), colors = translucentAboutCardColors()) {
                    Text("遊戲版本", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp)); Text(game.version.substringBefore(" ("), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp), colors = translucentAboutCardColors()) {
                Text("詳細資訊", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(10.dp))
                Detail("核心元件", "LSPosed API 102")
                Detail("使用者介面", "Miuix 0.9.3")
                Detail("文字轉換", "opencc4j 1.14.0")
                Detail("目標架構", "arm64-v8a")
                Detail("儲存目錄", "/sdcard/LCPatch")
            }
        }
        item { InfoCard("關於 LCPatch", "LCPatch 用於管理社群與自訂漢化、字體以及語言覆蓋設定。遊戲更新後會先驗證目標結構，配置不相符時停止載入，以降低閃退風險。", translucent = true) }
        item { InfoCard("開放原始碼與致謝", "介面採用 compose-miuix-ui，LSPosed 整合採用 libxposed API 102，繁簡轉換採用 opencc4j。漢化內容與授權條款歸各翻譯組及原作者所有。", translucent = true) }
    }

    private fun LazyListScope.updatePage(
        updateChannel: String,
        onUpdateChannel: (String) -> Unit,
        release: AppRelease?,
        checkingUpdate: Boolean,
        updateProgress: TransferProgress?,
        updateReady: Boolean,
        checkUpdate: () -> Unit,
        downloadUpdate: () -> Unit,
        installUpdate: () -> Unit
    ) {
        item {
            val newer = release?.let {
                updates.isNewer(it.version, BuildConfig.VERSION_NAME)
            } == true
            Card(insideMargin = PaddingValues(18.dp), colors = translucentAboutCardColors()) {
                Text("應用程式更新", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                OverlayDropdownPreference(
                    modifier = PreferenceItemModifier,
                    title = "更新渠道",
                    summary = if (updateChannel == "beta") "測試版與正式版" else "僅正式版",
                    items = listOf("穩定版", "測試版"),
                    selectedIndex = if (updateChannel == "beta") 1 else 0,
                    onSelectedIndexChange = { index ->
                        onUpdateChannel(if (index == 1) "beta" else "stable")
                    }
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    when {
                        checkingUpdate -> "正在從 GitHub Releases 檢查…"
                        updateReady -> "新版 APK 已下載"
                        newer -> "可更新至 LCPatch " + release?.version
                        release != null -> "目前已是最新版 " + BuildConfig.VERSION_NAME
                        else -> "目前版本 " + BuildConfig.VERSION_NAME
                    },
                    color = if (newer || updateReady) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                updateProgress?.let { progress ->
                    val fraction = if (progress.total > 0) {
                        (progress.bytes.toFloat() / progress.total).coerceIn(0f, 1f)
                    } else null
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = fraction)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatBytes(progress.bytes) +
                            if (progress.total > 0) " / " + formatBytes(progress.total) else "",
                        fontSize = 13.sp
                    )
                }
                if (newer && !release?.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        release!!.notes,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    updateReady -> Button(modifier = Modifier.fillMaxWidth(), onClick = installUpdate) {
                        Text("安裝更新")
                    }
                    newer -> Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = updateProgress == null,
                        onClick = downloadUpdate
                    ) { Text(if (updateProgress == null) "下載更新" else "正在下載…") }
                    else -> TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (checkingUpdate) "正在檢查…" else "檢查更新",
                        enabled = !checkingUpdate,
                        onClick = checkUpdate
                    )
                }
            }
        }
    }

'''
regex_once(
    MAIN,
    r'''    private fun LazyListScope\.about\(.*?\n    private fun LazyListScope\.onboardingPage\(''',
    about_replacement + '    private fun LazyListScope.onboardingPage('
)

# TranslationRepository: shared HTTP setup and shared resumable downloader.
replace_once(TRANS, 'import java.net.HttpURLConnection\n', '')
replace_once(TRANS, 'import java.net.URL\n', '')
replace_once(TRANS, '        private val ROOT_OPERATION_LOCK = Any()\n', '')
replace_once(TRANS, '            connection(SOURCE).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }', '            HttpClient.open(SOURCE).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }')
replace_once(TRANS, '        val raw = connection(OFFICIAL_LATEST).run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }', '        val raw = HttpClient.open(OFFICIAL_LATEST, accept = "application/vnd.github+json").run { inputStream.bufferedReader().use { it.readText() }.also { disconnect() } }')

new_download = r'''    suspend fun download(
        entry: TranslationEntry,
        progress: suspend (TransferProgress) -> Unit,
        preparation: suspend (ApplyProgress) -> Unit = {}
    ): DownloadedTranslation = withContext(Dispatchers.IO) {
        val fileName = "${safeName(entry.name)}.zip"
        val part = File(downloads, "$fileName.part")
        val output = File(downloads, fileName)
        try {
            val result = downloadResumable(
                url = entry.url,
                part = part,
                displayName = fileName,
                progress = progress
            )
            require(part.length() > 4 && part.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }) { "下載內容不是 ZIP 漢化包" }
            ZipFile(part).use { require(it.entries().asSequence().any { item -> localizeRelativePath(item.name) != null }) { "漢化包缺少 Localize 資料夾" } }
            if (output.exists()) output.delete()
            require(part.renameTo(output)) { "無法保存下載檔" }
            prefs.edit().putString("download.${output.name}", entry.name).apply()
            val prepared = File(staging, "download-${safeName(entry.name)}").also { it.deleteRecursively(); it.mkdirs() }
            unpackLocalize(output, prepared)
            File(prepared, ".lcpatch-script").writeText(inferScript(entry.name))
            encodeDirectoryToPua(prepared, preparation)
            File(prepared, PUA_MARKER).writeText("1")
            val publicPath = "$PUBLIC_DOWNLOADS/${safeName(entry.name)}"
            require(runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && rm -rf ${quote(publicPath)} && cp -R ${quote(prepared.absolutePath)} ${quote(publicPath)} && chmod -R 0755 ${quote(publicPath)}")) { "無法保存已解壓漢化" }
            output.delete()
            progress(TransferProgress(fileName, result.bytes, if (result.total > 0) result.total else result.bytes, 0, true))
            LogRepository.append(context, "INFO", "download.completed", "${entry.name} 已下載並解壓到獨立目錄")
            DownloadedTranslation(entry.name, publicPath, inferScript(entry.name), true)
        } catch (error: Throwable) {
            val looksLikeZip = runCatching {
                part.length() > 4 && part.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
            }.getOrDefault(false)
            if (!looksLikeZip) part.delete()
            throw error
        }
    }

'''
regex_once(
    TRANS,
    r'''    suspend fun download\(.*?\n    private suspend fun applyPrepared''',
    new_download + '    private suspend fun applyPrepared'
)

replace_once(
    TRANS,
    '''        val command = "am force-stop $GAME_PACKAGE && $nativeAction"
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val response = process.inputStream.bufferedReader().use { it.readText() }
        require(process.waitFor() == 0) { response.ifBlank { "root 套用失敗" } }
''',
    '''        val command = "am force-stop $GAME_PACKAGE && $nativeAction"
        val result = RootShell.run(command)
        require(result.success) { result.output.ifBlank { "root 套用失敗" } }
'''
)

replace_once(
    TRANS,
    '''        val process = ProcessBuilder("su", "-c", "find ${quote(PUBLIC_DOWNLOADS)} -mindepth 1 -maxdepth 1 -type d -print").redirectErrorStream(true).start()
        val paths = process.inputStream.bufferedReader().readLines()
        if (process.waitFor() != 0) return@withContext emptyList()
        paths.mapNotNull { original ->
''',
    '''        val result = RootShell.run("find ${quote(PUBLIC_DOWNLOADS)} -mindepth 1 -maxdepth 1 -type d -print")
        if (!result.success) return@withContext emptyList()
        result.output.lineSequence().filter(String::isNotBlank).mapNotNull { original ->
'''
)
replace_once(TRANS, '        }.distinctBy { it.path }.sortedBy { it.name.lowercase() }\n', '        }.toList().distinctBy { it.path }.sortedBy { it.name.lowercase() }\n')

replace_once(
    TRANS,
    '''        val process = ProcessBuilder("su", "-c", "cat ${quote("$path/.lcpatch-script")} 2>/dev/null").redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText().trim() }
        return if (process.waitFor() == 0 && value in setOf("簡體", "繁體")) value else inferScript(name)
''',
    '''        val result = RootShell.run("cat ${quote("$path/.lcpatch-script")} 2>/dev/null")
        val value = result.output.trim()
        return if (result.success && value in setOf("簡體", "繁體")) value else inferScript(name)
'''
)

replace_once(
    TRANS,
    '''        val legacyList = ProcessBuilder("su", "-c", "find ${quote(PUBLIC_DOWNLOADS)} -maxdepth 1 -type f -iname '*.zip' -print").redirectErrorStream(true).start()
        val legacyPaths = legacyList.inputStream.bufferedReader().readLines()
        if (legacyList.waitFor() == 0) legacyPaths.forEach { path ->
''',
    '''        val legacyList = RootShell.run("find ${quote(PUBLIC_DOWNLOADS)} -maxdepth 1 -type f -iname '*.zip' -print")
        if (legacyList.success) legacyList.output.lineSequence().filter(String::isNotBlank).forEach { path ->
'''
)

regex_once(
    TRANS,
    r'''    private fun runRoot\(command: String\): Boolean = synchronized\(ROOT_OPERATION_LOCK\) \{.*?    private fun safeName''',
    '''    private fun runRoot(command: String): Boolean = RootShell.run(command).success

    private fun runRootOutput(command: String): Pair<Boolean, String> {
        val result = RootShell.run(command)
        return result.success to result.output
    }

    private fun safeName'''
)

print("beta8 refactor applied")
