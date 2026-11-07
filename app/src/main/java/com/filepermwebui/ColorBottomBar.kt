package com.lcpatch

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationBarItem
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * COUI's actual NavigationBar instead of recoloring the MIUIX pill.
 * Navigation remains owned by MainActivity's existing pager.
 */
@Composable
internal fun ColorBottomBar(
    selectedIndex: Int,
    onTargetSelected: (Int) -> Unit,
) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    COUITheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            defaultWindowInsetsPadding = false
        ) {
            NavigationBarItem(
                selected = selectedIndex == 0,
                onClick = { onTargetSelected(0) },
                icon = MiuixIcons.Home,
                label = "概觀"
            )
            NavigationBarItem(
                selected = selectedIndex == 1,
                onClick = { onTargetSelected(1) },
                icon = Icons.Default.List,
                label = "日誌"
            )
            NavigationBarItem(
                selected = selectedIndex == 2,
                onClick = { onTargetSelected(2) },
                icon = MiuixIcons.Settings,
                label = "設定"
            )
        }
    }
}
