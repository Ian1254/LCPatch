package com.lcpatch

internal const val OVERVIEW = 0
internal const val SETTINGS = 1
internal const val LOGS = 2
internal const val ABOUT = 3
internal const val DOWNLOAD = 4
internal const val ONBOARDING = 5
internal const val DOWNLOADED = 6
internal const val DISPLAY = 7
internal const val CONVERSION = 8
internal const val UPDATE = 9
internal const val TOP_LEVEL_CONTAINER = -1

internal val TOP_LEVEL_PAGES = listOf(OVERVIEW, LOGS, SETTINGS)

internal data class NavigationState(
    val page: Int,
    val stack: IntArray = intArrayOf(),
    val direction: Int = 1
) {
    fun navigateTo(target: Int): NavigationState {
        if (target == page) return this
        return if (target in TOP_LEVEL_PAGES) {
            NavigationState(page = target, stack = intArrayOf(), direction = -1)
        } else {
            NavigationState(page = target, stack = stack + page, direction = 1)
        }
    }

    fun navigateBack(): NavigationState {
        if (stack.isNotEmpty()) {
            return NavigationState(
                page = stack.last(),
                stack = stack.copyOf(stack.size - 1),
                direction = -1
            )
        }
        return NavigationState(
            page = parentPage(page),
            stack = intArrayOf(),
            direction = -1
        )
    }
}

internal fun parentPage(page: Int): Int = when (page) {
    ABOUT, UPDATE, DISPLAY, ONBOARDING, CONVERSION -> SETTINGS
    DOWNLOAD, DOWNLOADED, SETTINGS, LOGS -> OVERVIEW
    else -> OVERVIEW
}

internal fun pageTitle(page: Int): String = when (page) {
    OVERVIEW -> "LCPatch"
    SETTINGS -> "設定"
    LOGS -> "日誌"
    ABOUT -> "關於"
    UPDATE -> "應用程式更新"
    DOWNLOAD -> "下載漢化"
    DOWNLOADED -> "選擇套用"
    DISPLAY -> "介面與顯示"
    CONVERSION -> "繁簡轉換"
    ONBOARDING -> "環境與權限"
    else -> "LCPatch"
}
