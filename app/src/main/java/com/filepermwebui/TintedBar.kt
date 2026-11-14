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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Haze 2.1 proof-of-concept renderer. Deliberately opt-in: the established
 * AGSL shader stays the default while we compare visual stability on-device.
 * Capture is explicit via Modifier.hazeSource(state) on the page content.
 */
@Composable
private fun HazeExperimentalTopBlur(state: HazeState, height: Dp) {
    val surface = MiuixTheme.colorScheme.surface
    val style = remember {
        HazeBlurStyle {
            blurRadius(12.dp)
            progressive(HazeProgressive.verticalGradient(
                startIntensity = 1f,
                endIntensity = 0f
            ))
        }
    }
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier
                .matchParentSize()
                .hazeBlur(
                    input = HazeInput.Sources(state),
                    style = style
                )
        )
        // A very light surface tint keeps title contrast while allowing
        // the underlying captured page to remain visible through the blur.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.76f),
                    1f to surface.copy(alpha = 0.12f)
                )
            )
        )
    }
}

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
    hazeState: HazeState? = null,
    pageTitles: List<String>,
    pagePosition: Float,
    collapseFraction: Float = 1f,
    actions: @Composable () -> Unit = {}
) {
    val hyper = true
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleLine = if (hyper) 24.sp else (42f-10f*collapseFraction.coerceIn(0f,1f)).sp
    val barHeight = statusInset + maxOf(56.dp, with(LocalDensity.current) { titleLine.toDp() } + 16.dp)
    val surface = MiuixTheme.colorScheme.surface

    Box(Modifier.fillMaxWidth().height(barHeight)) {
        if (hazeState != null) {
            HazeExperimentalTopBlur(hazeState, barHeight)
        } else if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                height = barHeight,
                tintIntensity = 0.42f,
                tintColor = surface,
                edgeFadeStart = 0.76f
            ) {}
        } else {
            Box(Modifier.fillMaxWidth().height(barHeight).background(surface))
        }

        Box(
            Modifier.fillMaxWidth().height(barHeight),
            contentAlignment = Alignment.TopStart
        ) {
            val density = LocalDensity.current
            pageTitles.forEachIndexed { index, title ->
                val delta = index - pagePosition
                if (abs(delta) < 1.15f) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .padding(start = 24.dp, top = statusInset + 16.dp)
                            .offset {
                                IntOffset(
                                    (delta * with(density) { 120.dp.toPx() }).roundToInt(),
                                    with(density) { (if(hyper) 0.dp else (48f*(1f-collapseFraction.coerceIn(0f,1f))).dp).roundToPx() }
                                )
                            }
                            .graphicsLayer { alpha = (1f - abs(delta)).coerceIn(0f, 1f)  },
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = if (hyper) 18.sp else (32f-8f*collapseFraction.coerceIn(0f,1f)).sp,
                        lineHeight = titleLine,
                        fontWeight = if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) FontWeight.Medium else FontWeight.SemiBold
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
internal fun TintedBar(
    backdrop: Backdrop?,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusInset + 56.dp
    val surface = MiuixTheme.colorScheme.surface
    Box {
        if (hazeState != null) {
            HazeExperimentalTopBlur(hazeState, barHeight)
        } else if (backdrop != null) {
            ProgressiveBlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.matchParentSize(),
                height = barHeight,
                tintIntensity = 0.18f,
                tintColor = surface,
                edgeFadeStart = 0.80f
            ) {}
        } else {
            Box(Modifier.matchParentSize().background(surface))
        }
        content()
    }
}
