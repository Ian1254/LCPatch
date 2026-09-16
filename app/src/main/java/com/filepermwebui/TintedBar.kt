package com.lcpatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberBarBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop { drawContent() }
}

/**
 * Single-pass masked backdrop. The blur stays strong at the edge and fades smoothly into content,
 * avoiding the three independent off-screen blur passes used previously.
 */
@Composable
private fun MaskedBackdrop(
    modifier: Modifier,
    backdrop: LayerBackdrop,
    radius: Float,
    mask: Brush,
    surfaceTint: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radius, radius) },
                onDrawSurface = {
                    if (surfaceTint.alpha > 0f) drawRect(surfaceTint)
                },
                contentBlendMode = BlendMode.DstIn
            )
            .background(mask)
    )
}

/** Top-edge overlay for top-level pages. It is drawn over content and should not reserve space. */
@Composable
internal fun TopLevelBlurBar(backdrop: LayerBackdrop?) {
    val surface = MiuixTheme.colorScheme.surface
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val mask = Brush.verticalGradient(
        0f to Color.Black,
        0.26f to Color.Black.copy(alpha = 0.94f),
        0.58f to Color.Black.copy(alpha = 0.54f),
        0.82f to Color.Black.copy(alpha = 0.18f),
        1f to Color.Transparent
    )
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.24f),
        0.50f to surface.copy(alpha = 0.08f),
        1f to Color.Transparent
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusInset + 68.dp)
    ) {
        if (backdrop != null) {
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 24f,
                mask = mask
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.035f),
                            0.60f to surface.copy(alpha = 0.010f),
                            1f to Color.Transparent
                        )
                    )
            )
        } else {
            Box(Modifier.matchParentSize().background(fallback))
        }
    }
}

/** Inner-page / standard navigation bar backdrop. */
@Composable
internal fun TintedBar(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    val mask = Brush.verticalGradient(
        0f to Color.Black,
        0.48f to Color.Black.copy(alpha = 0.90f),
        0.78f to Color.Black.copy(alpha = 0.28f),
        1f to Color.Transparent
    )
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.24f),
        0.58f to surface.copy(alpha = 0.08f),
        1f to Color.Transparent
    )

    Box {
        if (backdrop != null) {
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 21f,
                mask = mask,
                surfaceTint = surface.copy(alpha = 0.035f)
            )
        } else {
            Box(Modifier.matchParentSize().background(fallback))
        }
        content()
    }
}
