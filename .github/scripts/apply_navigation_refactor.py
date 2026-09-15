from pathlib import Path

path = Path("app/src/main/java/com/filepermwebui/MainActivity.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "private const val UPDATE = 9\n",
    "private const val UPDATE = 9\nprivate const val TOP_LEVEL_CONTAINER = -1\n",
    "top-level container constant",
)

page_decl = (
    '        var page by rememberSaveable { mutableIntStateOf(if '
    '(appPrefs.getBoolean("onboarding_done", false)) OVERVIEW else ONBOARDING) }\n'
)
replace_once(
    page_decl,
    page_decl
    + "        var navigationStack by rememberSaveable { mutableStateOf(intArrayOf()) }\n"
    + "        var navigationDirection by rememberSaveable { mutableIntStateOf(1) }\n",
    "navigation state",
)

replace_once(
    """        fun navigateBack() {
            page = parentPage(page)
        }""",
    """        fun navigateTo(target: Int) {
            if (target == page) return
            if (target in topPages) {
                navigationStack = intArrayOf()
                navigationDirection = -1
            } else {
                navigationStack = navigationStack + page
                navigationDirection = 1
            }
            page = target
        }
        fun navigateBack() {
            navigationDirection = -1
            if (navigationStack.isNotEmpty()) {
                page = navigationStack.last()
                navigationStack = navigationStack.copyOf(navigationStack.size - 1)
            } else {
                page = parentPage(page)
            }
        }""",
    "navigation helpers",
)

replace_once(
    """        BackHandler(enabled = page != OVERVIEW && (page != ONBOARDING || onboardingDone)) {
            page = if (page == SETTINGS || page == LOGS) OVERVIEW else parentPage(page)
        }""",
    """        BackHandler(enabled = page != OVERVIEW && (page != ONBOARDING || onboardingDone)) {
            navigateBack()
        }""",
    "back handler",
)

replace_once(
    "                            selectedIndex = pagerState.settledPage,\n",
    "                            selectedIndex = pagerState.settledPage,\n"
    "                            navigationPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)\n"
    "                                .coerceIn(0f, topPages.lastIndex.toFloat()),\n",
    "shared navigation position",
)

replace_once(
    "                    bottom = padding.calculateBottomPadding() + 16.dp\n",
    "                    bottom = padding.calculateBottomPadding() +\n"
    '                        if (visiblePage in topPages && navigationStyle == "floating") 28.dp else 16.dp\n',
    "floating bottom inset",
)

replacements = [
    ("onDownload = { page = DOWNLOAD }", "onDownload = { navigateTo(DOWNLOAD) }"),
    ("onDownloaded = { page = DOWNLOADED }", "onDownloaded = { navigateTo(DOWNLOADED) }"),
    ("onAbout = { page = ABOUT }", "onAbout = { navigateTo(ABOUT) }"),
    ("onUpdate = { page = UPDATE }", "onUpdate = { navigateTo(UPDATE) }"),
    ("onDisplay = { page = DISPLAY }", "onDisplay = { navigateTo(DISPLAY) }"),
    ("onConversion = { page = CONVERSION }", "onConversion = { navigateTo(CONVERSION) }"),
    ("onPermissions = { page = ONBOARDING }", "onPermissions = { navigateTo(ONBOARDING) }"),
    (
        'onClick = { page = LOGS; taskState.dismissNotice() }',
        'onClick = { navigateTo(LOGS); taskState.dismissNotice() }',
    ),
]
for old, new in replacements:
    replace_once(old, new, old)

# The onboarding completion assignment is unique and intentionally kept separate from
# top-level navigation state synchronization.
replace_once(
    '                            page = OVERVIEW\n                        }',
    '                            navigateTo(OVERVIEW)\n                        }',
    "onboarding completion navigation",
)

start_marker = """                if (page in topPages) {
                    HorizontalPager("""
end_marker = """                message?.let { notice ->"""
start = text.find(start_marker)
if start < 0:
    raise SystemExit("top-level pager block: start marker not found")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("top-level pager block: end marker not found")

new_host = """                AnimatedContent(
                    targetState = if (page in topPages) TOP_LEVEL_CONTAINER else page,
                    transitionSpec = {
                        val direction = navigationDirection
                        (slideInHorizontally(tween(360, easing = PageTransitionEasing)) { direction * it / 10 } +
                            fadeIn(tween(300, easing = PageTransitionEasing))) togetherWith
                            (slideOutHorizontally(tween(300, easing = PageTransitionEasing)) { -direction * it / 12 } +
                                fadeOut(tween(220, easing = PageTransitionEasing)))
                    },
                    label = "page-transition"
                ) { animatedPage ->
                    if (animatedPage == TOP_LEVEL_CONTAINER) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = navigationStyle != "floating",
                            key = { topPages[it] }
                        ) { index ->
                            renderPage(topPages[index])
                        }
                    } else {
                        renderPage(animatedPage)
                    }
                }
"""
text = text[:start] + new_host + text[end:]

checks = [
    'userScrollEnabled = navigationStyle != "floating"',
    "navigationPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)",
    "targetState = if (page in topPages) TOP_LEVEL_CONTAINER else page",
    "fun navigateTo(target: Int)",
]
for needle in checks:
    if needle not in text:
        raise SystemExit(f"sanity check missing: {needle}")
if "fun navigationOrder" in text:
    raise SystemExit("numeric navigationOrder still present")

path.write_text(text)
