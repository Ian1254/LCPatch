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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared, live backdrop source for page content and all glass chrome. */
@Composable
internal fun rememberBarBackdrop(): LayerBackdrop? {
    if (!isRenderEffectSupported()) return null
    return rememberLayerBackdrop { drawContent() }
}

/**
 * Fixed small-title top bar. Blur and foreground are deliberately separate layers.
 * [pagePosition] comes from PagerState, so title motion starts and ends with the page.
 */
@Composable
internal fun TopLevelProgressiveBar(
    backdrop: Backdrop?,
    pageTitles: List<String>,
    pagePosition: Float,
    actions: @Composable () -> Unit = {}
) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusInset + 64.dp
    val surface = MiuixTheme.colorScheme.surface

    Box(Modifier.fillMaxWidth().height(barHeight)) {
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                height = barHeight,
                tintIntensity = 0.16f,
                tintColor = surface,
                edgeFadeStart = 0.76f
            ) {}
        } else {
            Box(
                Modifier.fillMaxWidth().height(barHeight).background(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.94f),
                        0.78f to surface.copy(alpha = 0.70f),
                        1f to Color.Transparent
                    )
                )
            )
        }

        Box(
            Modifier.fillMaxWidth().height(barHeight).clipToBounds(),
            contentAlignment = Alignment.TopCenter
        ) {
            val density = LocalDensity.current
            pageTitles.forEachIndexed { index, title ->
                val delta = index - pagePosition
                if (abs(delta) < 1.15f) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .padding(top = statusInset + 20.dp)
                            .offset {
                                IntOffset(
                                    (delta * with(density) { 120.dp.toPx() }).roundToInt(),
                                    0
                                )
                            }
                            .graphicsLayer { alpha = (1f - abs(delta)).coerceIn(0f, 1f) },
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = statusInset + 10.dp, end = 12.dp)) {
            actions()
        }
    }
}

/** Inner-page top bar backdrop; the supplied UI is always above the blur layer. */
@Composable
internal fun TintedBar(backdrop: Backdrop?, content: @Composable () -> Unit) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusInset + 64.dp
    val surface = MiuixTheme.colorScheme.surface
    Box {
        if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.matchParentSize(),
                height = barHeight,
                tintIntensity = 0.18f,
                tintColor = surface,
                edgeFadeStart = 0.80f
            ) {}
        } else {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.94f),
                        1f to Color.Transparent
                    )
                )
            )
        }
        content()
    }
}
