package com.lcpatch

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.kyant.backdrop.backdrops.layerBackdrop

private val PreferenceItemModifier = Modifier.clip(RoundedCornerShape(18.dp))
private val PageTransitionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private data class PageScrollPosition(val index: Int, val offset: Int)

class MainActivity : ComponentActivity() {
    private val logs by lazy { LogRepository(this) }
    private val translations by lazy { TranslationRepository(this) }
    private val updates by lazy { AppUpdateRepository(this) }
    private val appPrefs by lazy { getSharedPreferences("app_state", MODE_PRIVATE) }
    override fun onResume() {
        super.onResume()
        Thread({ CrashDiagnostics.capture(this) }, "LCPatch-resume-diagnostics").start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(appPrefs.getString("theme_mode", "system") ?: "system") }
            val controller = remember(themeMode) {
                ThemeController(
                    colorSchemeMode = when (themeMode) {
                        "light" -> ColorSchemeMode.Light
                        "dark" -> ColorSchemeMode.Dark
                        else -> ColorSchemeMode.System
                    }
                )
            }
            MiuixTheme(controller = controller) {
                App(themeMode) { value -> themeMode = value; appPrefs.edit().putString("theme_mode", value).apply() }
            }
        }
    }

    @Composable
    private fun App(themeMode: String, onThemeMode: (String) -> Unit) {
        val taskState: MainTaskViewModel = viewModel()
        var page by rememberSaveable { mutableIntStateOf(if (appPrefs.getBoolean("onboarding_done", false)) OVERVIEW else ONBOARDING) }
        var navigationStack by rememberSaveable { mutableStateOf(intArrayOf()) }
        var navigationDirection by rememberSaveable { mutableIntStateOf(1) }
        val topPages = TOP_LEVEL_PAGES
        val pagerState = rememberPagerState(initialPage = topPages.indexOf(page).coerceAtLeast(0), pageCount = { topPages.size })
        val pagePosition by remember(pagerState) {
            derivedStateOf {
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            }
        }
        var topNavigationTarget by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }
        var topNavigationTransaction by rememberSaveable { mutableIntStateOf(0) }
        var revision by remember { mutableIntStateOf(0) }
        var notice by taskState.notice
        var latestRelease by taskState.latestRelease
        var checkingUpdate by taskState.checkingUpdate
        var updateProgress by taskState.updateProgress
        var downloadedUpdate by taskState.downloadedUpdate
        var catalog by remember { mutableStateOf<List<TranslationEntry>>(emptyList()) }
        var catalogLoading by remember { mutableStateOf(false) }
        var catalogError by remember { mutableStateOf<String?>(null) }
        var transfer by taskState.transfer
        var applyProgress by taskState.applyProgress
        var applying by taskState.applying
        var processingPackPath by taskState.processingPackPath
        var applyingPackPath by taskState.applyingPackPath
        var installingEntryKey by taskState.installingEntryKey
        var changingTargetLanguage by taskState.changingTargetLanguage
        var downloadedPacks by remember { mutableStateOf<List<DownloadedTranslation>>(emptyList()) }
        var activeName by remember { mutableStateOf(translations.activeName()) }
        var activeScript by remember { mutableStateOf(translations.activeScript()) }
        var translationEnabled by remember { mutableStateOf(translations.translationEnabled()) }
        var targetLanguage by remember { mutableStateOf(translations.targetLanguage()) }
        var fontName by remember { mutableStateOf(translations.fontName()) }
        var runtimeInspection by remember { mutableStateOf(RuntimeInspection()) }
        var scopeStatus by rememberSaveable { mutableStateOf("正在連接") }
        var rootStatus by rememberSaveable { mutableStateOf("尚未授權") }
        var navigationStyle by remember { mutableStateOf(appPrefs.getString("navigation_style", "floating") ?: "floating") }
        var blurEnabled by remember { mutableStateOf(appPrefs.getBoolean("blur_enabled", true)) }
        val environmentOverlayState = rememberEnvironmentStatusOverlayState()
        var updateChannel by remember { mutableStateOf(appPrefs.getString("update_channel", "stable") ?: "stable") }
        val overviewListState = rememberLazyListState()
        val settingsListState = rememberLazyListState()
        val logsListState = rememberLazyListState()
        val aboutListState = rememberLazyListState()
        val updateListState = rememberLazyListState()
        val downloadListState = rememberLazyListState()
        val onboardingListState = rememberLazyListState()
        val downloadedListState = rememberLazyListState()
        val displayListState = rememberLazyListState()
        val conversionListState = rememberLazyListState()
        val pageListState = when (page) {
            OVERVIEW -> overviewListState
            SETTINGS -> settingsListState
            LOGS -> logsListState
            ABOUT -> aboutListState
            UPDATE -> updateListState
            DOWNLOAD -> downloadListState
            ONBOARDING -> onboardingListState
            DOWNLOADED -> downloadedListState
            DISPLAY -> displayListState
            else -> conversionListState
        }
        val pageScrollPositions = remember { mutableMapOf<Int, PageScrollPosition>() }
        LaunchedEffect(page, pageListState) {
            pageScrollPositions[page]?.let { saved ->
                withFrameNanos { }
                withFrameNanos { }
                pageListState.scrollToItem(saved.index, saved.offset)
            }
            snapshotFlow {
                PageScrollPosition(
                    pageListState.firstVisibleItemIndex,
                    pageListState.firstVisibleItemScrollOffset
                )
            }.collect { pageScrollPositions[page] = it }
        }
        val overviewScrollBehavior = MiuixScrollBehavior()
        val settingsScrollBehavior = MiuixScrollBehavior()
        val logsScrollBehavior = MiuixScrollBehavior()
        val aboutScrollBehavior = MiuixScrollBehavior()
        val updateScrollBehavior = MiuixScrollBehavior()
        val downloadScrollBehavior = MiuixScrollBehavior()
        val onboardingScrollBehavior = MiuixScrollBehavior()
        val downloadedScrollBehavior = MiuixScrollBehavior()
        val displayScrollBehavior = MiuixScrollBehavior()
        val conversionScrollBehavior = MiuixScrollBehavior()
        val scope = rememberCoroutineScope()
        LaunchedEffect(topNavigationTransaction, topNavigationTarget) {
            if (pagerState.currentPage != topNavigationTarget || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.animateScrollToPage(
                    page = topNavigationTarget,
                    animationSpec = tween(440, easing = PageTransitionEasing)
                )
            }
        }
        val events = remember(revision) { logs.read() }
        val game = remember { gameInfo() }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    logs.setSelectedFolder(uri)
                    taskState.success("診斷文件位置已更新")
                } catch (error: Throwable) {
                    taskState.error("無法保存資料夾授權：${error.message}")
                }
                revision++
            }
        }
        val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching { withContext(Dispatchers.IO) { translations.setCustomFont(uri, contentName(uri)) } }
                    .onSuccess { fontName = translations.fontName(); taskState.success("字體已更新") }
                    .onFailure { taskState.error("字體匯入失敗：${it.message}") }
            }
        }
        val customPackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching { translations.importCustom(uri, contentName(uri)) }
                    .onSuccess { downloadedPacks = translations.downloaded(); taskState.success("已匯入 ${it.name}") }
                    .onFailure { taskState.error("漢化匯入失敗：${it.message}") }
            }
        }
        DisposableEffect(Unit) {
            val observer = logs.observe { revision++ }
            onDispose { logs.stopObserving(observer) }
        }
        fun refreshCatalog() {
            if (catalogLoading) return
            taskState.clearError()
            catalogLoading = true
            catalogError = null
            taskState.launchTask {
                runCatching { translations.fetchCatalog() }
                    .onSuccess { catalog = it }
                    .onFailure {
                        catalogError = it.message ?: "無法取得漢化清單"
                        taskState.error(
                            "無法取得漢化清單：" + (it.message ?: "請檢查網路連線")
                        ) { refreshCatalog() }
                    }
                catalogLoading = false
            }
        }
        LaunchedEffect(page) {
            if (page == DOWNLOAD && catalog.isEmpty() && !catalogLoading) refreshCatalog()
            if (page == DOWNLOADED || page == CONVERSION) downloadedPacks = translations.downloaded()
        }
        LaunchedEffect(notice) {
            if (notice != null && notice?.kind != UiNoticeKind.Error) {
                delay(2800)
                taskState.dismissNotice()
            }
        }
        suspend fun refreshScopeStatus() {
            ModernApp.refreshScope()
            delay(300)
            scopeStatus = when {
                ModernApp.scopeKnown && ModernApp.scopeGranted -> "已啟用"
                ModernApp.scopeKnown -> "尚未授權遊戲"
                else -> "尚未連接"
            }
        }
        fun checkScopeStatus() {
            scope.launch {
                refreshScopeStatus()
                taskState.info("模組狀態已更新")
            }
        }
        fun checkRootStatus() {
            if (rootStatus == "正在請求") return
            rootStatus = "正在請求"
            scope.launch {
                val granted = withContext(Dispatchers.IO) { requestRoot() }
                rootStatus = if (granted) "已授權" else "未取得授權"
                if (granted) taskState.success("Root 權限已授予")
                else taskState.error("未取得 Root 權限，套用漢化時將無法寫入遊戲資料")
            }
        }
        LaunchedEffect(Unit) { refreshCatalog() }
        LaunchedEffect(page) {
            if (page == OVERVIEW || page == ONBOARDING) refreshScopeStatus()
        }
        val onboardingDone = appPrefs.getBoolean("onboarding_done", false)
        fun applyNavigation(next: NavigationState) {
            page = next.page
            navigationStack = next.stack
            navigationDirection = next.direction
        }
        fun navigateTo(target: Int) {
            val topIndex = topPages.indexOf(target)
            if (topIndex >= 0) {
                val pagerSettledAtTarget =
                    pagerState.settledPage == topIndex &&
                        pagerState.currentPageOffsetFraction == 0f
                if (
                    topIndex == topNavigationTarget &&
                    page in topPages &&
                    pagerSettledAtTarget
                ) return
                topNavigationTarget = topIndex
                topNavigationTransaction += 1
            }
            applyNavigation(NavigationState(page, navigationStack, navigationDirection).navigateTo(target))
        }
        fun navigateBack() {
            applyNavigation(NavigationState(page, navigationStack, navigationDirection).navigateBack())
        }
        LaunchedEffect(page, activeName, applying, targetLanguage.id) {
            if (page == OVERVIEW && !applying) {
                runtimeInspection = withContext(Dispatchers.IO) { translations.inspectRuntime() }
            }
        }
        fun changeTargetLanguage(language: OverrideLanguage) {
            if (applying || language == targetLanguage) return
            targetLanguage = language
            translations.setTargetLanguage(language)
            applying = true
            changingTargetLanguage = true
            applyProgress = ApplyProgress("正在切換覆蓋語言", 0, 1)
            taskState.launchTask {
                runCatching {
                    val packs = translations.downloaded()
                    withContext(Dispatchers.Main.immediate) { downloadedPacks = packs }
                    val activePack = packs.firstOrNull { it.name == activeName }
                    if (activePack != null) {
                        translations.apply(activePack) { value ->
                            withContext(Dispatchers.Main.immediate) { applyProgress = value }
                        }
                    }
                    activePack != null
                }.onSuccess {
                    activeScript = translations.activeScript()
                    taskState.success(if (!it) {
                        "覆蓋語言已設為 ${language.label}"
                    } else {
                        "已更新，重啟遊戲後生效"
                    })
                }.onFailure {
                    taskState.error("切換覆蓋語言失敗：${it.message}")
                }
                applying = false
                changingTargetLanguage = false
                applyProgress = null
            }
        }
        fun installDownloadedUpdate() {
            val apk = downloadedUpdate ?: return
            val uri = FileProvider.getUriForFile(this, "com.lcpatch.files", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { startActivity(intent) }
                .onFailure { taskState.error("無法開啟系統安裝畫面：" + it.message) }
        }

        lateinit var downloadAppUpdate: (AppRelease) -> Unit
        downloadAppUpdate = { release ->
            if (updateProgress == null) {
                taskState.clearError()
                updateProgress = TransferProgress(release.apkName, 0, -1, 0)
                taskState.launchTask {
                    runCatching {
                        updates.download(release) { value ->
                            withContext(Dispatchers.Main.immediate) { updateProgress = value }
                        }
                    }.onSuccess {
                        downloadedUpdate = it
                        updateProgress = null
                        taskState.success("LCPatch " + release.version + " 已下載，可以開始安裝")
                    }.onFailure {
                        updateProgress = null
                        taskState.error(
                            "更新下載失敗：" + (it.message ?: it.javaClass.simpleName)
                        ) { downloadAppUpdate(release) }
                    }
                }
            }
        }

        fun checkAppUpdate() {
            if (checkingUpdate) return
            checkingUpdate = true
            taskState.clearError()
            taskState.launchTask {
                runCatching { updates.latestRelease(includePrerelease = updateChannel == "beta") }
                    .onSuccess { release ->
                        latestRelease = release
                        taskState.success(
                            if (updates.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                                "發現新版本 " + release.version
                            } else "目前已是最新版"
                        )
                    }
                    .onFailure {
                        taskState.error(
                            "檢查更新失敗：" + (it.message ?: it.javaClass.simpleName)
                        ) { checkAppUpdate() }
                    }
                checkingUpdate = false
            }
        }

        lateinit var installTranslation: (TranslationEntry) -> Unit
        installTranslation = { entry ->
            if (transfer?.finished != false && !applying) {
                taskState.clearError()
                installingEntryKey = entry.url
                transfer = TransferProgress(entry.name, 0, -1, 0)
                taskState.launchTask {
                    try {
                        val downloaded = translations.download(
                            entry,
                            progress = { value ->
                                withContext(Dispatchers.Main.immediate) { transfer = value }
                            },
                            preparation = { value ->
                                withContext(Dispatchers.Main.immediate) { applyProgress = value }
                            }
                        )
                        applying = true
                        activeName = translations.apply(downloaded) { value ->
                            withContext(Dispatchers.Main.immediate) { applyProgress = value }
                        }
                        activeScript = translations.activeScript()
                        downloadedPacks = translations.downloaded()
                        transfer = null
                        taskState.success("已套用，重啟遊戲後生效")
                    } catch (error: Throwable) {
                        transfer = null
                        taskState.error(
                            "下載或套用失敗：" + (error.message ?: error.javaClass.simpleName)
                        ) { installTranslation(entry) }
                        LogRepository.append(
                            this@MainActivity,
                            "ERROR",
                            "translation.install_failed",
                            error.message ?: error.javaClass.simpleName
                        )
                    } finally {
                        applying = false
                        applyProgress = null
                        installingEntryKey = null
                    }
                }
            }
        }

        BackHandler(enabled = page != OVERVIEW && (page != ONBOARDING || onboardingDone)) {
            navigateBack()
        }

        val shellTarget = if (page in topPages) TOP_LEVEL_CONTAINER else page

        val renderPage: @Composable (Int, PaddingValues) -> Unit = { visiblePage, padding ->
            val visibleListState = when (visiblePage) {
                OVERVIEW -> overviewListState; SETTINGS -> settingsListState; LOGS -> logsListState
                ABOUT -> aboutListState; UPDATE -> updateListState; DOWNLOAD -> downloadListState; ONBOARDING -> onboardingListState
                DOWNLOADED -> downloadedListState; DISPLAY -> displayListState; else -> conversionListState
            }
            val visibleScrollBehavior = when (visiblePage) {
                OVERVIEW -> overviewScrollBehavior; SETTINGS -> settingsScrollBehavior; LOGS -> logsScrollBehavior
                ABOUT -> aboutScrollBehavior; UPDATE -> updateScrollBehavior; DOWNLOAD -> downloadScrollBehavior; ONBOARDING -> onboardingScrollBehavior
                DOWNLOADED -> downloadedScrollBehavior; DISPLAY -> displayScrollBehavior; else -> conversionScrollBehavior
            }
            val listModifier = if (visiblePage in topPages) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().nestedScroll(visibleScrollBehavior.nestedScrollConnection)
            }
            Box(Modifier.fillMaxSize()) {
                key(visiblePage) {
                    LazyColumn(
                        state = visibleListState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = padding.calculateTopPadding() +
                                if (visiblePage in topPages) {
                                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp
                                } else 12.dp,
                            bottom = padding.calculateBottomPadding() +
                                if (visiblePage in topPages && navigationStyle == "floating") 28.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (visiblePage) {
                            OVERVIEW -> overview(
                                overlayState = environmentOverlayState,
                                game = game,
                                events = events,
                                activeName = activeName,
                                activeScript = activeScript,
                                scopeStatus = scopeStatus,
                                rootStatus = rootStatus,
                                onCheckScope = ::checkScopeStatus,
                                onRequestRoot = ::checkRootStatus,
                                translationEnabled = translationEnabled,
                                onTranslationEnabled = { enabled ->
                                    scope.launch {
                                        runCatching { translations.setTranslationEnabled(enabled) }
                                            .onSuccess { translationEnabled = enabled; taskState.success("設定已更新，重啟遊戲後生效") }
                                            .onFailure { taskState.error("切換失敗：${it.message}") }
                                    }
                                },
                                targetLanguage = targetLanguage,
                                runtimeInspection = runtimeInspection,
                                onDownload = { navigateTo(DOWNLOAD) },
                                onDownloaded = { navigateTo(DOWNLOADED) }
                            )
                            SETTINGS -> settings(
                                applyProgress = applyProgress,
                                applying = applying,
                                changingTargetLanguage = changingTargetLanguage,
                                onFolder = { folderPicker.launch(logs.selectedFolder()) },
                                onAbout = { navigateTo(ABOUT) },
                                onUpdate = { navigateTo(UPDATE) },
                                onDisplay = { navigateTo(DISPLAY) },
                                onConversion = { navigateTo(CONVERSION) },
                                targetLanguage = targetLanguage,
                                fontName = fontName,
                                onLanguage = ::changeTargetLanguage,
                                onFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
                                onClearFont = { translations.clearCustomFont(); fontName = translations.fontName(); taskState.success("已改回漢化包內字體") },
                                onImport = { customPackPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                onReset = { logs.resetSelectedFolder(); revision++; taskState.success("已改回預設暫存位置") }
                            )
                            LOGS -> logPage(
                                events,
                                save = {
                                    try { logs.saveDiagnostic(events); taskState.success("診斷文件已儲存") }
                                    catch (error: Throwable) { taskState.error("儲存失敗：${error.message}") }
                                },
                                share = {
                                    try { startActivity(logs.shareIntent(events)) }
                                    catch (error: Throwable) { taskState.error("分享失敗：${error.message}") }
                                },
                                clear = { logs.clear(); revision++; taskState.success("日誌已清除") }
                            )
                            ABOUT -> aboutPage(game)
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
                            DISPLAY -> displaySettings(
                                themeMode = themeMode,
                                navigationStyle = navigationStyle,
                                onNavigationStyle = { navigationStyle = it; appPrefs.edit().putString("navigation_style", it).apply() },
                                blurEnabled = blurEnabled,
                                onBlurEnabled = { enabled -> blurEnabled = enabled; appPrefs.edit().putBoolean("blur_enabled", enabled).apply() },
                                onThemeMode = onThemeMode
                            )
                            ONBOARDING -> onboardingPage(
                                scopeStatus = scopeStatus,
                                rootStatus = rootStatus,
                                onCheckScope = ::checkScopeStatus,
                                onRequestRoot = ::checkRootStatus,
                                onDone = {
                                    appPrefs.edit().putBoolean("onboarding_done", true).apply()
                                    navigateTo(OVERVIEW)
                                }
                            )
                            DOWNLOAD -> downloadPage(
                                catalog,
                                catalogLoading,
                                catalogError,
                                transfer,
                                applyProgress,
                                applying,
                                installingEntryKey,
                                refresh = ::refreshCatalog,
                                install = installTranslation
                            )
                            DOWNLOADED -> downloadedPage(
                                downloadedPacks,
                                activeName,
                                applyProgress,
                                applying,
                                processingPackPath,
                                applyingPackPath,
                                convert = { pack, conversion ->
                                    if (!applying) {
                                        applying = true
                                        processingPackPath = pack.path
                                        taskState.launchTask {
                                            runCatching { translations.convertDownloaded(pack, conversion) { value -> withContext(Dispatchers.Main.immediate) { applyProgress = value } } }
                                                .onSuccess {
                                                    val newScript = if (conversion.id == "traditional") "繁體" else "簡體"
                                                    downloadedPacks = translations.downloaded().map {
                                                        if (it.path == pack.path) it.copy(script = newScript) else it
                                                    }
                                                    if (pack.name == activeName) {
                                                        translations.apply(pack.copy(script = newScript)) { value ->
                                                            withContext(Dispatchers.Main.immediate) { applyProgress = value }
                                                        }
                                                        activeScript = translations.activeScript()
                                                    }
                                                    taskState.success(if (pack.name == activeName) "轉換完成，重啟遊戲後生效" else "轉換完成")
                                                }
                                                .onFailure {
                                                    android.util.Log.e("LCPatch", "Text conversion failed", it)
                                                    taskState.error("轉換失敗：${it.message}")
                                                }
                                            applying = false
                                            applyProgress = null
                                            processingPackPath = null
                                        }
                                    } else taskState.info("請等待目前的漢化處理完成")
                                }
                            ) { pack ->
                                if (!applying) {
                                    applying = true
                                    applyingPackPath = pack.path
                                    taskState.launchTask {
                                        runCatching { translations.apply(pack) { value -> withContext(Dispatchers.Main.immediate) { applyProgress = value } } }
                                            .onSuccess { activeName = it; activeScript = translations.activeScript(); taskState.success("已套用，重啟遊戲後生效") }
                                            .onFailure { taskState.error("套用失敗：${it.message}") }
                                        applying = false
                                        applyingPackPath = null
                                        applyProgress = null
                                    }
                                }
                            }
                            CONVERSION -> conversionPage(downloadedPacks, applyProgress, processingPackPath) { pack, conversion ->
                                if (!applying) {
                                    applying = true
                                    processingPackPath = pack.path
                                    taskState.launchTask {
                                        runCatching { translations.convertDownloaded(pack, conversion) { value -> withContext(Dispatchers.Main.immediate) { applyProgress = value } } }
                                            .onSuccess {
                                                val newScript = if (conversion.id == "traditional") "繁體" else "簡體"
                                                downloadedPacks = translations.downloaded().map {
                                                    if (it.path == pack.path) it.copy(script = newScript) else it
                                                }
                                                if (pack.name == activeName) {
                                                    translations.apply(pack.copy(script = newScript)) { value ->
                                                        withContext(Dispatchers.Main.immediate) { applyProgress = value }
                                                    }
                                                    activeScript = translations.activeScript()
                                                }
                                                taskState.success(if (pack.name == activeName) "轉換完成，重啟遊戲後生效" else "轉換完成")
                                            }
                                            .onFailure {
                                                android.util.Log.e("LCPatch", "Text conversion failed", it)
                                                taskState.error("轉換失敗：${it.message}")
                                            }
                                        applying = false
                                        applyProgress = null
                                        processingPackPath = null
                                    }
                                } else taskState.info("請等待目前的漢化處理完成")
                            }
                        }
                    }
                }
            }
        }

        val renderNotice: @Composable (PaddingValues) -> Unit = { padding ->
            Box(Modifier.fillMaxSize()) {
                notice?.let { currentNotice ->
                    val noticeModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = padding.calculateBottomPadding() + 22.dp
                        )
                    if (currentNotice.kind == UiNoticeKind.Error) {
                        Card(
                            modifier = noticeModifier,
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.error.copy(alpha = 0.14f)
                            )
                        ) {
                            Text(currentNotice.message, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (taskState.retryAction != null) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        text = "重試",
                                        onClick = taskState::retry
                                    )
                                }
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = "查看日誌",
                                    onClick = { navigateTo(LOGS); taskState.dismissNotice() }
                                )
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = "關閉",
                                    onClick = taskState::dismissNotice
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = noticeModifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                currentNotice.message,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = shellTarget,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = navigationDirection
                    slideInHorizontally(tween(320, easing = PageTransitionEasing)) { direction * it } togetherWith
                        slideOutHorizontally(tween(320, easing = PageTransitionEasing)) { -direction * it }
                },
                label = "screen-transition"
            ) { animatedShell ->
            // AnimatedContent keeps outgoing and incoming branches alive together.
            // Each branch must own its recording layer so they never write into
            // the same GraphicsLayer during a transition.
            val branchBarBackdrop = rememberBarBackdrop()
            val activeBarBackdrop = if (blurEnabled) branchBarBackdrop else null
            val topLevelScreen = animatedShell == TOP_LEVEL_CONTAINER
            val visiblePage = if (topLevelScreen) page else animatedShell
            val visibleTitle = pageTitle(visiblePage)
            val visibleScrollBehavior = when (visiblePage) {
                OVERVIEW -> overviewScrollBehavior
                SETTINGS -> settingsScrollBehavior
                LOGS -> logsScrollBehavior
                ABOUT -> aboutScrollBehavior
                UPDATE -> updateScrollBehavior
                DOWNLOAD -> downloadScrollBehavior
                ONBOARDING -> onboardingScrollBehavior
                DOWNLOADED -> downloadedScrollBehavior
                DISPLAY -> displayScrollBehavior
                else -> conversionScrollBehavior
            }

            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
                    topBar = {
                        if (!topLevelScreen) {
                            TintedBar(activeBarBackdrop) {
                                val navigationIcon: @Composable () -> Unit = {
                                    if (visiblePage != ONBOARDING || onboardingDone) {
                                        IconButton(onClick = ::navigateBack) {
                                            Icon(MiuixIcons.Back, contentDescription = "返回")
                                        }
                                    }
                                }
                                SmallTopAppBar(
                                    title = visibleTitle,
                                    color = Color.Transparent,
                                    scrollBehavior = visibleScrollBehavior,
                                    navigationIcon = navigationIcon
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (topLevelScreen) {
                            if (navigationStyle == "floating") {
                                SukiFloatingBottomBar(
                                    currentIndex = pagerState.settledPage,
                                    targetIndex = topNavigationTarget,
                                    transactionId = topNavigationTransaction,
                                    pagePosition = pagePosition,
                                    onTargetSelected = { index ->
                                        topPages.getOrNull(index)?.let(::navigateTo)
                                    },
                                    backdrop = activeBarBackdrop
                                )
                            } else {
                                TintedBar(activeBarBackdrop) {
                                    NavigationBar(color = Color.Transparent) {
                                        NavigationBarItem(selected = pagerState.currentPage == 0, onClick = { navigateTo(OVERVIEW) }, icon = MiuixIcons.Home, label = "概觀")
                                        NavigationBarItem(selected = pagerState.currentPage == 1, onClick = { navigateTo(LOGS) }, icon = Icons.Default.List, label = "日誌")
                                        NavigationBarItem(selected = pagerState.currentPage == 2, onClick = { navigateTo(SETTINGS) }, icon = MiuixIcons.Settings, label = "設定")
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (activeBarBackdrop != null) Modifier.layerBackdrop(activeBarBackdrop) else Modifier
                            )
                    ) {
                        if (topLevelScreen) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                userScrollEnabled = false,
                                key = { topPages[it] }
                            ) { index ->
                                renderPage(topPages[index], padding)
                            }
                        } else {
                            renderPage(animatedShell, padding)
                        }
                        if (animatedShell == shellTarget) renderNotice(padding)
                    }
                }
                if (topLevelScreen) {
                    TopLevelProgressiveBar(
                        backdrop = activeBarBackdrop,
                        pageTitles = topPages.map(::pageTitle),
                        pagePosition = pagePosition
                    )
                }
            }
            }

            EnvironmentStatusOverlayHost(
                state = environmentOverlayState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun LazyListScope.overview(
        overlayState: EnvironmentStatusOverlayState,
        game: GameInfo, events: List<LogEvent>, activeName: String, activeScript: String,
        scopeStatus: String, rootStatus: String,
        onCheckScope: () -> Unit, onRequestRoot: () -> Unit,
        translationEnabled: Boolean, onTranslationEnabled: (Boolean) -> Unit,
        targetLanguage: OverrideLanguage, runtimeInspection: RuntimeInspection,
        onDownload: () -> Unit, onDownloaded: () -> Unit
    ) {
        item {
            EnvironmentStatusOverviewCard(
                overlayState = overlayState,
                scopeStatus = scopeStatus,
                rootStatus = rootStatus,
                gameInstalled = game.installed,
                gameVersion = game.version,
                onCheckScope = onCheckScope,
                onRequestRoot = onRequestRoot
            )
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("當前漢化", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(6.dp))
                Text("$activeName · $activeScript", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (runtimeInspection.ready && runtimeInspection.fontReady) {
                        "遊戲快取已就緒 · ${runtimeInspection.jsonFiles} 個 JSON · ${runtimeInspection.matchingFiles} 個 ${targetLanguage.prefix} 文件 · 字型 ${formatBytes(runtimeInspection.fontBytes)}"
                    } else {
                        "遊戲快取未就緒 · JSON ${runtimeInspection.jsonFiles} · ${targetLanguage.prefix} 文件 ${runtimeInspection.matchingFiles} · ${if (runtimeInspection.fontReady) "字型已寫入" else "缺少中文字型"}"
                    },
                    color = if (runtimeInspection.ready && runtimeInspection.fontReady) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                Text("遊戲內語言需設為 ${targetLanguage.label}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                SwitchPreference(
                    modifier = PreferenceItemModifier,
                    title = "應用漢化",
                    summary = if (translationEnabled) "已啟用，重新啟動遊戲後載入" else "已停用，下載內容仍會保留",
                    checked = translationEnabled,
                    onCheckedChange = onTranslationEnabled
                )
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) { Text("下載漢化") }
                Spacer(Modifier.height(8.dp))
                TextButton(modifier = Modifier.fillMaxWidth(), text = "選擇已下載漢化", onClick = onDownloaded)
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("遊戲", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                Detail("Limbus Company", if (game.installed) "已安裝" else "未安裝")
                Detail("版本", game.version)
                Detail("字型相容性", FontProfiles.status(game.versionCode))
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), enabled = game.installed, onClick = ::launchGame) { Text("啟動 Limbus Company") }
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("裝置資訊", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(8.dp))
                Detail("機型", "${Build.MANUFACTURER} ${Build.MODEL}")
                Detail("裝置", Build.DEVICE)
                Detail("系統版本", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                Detail("架構", Build.SUPPORTED_ABIS.joinToString())
            }
        }
    }

    private fun LazyListScope.settings(
        onFolder: () -> Unit, onAbout: () -> Unit, onUpdate: () -> Unit,
        onDisplay: () -> Unit, onConversion: () -> Unit, targetLanguage: OverrideLanguage,
        applyProgress: ApplyProgress?, applying: Boolean, changingTargetLanguage: Boolean,
        fontName: String, onLanguage: (OverrideLanguage) -> Unit,
        onFont: () -> Unit, onClearFont: () -> Unit, onImport: () -> Unit, onReset: () -> Unit
    ) {
        item { SectionLabel("漢化") }
        item {
            Card {
                OverlayDropdownPreference(
                    title = if (changingTargetLanguage) "正在切換覆蓋語言…" else "覆蓋語言文件",
                    modifier = PreferenceItemModifier,
                    enabled = !applying,
                    items = TranslationRepository.LANGUAGES.map { it.label },
                    selectedIndex = TranslationRepository.LANGUAGES.indexOf(targetLanguage).coerceAtLeast(0),
                    onSelectedIndexChange = { index -> TranslationRepository.LANGUAGES.getOrNull(index)?.let(onLanguage) }
                )
                if (changingTargetLanguage) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        progress = applyProgress?.fraction
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ArrowPreference(modifier = PreferenceItemModifier, title = "繁簡轉換", summary = "轉換已下載漢化目錄內的 JSON 文件", onClick = onConversion)
                ArrowPreference(modifier = PreferenceItemModifier, title = "匯入自訂漢化文件", summary = "從本機加入 ZIP 漢化包", onClick = onImport)
                ArrowPreference(modifier = PreferenceItemModifier, title = "更換字體", summary = fontName, onClick = onFont)
            }
        }
        if (fontName != "使用漢化包內字體") item { TextButton(modifier = Modifier.fillMaxWidth(), text = "移除自訂字體", onClick = onClearFont) }
        item { SectionLabel("應用程式") }
        item {
            Card {
                ArrowPreference(modifier = PreferenceItemModifier, title = "介面與顯示", summary = "主題、模糊效果與底欄", onClick = onDisplay)
                ArrowPreference(modifier = PreferenceItemModifier, title = "診斷文件儲存位置", summary = logs.selectedFolderLabel(), onClick = onFolder)
                ArrowPreference(modifier = PreferenceItemModifier, title = "應用程式更新", summary = "更新渠道與檢查更新", onClick = onUpdate)
                ArrowPreference(modifier = PreferenceItemModifier, title = "關於", summary = "版本、元件與相容策略", onClick = onAbout)
            }
        }
        if (logs.selectedFolder() != null) item { TextButton(modifier = Modifier.fillMaxWidth(), text = "改回預設暫存位置", onClick = onReset) }
    }

    private fun LazyListScope.displaySettings(
        themeMode: String,
        navigationStyle: String,
        onNavigationStyle: (String) -> Unit,
        blurEnabled: Boolean,
        onBlurEnabled: (Boolean) -> Unit,
        onThemeMode: (String) -> Unit
    ) {
        item {
            Card {
                OverlayDropdownPreference(
                    modifier = PreferenceItemModifier,
                    title = "深淺模式",
                    items = listOf("跟隨系統", "淺色", "深色"),
                    selectedIndex = when (themeMode) { "light" -> 1; "dark" -> 2; else -> 0 },
                    onSelectedIndexChange = { onThemeMode(when (it) { 1 -> "light"; 2 -> "dark"; else -> "system" }) }
                )
                OverlayDropdownPreference(
                    modifier = PreferenceItemModifier,
                    title = "底欄樣式",
                    items = listOf("標準底欄", "懸浮底欄"),
                    selectedIndex = if (navigationStyle == "floating") 1 else 0,
                    onSelectedIndexChange = { onNavigationStyle(if (it == 1) "floating" else "standard") }
                )
                SwitchPreference(
                    modifier = PreferenceItemModifier,
                    title = "背景模糊",
                    summary = if (blurEnabled) "已啟用；頂欄與底欄保持即時模糊" else "已停用；使用穩定的半透明背景",
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabled
                )
            }
        }
        item { InfoCard("顯示效果", "可選擇使用背景模糊；關閉後頂欄與底欄會改用穩定的半透明背景。") }
    }

    private fun LazyListScope.logPage(events: List<LogEvent>, save: () -> Unit, share: () -> Unit, clear: () -> Unit) {
        item {
            Card(insideMargin = PaddingValues(16.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = share) { Text("分享診斷文件") }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(modifier = Modifier.weight(1f), text = "儲存", onClick = save)
                    TextButton(modifier = Modifier.weight(1f), text = "清除", enabled = events.isNotEmpty(), onClick = clear)
                }
            }
        }
        if (events.isEmpty()) item { InfoCard("尚無日誌", "啟用模組並啟動一次遊戲後，再回到這裡匯出診斷文件。") }
        else items(events) { LogCard(it) }
    }

    private fun LazyListScope.aboutPage(game: GameInfo) {
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
                    newer -> {
                        val fraction = updateProgress?.let { progress ->
                            progress.total.takeIf { it > 0 }?.let {
                                (progress.bytes.toFloat() / it).coerceIn(0f, 1f)
                            }
                        }
                        ProgressActionButton(
                            text = if (updateProgress == null) "下載更新"
                                else progressDownloadLabel(fraction),
                            progress = fraction,
                            busy = updateProgress != null,
                            onClick = downloadUpdate
                        )
                    }
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

    private fun LazyListScope.onboardingPage(
        scopeStatus: String,
        rootStatus: String,
        onCheckScope: () -> Unit,
        onRequestRoot: () -> Unit,
        onDone: () -> Unit
    ) {
        item { InfoCard("歡迎使用 LCPatch", "完成模組作用域與 Root 權限設定後，即可在 App 內下載並套用漢化。") }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("1 · 模組作用域", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(6.dp))
                Text("檢查 LCPatch 模組與 Limbus Company 作用域，不會跳轉至其他 App。\n目前狀態：$scopeStatus", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onCheckScope) { Text("重新檢查") }
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("2 · Root 權限", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(6.dp))
                Text("檢查 LCPatch 是否能使用 Root 權限，不會開啟其他 App。\n目前狀態：$rootStatus", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(14.dp))
                Button(modifier = Modifier.fillMaxWidth(), enabled = rootStatus != "正在請求", onClick = onRequestRoot) { Text(if (rootStatus == "正在請求") "正在檢查…" else "檢查 Root 權限") }
            }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onDone) { Text("完成設定") }
            Spacer(Modifier.height(8.dp))
            Text("若模組尚未回報狀態，可先完成設定；重新啟動遊戲後，概觀頁會自動更新。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
        }
    }

    @Composable private fun Detail(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary); Text(value)
        }
        Spacer(Modifier.height(6.dp))
    }

    @Composable
    private fun ProgressActionButton(
        text: String,
        progress: Float? = null,
        busy: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(16.dp)
        val primary = MiuixTheme.colorScheme.primary
        val fraction = progress?.coerceIn(0f, 1f)
        val trackColor = when {
            busy -> primary.copy(alpha = 0.16f)
            enabled -> primary
            else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        }
        val textColor = when {
            !enabled && !busy -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            primary.luminance() > 0.56f -> Color(0xFF151518)
            else -> Color.White
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(shape)
                .background(trackColor)
                .clickable(enabled = enabled && !busy, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (busy && fraction != null && fraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(primary)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    @Composable private fun InfoCard(title: String, body: String, translucent: Boolean = false) {
        Card(
            insideMargin = PaddingValues(18.dp),
            colors = if (translucent) translucentAboutCardColors() else CardDefaults.defaultColors()
        ) {
            Text(title, style = MiuixTheme.textStyles.title2); Spacer(Modifier.height(6.dp)); Text(body, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    @Composable private fun translucentAboutCardColors() = CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (isSystemInDarkTheme()) 0.76f else 0.82f)
    )

    @Composable private fun SectionLabel(text: String) {
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 2.dp),
            color = MiuixTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    @Composable private fun Modifier.aboutShimmer(): Modifier {
        val transition = rememberInfiniteTransition(label = "about-shimmer")
        val position by transition.animateFloat(
            initialValue = -420f,
            targetValue = 1640f,
            animationSpec = infiniteRepeatable(animation = tween(4300), repeatMode = RepeatMode.Reverse),
            label = "about-shimmer-position"
        )
        val glow = MiuixTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.48f else 0.34f)
        val violet = Color(0xFF9B6DFF).copy(alpha = if (isSystemInDarkTheme()) 0.42f else 0.30f)
        return drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glow.copy(alpha = glow.alpha * 0.72f),
                        violet.copy(alpha = violet.alpha * 0.56f),
                        glow.copy(alpha = glow.alpha * 0.18f),
                        Color.Transparent
                    ),
                    endY = size.height * 0.82f
                )
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        glow.copy(alpha = glow.alpha * 0.42f),
                        violet,
                        glow,
                        violet.copy(alpha = violet.alpha * 0.36f),
                        Color.Transparent
                    ),
                    start = Offset(position - size.width * 1.15f, 0f),
                    end = Offset(position + size.width * 0.65f, size.height)
                )
            )
        }
    }

    @Composable private fun LogCard(event: LogEvent) {
        Card(insideMargin = PaddingValues(16.dp)) {
            Text("${event.level} · ${event.code}", fontWeight = FontWeight.SemiBold,
                color = when (event.level) { "ERROR" -> Color(0xFFE5484D); "WARN" -> Color(0xFFF59E0B); else -> MiuixTheme.colorScheme.primary })
            Spacer(Modifier.height(4.dp)); Text(event.message); Spacer(Modifier.height(6.dp))
            Text("${event.time}${if (event.process.isBlank()) "" else " · ${event.process}"}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    private fun gameInfo(): GameInfo = try {
        val value = packageManager.getPackageInfo(LogProvider.GAME, 0)
        GameInfo(true, "${value.versionName} (${value.longVersionCode})", value.longVersionCode)
    } catch (_: Exception) { GameInfo(false, "無法讀取", -1L) }

    private fun contentName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "file"
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun launchGame() {
        Thread({
            val prepared = runCatching { translations.prepareGameLaunch() }.getOrDefault(false)
            if (!prepared) LogRepository.append(this, "ERROR", "game.prepare_failed", "啟動前無法同步漢化；將以遊戲現有文件啟動")
            Thread.sleep(350)
            runOnUiThread {
                try { startActivity(packageManager.getLaunchIntentForPackage(LogProvider.GAME) ?: throw ActivityNotFoundException()) }
                catch (_: ActivityNotFoundException) { LogRepository.append(this, "ERROR", "game.missing", "找不到遊戲啟動入口") }
            }
        }, "LCPatch-game-restart").start()
    }

    private fun requestRoot(): Boolean = RootShell.run("id", timeoutMs = 15_000L).success

    private fun LazyListScope.downloadPage(
        entries: List<TranslationEntry>, loading: Boolean, error: String?, transfer: TransferProgress?,
        applyProgress: ApplyProgress?, applying: Boolean, installingEntryKey: String?,
        refresh: () -> Unit, install: (TranslationEntry) -> Unit
    ) {
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("漢化資源", style = MiuixTheme.textStyles.title1)
                Spacer(Modifier.height(6.dp))
                Text(when { loading -> "正在取得可用漢化…"; error != null -> error; entries.isEmpty() -> "目前沒有可用項目"; else -> "已找到 ${entries.size} 個漢化版本" }, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (!loading) { Spacer(Modifier.height(8.dp)); TextButton(modifier = Modifier.fillMaxWidth(), text = "重新整理", onClick = refresh) }
            }
        }
        items(entries) { entry ->
            Card(insideMargin = PaddingValues(18.dp)) {
                Text(entry.name, style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(4.dp)); Text("${entry.author} · ${entry.section}", color = MiuixTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp)); Text(entry.description, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
                val ownsTask = installingEntryKey == entry.url
                val downloadFraction = transfer?.takeIf { ownsTask }?.let { value ->
                    value.total.takeIf { it > 0 }?.let {
                        (value.bytes.toFloat() / it).coerceIn(0f, 1f)
                    }
                }
                val postDownload = ownsTask && (transfer?.finished == true || applyProgress != null || applying)
                val buttonProgress = when {
                    postDownload -> 1f
                    ownsTask -> downloadFraction
                    else -> null
                }
                val buttonText = when {
                    !ownsTask -> "下載並套用"
                    postDownload -> progressStageLabel(applyProgress?.stage)
                    else -> progressDownloadLabel(downloadFraction)
                }
                ProgressActionButton(
                    text = buttonText,
                    progress = buttonProgress,
                    busy = ownsTask,
                    enabled = installingEntryKey == null && transfer?.finished != false && !applying,
                    onClick = { install(entry) }
                )
            }
        }
    }

    private fun LazyListScope.downloadedPage(
        entries: List<DownloadedTranslation>, activeName: String, applyProgress: ApplyProgress?, applying: Boolean,
        processingPackPath: String?, applyingPackPath: String?,
        convert: (DownloadedTranslation, TextConversion) -> Unit,
        apply: (DownloadedTranslation) -> Unit
    ) {
        if (entries.isEmpty()) {
            item { InfoCard("尚無已下載漢化", "請先從「下載漢化」取得漢化包。下載完成後會預設立即套用，也會保留在此供日後切換。") }
        } else {
            item { InfoCard("本機漢化", "選擇已下載的漢化包即可直接切換，不需要重新下載。") }
            items(entries) { entry ->
                val active = entry.name == activeName
                var conversionIndex by rememberSaveable(entry.path, "downloaded-conversion") {
                    mutableIntStateOf(if (entry.script == "簡體") 1 else 0)
                }
                LaunchedEffect(entry.script) { conversionIndex = if (entry.script == "簡體") 1 else 0 }
                Card(insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                    Text(entry.name, style = MiuixTheme.textStyles.title2, color = if (active) MiuixTheme.colorScheme.primary else Color.Unspecified)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (active) "已選擇 · 目前套用中" else File(entry.path).name,
                        color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(12.dp))
                    val buttonText = when {
                        active -> "已套用"
                        applyingPackPath == entry.path -> "正在套用…"
                        processingPackPath == entry.path -> "正在轉換…"
                        else -> "套用此漢化"
                    }
                    val applyBusy = applyingPackPath == entry.path
                    ProgressActionButton(
                        text = buttonText,
                        progress = applyProgress?.fraction?.takeIf { applyBusy },
                        busy = applyBusy,
                        enabled = !active && !applying,
                        onClick = { apply(entry) }
                    )
                    Spacer(Modifier.height(6.dp))
                    OverlayDropdownPreference(
                        modifier = PreferenceItemModifier,
                        title = "繁簡轉換",
                        enabled = processingPackPath != entry.path,
                        items = TranslationRepository.CONVERSIONS.map { it.label.removePrefix("轉為") },
                        selectedIndex = conversionIndex,
                        onSelectedIndexChange = { index ->
                            conversionIndex = index
                            TranslationRepository.CONVERSIONS.getOrNull(index)?.let { convert(entry, it) }
                        }
                    )
                    if (processingPackPath == entry.path) applyProgress?.let { value ->
                        Spacer(Modifier.height(8.dp))
                        Text("${value.stage}：${entry.name}", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (value.total > 0) "已處理 ${value.current} / ${value.total} 個 JSON" else "正在掃描此漢化目錄…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "文字類型：${entry.script} · ${if (entry.puaPrepared) "PUA 已完成" else "首次套用時轉換 PUA"}",
                        color = if (entry.puaPrepared) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        fontWeight = if (entry.puaPrepared) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }

    private fun LazyListScope.conversionPage(
        entries: List<DownloadedTranslation>,
        progress: ApplyProgress?,
        processingPackPath: String?,
        convert: (DownloadedTranslation, TextConversion) -> Unit
    ) {
        if (entries.isEmpty()) {
            item { InfoCard("尚無可轉換的漢化", "請先下載或匯入漢化。下載完成後，漢化會先解壓到 /sdcard/LCPatch/漢化 的獨立目錄。") }
        } else {
            item { InfoCard("選擇漢化目錄", "只會轉換你選中的已下載目錄；若它正是目前套用的漢化，完成後會自動重新套用。") }
            items(entries) { entry ->
                var conversionIndex by rememberSaveable(entry.path, "conversion-page") {
                    mutableIntStateOf(if (entry.script == "簡體") 1 else 0)
                }
                LaunchedEffect(entry.script) { conversionIndex = if (entry.script == "簡體") 1 else 0 }
                Card(insideMargin = PaddingValues(18.dp)) {
                    Text(entry.name, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(5.dp))
                    Text(entry.path, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OverlayDropdownPreference(
                        modifier = PreferenceItemModifier,
                        title = if (processingPackPath == entry.path) "正在轉換…" else "繁簡轉換",
                        enabled = processingPackPath != entry.path,
                        items = TranslationRepository.CONVERSIONS.map { it.label.removePrefix("轉為") },
                        selectedIndex = conversionIndex,
                        onSelectedIndexChange = { index ->
                            conversionIndex = index
                            TranslationRepository.CONVERSIONS.getOrNull(index)?.let { convert(entry, it) }
                        }
                    )
                    if (processingPackPath == entry.path) progress?.let { value ->
                        Spacer(Modifier.height(8.dp))
                        Text("${value.stage}：${entry.name}", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (value.total > 0) "已處理 ${value.current} / ${value.total} 個 JSON" else "正在掃描此漢化目錄…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "目前：${entry.script} · ${if (entry.puaPrepared) "PUA 已完成" else "尚未轉換 PUA"}",
                        color = if (entry.puaPrepared) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        fontWeight = if (entry.puaPrepared) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }

    private fun friendlyError(event: LogEvent): String = when (event.code) {
        "native.failed" -> "模組元件載入失敗，請更新或重新安裝 LCPatch"
        "locator.library_missing" -> "遊戲元件尚未準備完成，請重新啟動遊戲"
        "locator.ambiguous" -> "目前遊戲版本尚未支援，請等待相容更新"
        "hook.install_failed" -> "字型套用失敗，請重新啟動遊戲"
        "hook.engine_missing" -> "字型掛鉤核心不可用，本次已安全停用"
        else -> "設定尚未完成，可前往設定查看診斷"
    }
}

private data class GameInfo(val installed: Boolean, val version: String, val versionCode: Long)
private fun progressDownloadLabel(progress: Float?): String =
    progress?.let { "正在下載 ${(it.coerceIn(0f, 1f) * 100).roundToInt()}%" } ?: "正在下載…"

private fun progressStageLabel(stage: String?): String = when {
    stage == null -> "正在準備…"
    "套用" in stage -> "正在套用…"
    "準備" in stage || "解壓" in stage || "掃描" in stage -> "正在準備…"
    else -> "正在處理…"
}

private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    else -> "%.1f MB".format(value / 1024.0 / 1024.0)
}
