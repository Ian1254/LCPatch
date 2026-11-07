package com.lcpatch

import androidx.compose.foundation.lazy.LazyListScope

/**
 * The lazy-list owner and scroll position stay in MainActivity. No nested
 * LazyColumn, duplicate downloader or duplicate navigation state.
 */
internal fun LazyListScope.overviewRoute(
    style: UiStyle,
    state: OverviewUiState,
    actions: OverviewActions,
    overlayState: EnvironmentStatusOverlayState,
) {
    when (style) {
        UiStyle.HYPER_OS_4 -> hyperOverview(state, actions, overlayState)
        UiStyle.COLOR_OS_17 -> colorOverview(state, actions, overlayState)
    }
}
