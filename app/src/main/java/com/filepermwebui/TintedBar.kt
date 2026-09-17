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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberBarBackdrop(): LayerBackdrop? {
    if (!isRenderEffectSupported()) return null
    return rememberLayerBackdrop {
        drawContent()
    }
}

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
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(radius, radius) },
                    onDrawSurface = {
                        if (surfaceTint.alpha > 0f) drawRect(surfaceTint)
                    }
                )
        )
    }
}

/**
 * Blur-only top edge for top-level pages. Several blur bands are blended together so the
 * blur strength itself falls off toward the content instead of drawing one fixed blurred card
 * and merely fading its alpha.
 */
@Composable
internal fun TopLevelBlurBar(backdrop: LayerBackdrop?) {
    val surface = MiuixTheme.colorScheme.surface
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.28f),
        0.48f to surface.copy(alpha = 0.10f),
        1f to Color.Transparent
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusInset + 64.dp)
    ) {
        if (backdrop != null) {
            val strongMask = Brush.verticalGradient(
                0f to Color.Black,
                0.30f to Color.Black.copy(alpha = 0.92f),
                0.58f to Color.Black.copy(alpha = 0.46f),
                0.76f to Color.Transparent
            )
            val mediumMask = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.44f),
                0.52f to Color.Black.copy(alpha = 0.34f),
                0.82f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Transparent
            )
            val lightMask = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.18f),
                0.70f to Color.Black.copy(alpha = 0.13f),
                1f to Color.Transparent
            )

            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 26f,
                mask = strongMask
            )
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 15f,
                mask = mediumMask
            )
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 7f,
                mask = lightMask
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.055f),
                            0.62f to surface.copy(alpha = 0.018f),
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
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.26f),
        0.55f to surface.copy(alpha = 0.10f),
        1f to Color.Transparent
    )
    val blurMask = Brush.verticalGradient(
        0f to Color.Black,
        0.42f to Color.Black.copy(alpha = 0.90f),
        0.76f to Color.Black.copy(alpha = 0.30f),
        1f to Color.Transparent
    )

    Box {
        if (backdrop != null) {
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 22f,
                mask = blurMask,
                surfaceTint = surface.copy(alpha = 0.055f)
            )
        } else {
            Box(Modifier.matchParentSize().background(fallback))
        }
        content()
    }
}
