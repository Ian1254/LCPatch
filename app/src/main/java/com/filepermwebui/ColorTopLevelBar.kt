package com.lcpatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import io.github.suqi8.coui.kmp.basic.Text as COUIText
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Separate COUI title layout: leading-aligned typography instead of the
 * center-aligned MIUIX bar. The existing live blur source stays intact.
 */
@Composable
internal fun ColorTopLevelBar(
    backdrop: Backdrop?,
    pageTitles: List<String>,
    pagePosition: Float,
) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    COUITheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        val surface = COUITheme.colorScheme.surface
        val ink = COUITheme.colorScheme.onSurface
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val height = statusInset + 64.dp
        val density = LocalDensity.current
        Box(Modifier.fillMaxWidth().height(height)) {
            if (backdrop != null) {
                ProgressiveBlurTopBar(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    height = height,
                    tintColor = surface,
                    tintIntensity = 0.22f,
                    edgeFadeStart = 0.80f
                ) {}
            } else {
                Box(
                    Modifier.fillMaxWidth().height(height).background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.96f),
                            0.75f to surface.copy(alpha = 0.76f),
                            1f to Color.Transparent
                        )
                    )
                )
            }
            Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
                pageTitles.forEachIndexed { index, title ->
                    val delta = index - pagePosition
                    if (abs(delta) < 1.15f) {
                        COUIText(
                            title,
                            modifier = Modifier
                                .padding(start = 24.dp, top = statusInset + 17.dp)
                                .offset {
                                    IntOffset(
                                        (delta * with(density) { 96.dp.toPx() }).roundToInt(),
                                        0
                                    )
                                }
                                .graphicsLayer { alpha = (1f - abs(delta)).coerceIn(0f, 1f) },
                            color = ink,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
