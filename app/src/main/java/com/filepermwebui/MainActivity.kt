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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.io.File
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
import top.yukonga.miuix.kmp.blur.layerBackdrop

private const val OVERVIEW = 0
private const val SETTINGS = 1
private const val LOGS = 2
private const val ABOUT = 3
private const val DOWNLOAD = 4
private const val ONBOARDING = 5
private const val DOWNLOADED = 6
private const val DISPLAY = 7
private const val CONVERSION = 8
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
                ThemeController(when (themeMode) { "light" -> ColorSchemeMode.Light; "dark" -> ColorSchemeMode.Dark; else -> ColorSchemeMode.System })
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
        val topPages = remember { listOf(OVERVIEW, LOGS, SETTINGS) }
        val pagerState = rememberPagerState(initialPage = topPages.indexOf(page).coerceAtLeast(0), pageCount = { topPages.size })
        var revision by remember { mutableIntStateOf(0) }
        var message by taskState.message
        var messageIsError by taskState.messageIsError
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
        var updateChannel by remember { mutableStateOf(appPrefs.getString("update_channel", "stable") ?: "stable") }
        val overviewListState = rememberLazyListState()
        val settingsListState = rememberLazyListState()
        val logsListState = rememberLazyListState()
        val aboutListState = rememberLazyListState()
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
        val downloadScrollBehavior = MiuixScrollBehavior()
        val onboardingScrollBehavior = MiuixScrollBehavior()
        val downloadedScrollBehavior = MiuixScrollBehavior()
        val displayScrollBehavior = MiuixScrollBehavior()
        val conversionScrollBehavior = MiuixScrollBehavior()
        val scrollBehavior = when (page) {
            OVERVIEW -> overviewScrollBehavior
            SETTINGS -> settingsScrollBehavior
            LOGS -> logsScrollBehavior
            ABOUT -> aboutScrollBehavior
            DOWNLOAD -> downloadScrollBehavior
            ONBOARDING -> onboardingScrollBehavior
            DOWNLOADED -> downloadedScrollBehavior
            DISPLAY -> displayScrollBehavior
            else -> conversionScrollBehavior
        }
        val barBackdrop = rememberBarBackdrop()
        val scope = rememberCoroutineScope()
        LaunchedEffect(page) {
            val target = topPages.indexOf(page)
            if (target >= 0 && pagerState.currentPage != target) pagerState.animateScrollToPage(target)
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { settled ->
                if (page in topPages) page = topPages[settled]
            }
        }
        val events = remember(revision) { logs.read() }
        val game = remember { gameInfo() }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    logs.setSelectedFolder(uri)
                    message = "已更新診斷文件儲存位置"
                } catch (error: Throwable) {
                    message = "無法保存資料夾授權：${error.message}"
                }
                revision++
            }
        }
        val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching { withContext(Dispatchers.IO) { translations.setCustomFont(uri, contentName(uri)) } }
                    .onSuccess { fontName = translations.fontName(); message = "已選擇字體：$fontName" }
                    .onFailure { message = "字體匯入失敗：${it.message}" }
            }
        }
        val customPackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching { translations.importCustom(uri, contentName(uri)) }
                    .onSuccess { downloadedPacks = translations.downloaded(); message = "已匯入 ${it.name}" }
                    .onFailure { message = "漢化匯入失敗：${it.message}" }
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
        LaunchedEffect(message, messageIsError) {
            if (message != null && !messageIsError) {
                delay(6000)
                message = null
            }
        }
        LaunchedEffect(Unit) {
            refreshCatalog()
            while (true) {
                ModernApp.refreshScope()
                delay(300)
                scopeStatus = when { ModernApp.scopeKnown && ModernApp.scopeGranted -> "已啟用"; ModernApp.scopeKnown -> "尚未授權遊戲"; else -> "尚未連接" }
                delay(1700)
            }
        }
        val title = when (page) { OVERVIEW -> "LCPatch"; SETTINGS -> "設定"; LOGS -> "日誌"; ABOUT -> "關於"; DOWNLOAD -> "下載漢化"; DOWNLOADED -> "選擇套用"; DISPLAY -> "介面與顯示"; CONVERSION -> "繁簡轉換"; else -> "開始使用" }
        val onboardingDone = appPrefs.getBoolean("onboarding_done", false)
        fun navigateBack() {
            page = parentPage(page)
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
                    message = if (!it) {
                        "已將覆蓋語言設為 ${language.label}，套用漢化時生效"
                    } else {
                        "已改為覆蓋 ${language.label}，請重新啟動遊戲"
                    }
                }.onFailure {
                    message = "切換覆蓋語言失敗：${it.message}"
                }
                applying = false
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
                        taskState.success("已下載並套用 " + activeName + "，請重新啟動遊戲")
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
                    }
                }
            }
        }

        BackHandler(enabled = page != OVERVIEW && (page != ONBOARDING || onboardingDone)) {
            page = if (page == SETTINGS || page == LOGS) OVERVIEW else parentPage(page)
        }

        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
            topBar = {
                TintedBar(barBackdrop) {
                    val navigationIcon: @Composable () -> Unit = {
                        if (page == ABOUT || page == DOWNLOAD || page == DOWNLOADED || page == DISPLAY || page == CONVERSION || (page == ONBOARDING && onboardingDone)) IconButton(onClick = ::navigateBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                    SmallTopAppBar(
                        title = title,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon
                    )
                }
            },
            bottomBar = {
                if (page == OVERVIEW || page == SETTINGS || page == LOGS) {
                    if (navigationStyle == "floating") {
                        SukiFloatingBottomBar(
                            selectedIndex = pagerState.currentPage,
                            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backdrop = barBackdrop
                        )
                    } else {
                        TintedBar(barBackdrop) {
                            NavigationBar(
                            color = Color.Transparent
                        ) {
                                NavigationBarItem(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } }, icon = MiuixIcons.Home, label = "概觀")
                                NavigationBarItem(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } }, icon = Icons.Default.List, label = "日誌")
                                NavigationBarItem(selected = pagerState.currentPage == 2, onClick = { scope.launch { pagerState.animateScrollToPage(2) } }, icon = MiuixIcons.Settings, label = "設定")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier)) {
                val renderPage: @Composable (Int) -> Unit = { visiblePage ->
                    val visibleListState = when (visiblePage) {
                        OVERVIEW -> overviewListState; SETTINGS -> settingsListState; LOGS -> logsListState
                        ABOUT -> aboutListState; DOWNLOAD -> downloadListState; ONBOARDING -> onboardingListState
                        DOWNLOADED -> downloadedListState; DISPLAY -> displayListState; else -> conversionListState
                    }
                    val visibleScrollBehavior = when (visiblePage) {
                        OVERVIEW -> overviewScrollBehavior; SETTINGS -> settingsScrollBehavior; LOGS -> logsScrollBehavior
                        ABOUT -> aboutScrollBehavior; DOWNLOAD -> downloadScrollBehavior; ONBOARDING -> onboardingScrollBehavior
                        DOWNLOADED -> downloadedScrollBehavior; DISPLAY -> displayScrollBehavior; else -> conversionScrollBehavior
                    }
                    Box(Modifier.fillMaxSize()) {
                        // 關於頁不再疊加動畫遮罩，避免內容被異常著色
                        key(visiblePage) {
                            LazyColumn(
                state = visibleListState,
                modifier = Modifier.fillMaxSize().nestedScroll(visibleScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (visiblePage) {
                    OVERVIEW -> overview(
                        game, events, activeName, activeScript, scopeStatus, rootStatus, translationEnabled,
                        onTranslationEnabled = { enabled ->
                            scope.launch {
                                runCatching { translations.setTranslationEnabled(enabled) }
                                    .onSuccess { translationEnabled = enabled; message = "漢化已${if (enabled) "啟用" else "停用"}，請重新啟動遊戲" }
                                    .onFailure { message = "切換失敗：${it.message}" }
                            }
                        },
                        targetLanguage = targetLanguage,
                        runtimeInspection = runtimeInspection,
                        onDownload = { page = DOWNLOAD },
                        onDownloaded = { page = DOWNLOADED },
                        onFixEnvironment = { page = ONBOARDING }
                    )
                    SETTINGS -> settings(
                        applyProgress = applyProgress,
                        applying = applying,
                        onFolder = { folderPicker.launch(logs.selectedFolder()) },
                        onAbout = { page = ABOUT },
                        onUpdate = { page = ABOUT },
                        onDisplay = { page = DISPLAY },
                        onConversion = { page = CONVERSION },
                        onPermissions = { page = ONBOARDING },
                        targetLanguage = targetLanguage,
                        fontName = fontName,
                        onLanguage = ::changeTargetLanguage,
                        onFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
                        onClearFont = { translations.clearCustomFont(); fontName = translations.fontName(); message = "已改回漢化包內字體" },
                        onImport = { customPackPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        onReset = { logs.resetSelectedFolder(); revision++; message = "已改回預設暫存位置" }
                    )
                    LOGS -> logPage(
                        events,
                        save = {
                            try { message = "診斷文件已儲存：${logs.saveDiagnostic(events).lastPathSegment}" }
                            catch (error: Throwable) { message = "儲存失敗：${error.message}" }
                        },
                        share = {
                            try { startActivity(logs.shareIntent(events)) }
                            catch (error: Throwable) { message = "分享失敗：${error.message}" }
                        },
                        clear = { logs.clear(); revision++; message = "日誌已清除" }
                    )
                    ABOUT -> about(
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
                    DISPLAY -> displaySettings(
                        themeMode = themeMode,
                        navigationStyle = navigationStyle,
                        onNavigationStyle = { navigationStyle = it; appPrefs.edit().putString("navigation_style", it).apply() },
                        onThemeMode = onThemeMode
                    )
                    ONBOARDING -> onboardingPage(
                        scopeStatus = scopeStatus,
                        rootStatus = rootStatus,
                        onCheckScope = { ModernApp.refreshScope(); message = "正在重新檢查模組狀態" },
                        onRequestRoot = {
                            if (rootStatus != "正在請求") {
                                rootStatus = "正在請求"
                                scope.launch {
                                    val granted = withContext(Dispatchers.IO) { requestRoot() }
                                    rootStatus = if (granted) "已授權" else "未取得授權"
                                    message = if (granted) "Root 權限已授予" else "未取得 Root 權限，套用漢化時將無法寫入遊戲資料"
                                }
                            }
                        },
                        onDone = {
                            appPrefs.edit().putBoolean("onboarding_done", true).apply()
                            page = OVERVIEW
                        }
                    )
                    DOWNLOAD -> downloadPage(
                        catalog,
                        catalogLoading,
                        catalogError,
                        transfer,
                        applyProgress,
                        applying,
                        refresh = ::refreshCatalog,
                        install = installTranslation
                    )
                    DOWNLOADED -> downloadedPage(downloadedPacks, activeName, applyProgress, applying, processingPackPath, applyingPackPath,
                        convert = { pack, conversion ->
                            if (!applying) {
                                applying = true
                                processingPackPath = pack.path
                                taskState.launchTask {
                                    runCatching { translations.convertDownloaded(pack, conversion) { value -> withContext(Dispatchers.Main.immediate) { applyProgress = value } } }
                                        .onSuccess { result ->
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
                                            message = "${pack.name} ${conversion.label}完成：已掃描 ${result.scannedFiles} 個，內容有變更 ${result.changedFiles} 個${if (pack.name == activeName) "，已重新套用；請重新啟動遊戲" else ""}"
                                        }
                                        .onFailure {
                                            android.util.Log.e("LCPatch", "Text conversion failed", it)
                                            message = "轉換失敗：${it.message}"
                                        }
                                    applying = false; applyProgress = null; processingPackPath = null
                                }
                            } else message = "請等待目前的漢化處理完成"
                        }
                    ) { pack ->
                        if (!applying) {
                            applying = true
                            applyingPackPath = pack.path
                            taskState.launchTask {
                                runCatching { translations.apply(pack) { value -> withContext(Dispatchers.Main.immediate) { applyProgress = value } } }
                                    .onSuccess { activeName = it; activeScript = translations.activeScript(); message = "已套用 $it，請重新啟動遊戲" }
                                    .onFailure { message = "套用失敗：${it.message}" }
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
                                    .onSuccess { result ->
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
                                        message = "${pack.name} ${conversion.label}完成：已掃描 ${result.scannedFiles} 個，內容有變更 ${result.changedFiles} 個${if (pack.name == activeName) "，已重新套用；請重新啟動遊戲" else ""}"
                                    }
                                    .onFailure {
                                        android.util.Log.e("LCPatch", "Text conversion failed", it)
                                        message = "轉換失敗：${it.message}"
                                    }
                                applying = false
                                applyProgress = null
                                processingPackPath = null
                            }
                        } else message = "請等待目前的漢化處理完成"
                    }
                }
                            }
                        }
                    }
                                }
                if (page in topPages) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { topPages[it] }
                    ) { index ->
                        renderPage(topPages[index])
                    }
                } else {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        fun navigationOrder(value: Int) = when (value) {
                            OVERVIEW -> 0
                            LOGS -> 1
                            SETTINGS -> 2
                            else -> value + 3
                        }
                        val direction = if (navigationOrder(targetState) > navigationOrder(initialState)) 1 else -1
                        (slideInHorizontally(tween(360, easing = PageTransitionEasing)) { direction * it / 10 } +
                            fadeIn(tween(300, easing = PageTransitionEasing))) togetherWith
                            (slideOutHorizontally(tween(300, easing = PageTransitionEasing)) { -direction * it / 12 } +
                                fadeOut(tween(220, easing = PageTransitionEasing)))
                    },
                    label = "page-transition"
                ){ animatedPage ->
                        renderPage(animatedPage)
                    }
                }
                message?.let { notice ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 18.dp, end = 18.dp, bottom = padding.calculateBottomPadding() + 14.dp),
                        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        colors = CardDefaults.defaultColors(
                            color = if (messageIsError) MiuixTheme.colorScheme.error.copy(alpha = 0.14f)
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text(notice, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (messageIsError) {
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
                                    onClick = { page = LOGS; taskState.dismissNotice() }
                                )
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = "關閉",
                                    onClick = taskState::dismissNotice
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun LazyListScope.overview(
        game: GameInfo, events: List<LogEvent>, activeName: String, activeScript: String,
        scopeStatus: String, rootStatus: String,
        translationEnabled: Boolean, onTranslationEnabled: (Boolean) -> Unit,
        targetLanguage: OverrideLanguage, runtimeInspection: RuntimeInspection,
        onDownload: () -> Unit, onDownloaded: () -> Unit, onFixEnvironment: () -> Unit
    ) {
        item {
            val healthy = scopeStatus == "已啟用"
            val hasError = scopeStatus == "尚未授權遊戲"
            val dark = isSystemInDarkTheme()
            val color = when {
                healthy -> if (dark) Color(0xFF173D27) else Color(0xFFDFFAE4)
                hasError -> if (dark) Color(0xFF472224) else Color(0xFFFFDAD9)
                else -> MiuixTheme.colorScheme.secondaryContainer
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = color)) {
                Box(modifier = Modifier.fillMaxWidth().height(142.dp)) {
                    Icon(
                        painter = painterResource(if (healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(18.dp, 18.dp).size(112.dp),
                        tint = if (healthy) Color(0xFF43D477) else if (hasError) Color(0xFFFF6B70) else MiuixTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                    Column(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        Text(if (healthy) "已啟用" else if (hasError) "尚未設定作用域" else "尚未連接", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(if (healthy) "LCPatch ${BuildConfig.VERSION_NAME}" else if (hasError) "請授予 Limbus Company 作用域" else "請確認模組與作用域狀態", fontSize = 15.sp)
                    }
                    Text(if (healthy) "Limbus Company · ${game.version}" else "模組狀態 · $scopeStatus", modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        item {
            val gameReady = game.installed
            val scopeReady = scopeStatus == "已啟用"
            val rootReady = rootStatus == "已授權"
            val runtimeReady = runtimeInspection.ready && runtimeInspection.fontReady
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("環境健檢", style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(10.dp))
                HealthCheckRow("Limbus Company", gameReady, if (gameReady) game.version else "尚未安裝")
                HealthCheckRow("LSPosed 作用域", scopeReady, scopeStatus)
                HealthCheckRow("Root 權限", rootReady, rootStatus)
                HealthCheckRow("漢化快取", runtimeReady, if (runtimeReady) "已就緒" else "尚未就緒")
                if (!gameReady || !scopeReady || !rootReady) {
                    Spacer(Modifier.height(10.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onFixEnvironment) {
                        Text("檢查並修正")
                    }
                }
            }
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
        onDisplay: () -> Unit, onConversion: () -> Unit, onPermissions: () -> Unit, targetLanguage: OverrideLanguage,
        applyProgress: ApplyProgress?, applying: Boolean,
        fontName: String, onLanguage: (OverrideLanguage) -> Unit,
        onFont: () -> Unit, onClearFont: () -> Unit, onImport: () -> Unit, onReset: () -> Unit
    ) {
        applyProgress?.let { value ->
            item {
                Card(insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                    Text(value.stage, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (value.total > 0) "${value.current} / ${value.total} 個文件" else "正在準備…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
        item { SectionLabel("漢化") }
        item {
            Card {
                OverlayDropdownPreference(
                    title = "覆蓋語言文件",
                    modifier = PreferenceItemModifier,
                    enabled = !applying,
                    items = TranslationRepository.LANGUAGES.map { it.label },
                    selectedIndex = TranslationRepository.LANGUAGES.indexOf(targetLanguage).coerceAtLeast(0),
                    onSelectedIndexChange = { index -> TranslationRepository.LANGUAGES.getOrNull(index)?.let(onLanguage) }
                )
                ArrowPreference(modifier = PreferenceItemModifier, title = "繁簡轉換", summary = "轉換已下載漢化目錄內的 JSON 文件", onClick = onConversion)
                ArrowPreference(modifier = PreferenceItemModifier, title = "匯入自訂漢化文件", summary = "從本機加入 ZIP 漢化包", onClick = onImport)
                ArrowPreference(modifier = PreferenceItemModifier, title = "更換字體", summary = fontName, onClick = onFont)
            }
        }
        if (fontName != "使用漢化包內字體") item { TextButton(modifier = Modifier.fillMaxWidth(), text = "移除自訂字體", onClick = onClearFont) }
        item { SectionLabel("應用程式") }
        item {
            Card {
                ArrowPreference(modifier = PreferenceItemModifier, title = "權限與初始設定", summary = "模組作用域與 Root 權限", onClick = onPermissions)
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
            }
        }
        item { InfoCard("顯示效果", "頂欄與底欄使用較強的背景模糊與較高不透明度，保留少量內容色彩。") }
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

    private fun LazyListScope.about(
        game: GameInfo,
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
        item { InfoCard("關於 LCPatch", "LCPatch 用於管理社群與自訂漢化、字體以及語言覆蓋設定。遊戲更新後會先驗證目標結構，配置不相符時停止載入，以降低閃退風險。", translucent = true) }
        item { InfoCard("開放原始碼與致謝", "介面採用 compose-miuix-ui，LSPosed 整合採用 libxposed API 102，繁簡轉換採用 opencc4j。漢化內容與授權條款歸各翻譯組及原作者所有。", translucent = true) }
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
    private fun HealthCheckRow(label: String, ready: Boolean, detail: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (ready) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline
                ),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (ready) Color(0xFF43A861) else MiuixTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(detail, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
            }
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

    private fun requestRoot(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        finished && process.exitValue() == 0
    } catch (_: Throwable) { false }

    private fun LazyListScope.downloadPage(
        entries: List<TranslationEntry>, loading: Boolean, error: String?, transfer: TransferProgress?,
        applyProgress: ApplyProgress?, applying: Boolean, refresh: () -> Unit, install: (TranslationEntry) -> Unit
    ) {
        item {
            Card(insideMargin = PaddingValues(18.dp)) {
                Text("漢化資源", style = MiuixTheme.textStyles.title1)
                Spacer(Modifier.height(6.dp))
                Text(when { loading -> "正在取得可用漢化…"; error != null -> error; entries.isEmpty() -> "目前沒有可用項目"; else -> "已找到 ${entries.size} 個漢化版本" }, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (!loading) { Spacer(Modifier.height(8.dp)); TextButton(modifier = Modifier.fillMaxWidth(), text = "重新整理", onClick = refresh) }
            }
        }
        transfer?.let { value ->
            item {
                Card(insideMargin = PaddingValues(18.dp)) {
                    val fraction = if (value.total > 0) (value.bytes.toFloat() / value.total).coerceIn(0f, 1f) else null
                    Text(if (applying) "正在套用 ${value.name}" else if (value.finished) "下載完成，準備套用" else "正在下載 ${value.name}", style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = fraction)
                    Spacer(Modifier.height(8.dp))
                    Text(if (value.finished) formatBytes(value.bytes) else "${if (fraction != null) "${(fraction * 100).toInt()}% · " else ""}${formatBytes(value.bytes)}${if (value.total > 0) " / ${formatBytes(value.total)}" else ""} · ${formatBytes(value.bytesPerSecond)}/s", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        applyProgress?.let { value ->
            item {
                Card(insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                    Text(value.stage, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                    Spacer(Modifier.height(8.dp))
                    Text(if (value.total > 0) "${value.current} / ${value.total} 個文件" else "正在準備…", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        items(entries) { entry ->
            Card(insideMargin = PaddingValues(18.dp)) {
                Text(entry.name, style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(4.dp)); Text("${entry.author} · ${entry.section}", color = MiuixTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp)); Text(entry.description, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp)); Button(modifier = Modifier.fillMaxWidth(), enabled = transfer?.finished != false && !applying, onClick = { install(entry) }) { Text("下載並套用") }
            }
        }
    }

    private fun LazyListScope.downloadedPage(
        entries: List<DownloadedTranslation>, activeName: String, applyProgress: ApplyProgress?, applying: Boolean,
        processingPackPath: String?, applyingPackPath: String?,
        convert: (DownloadedTranslation, TextConversion) -> Unit,
        apply: (DownloadedTranslation) -> Unit
    ) {
        applyProgress?.takeIf { processingPackPath == null }?.let { value ->
            item {
                Card(insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                    Text(value.stage, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(10.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                    Spacer(Modifier.height(8.dp)); Text(if (value.total > 0) "${value.current} / ${value.total} 個文件" else "正在準備…", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
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
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !active && !applying, onClick = { apply(entry) }) { Text(buttonText) }
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
        progress?.takeIf { processingPackPath == null }?.let { value ->
            item {
                Card(insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                    Text(value.stage, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = value.fraction)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (value.total > 0) "${value.current} / ${value.total} 個文件" else "正在掃描漢化目錄…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
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
internal fun parentPage(page: Int): Int = when (page) {
    LOGS, ABOUT, DISPLAY, ONBOARDING, CONVERSION -> SETTINGS
    DOWNLOAD, DOWNLOADED, SETTINGS -> OVERVIEW
    else -> OVERVIEW
}
private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    else -> "%.1f MB".format(value / 1024.0 / 1024.0)
}
