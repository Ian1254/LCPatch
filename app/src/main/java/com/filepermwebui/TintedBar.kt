package com.lcpatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
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
 * Fixed black frosted-glass bar for the three top-level pages.
 * The blur strength and tint stay uniform across the bar; there is no progressive fade.
 */
@Composable
internal fun TopLevelGlassBar(backdrop: LayerBackdrop?, title: String) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusInset + 64.dp)
    ) {
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(24f, 24f) },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.58f))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.90f))
            )
        }

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            color = MiuixTheme.colorScheme.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
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
