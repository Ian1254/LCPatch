package com.lcpatch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Returns the actual theme brightness currently rendered by MiuixTheme. */
@Composable
internal fun isMiuixDarkTheme(): Boolean = MiuixTheme.colorScheme.surface.luminance() < 0.5f
