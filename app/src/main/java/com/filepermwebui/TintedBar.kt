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
    return rememberLayerBackdrop { drawContent() }
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
                    onDrawSurface = { if (surfaceTint.alpha > 0f) drawRect(surfaceTint) }
                )
        )
    }
}

/** Single-pass gradient blur: no stacked blur cards / repeated offscreen blur passes. */
@Composable
internal fun TopLevelBlurBar(backdrop: LayerBackdrop?) {
    val surface = MiuixTheme.colorScheme.surface
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val mask = Brush.verticalGradient(
        0f to Color.Black,
        0.34f to Color.Black.copy(alpha = 0.94f),
        0.66f to Color.Black.copy(alpha = 0.42f),
        1f to Color.Transparent
    )
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.25f),
        0.58f to surface.copy(alpha = 0.08f),
        1f to Color.Transparent
    )
    Box(Modifier.fillMaxWidth().height(statusInset + 64.dp)) {
        if (backdrop != null) {
            MaskedBackdrop(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                radius = 24f,
                mask = mask,
                surfaceTint = surface.copy(alpha = 0.035f)
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
        } else Box(Modifier.matchParentSize().background(fallback))
        content()
    }
}
