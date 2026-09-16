package com.lcpatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Draws the bar content normally, but masks the blurred backdrop itself toward the lower edge.
 * This avoids the old "blurred rectangle/card" look where only the tint faded while the blur
 * strength stayed visually constant across the whole top bar.
 */
@Composable
internal fun TintedBar(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    val fallback = Brush.verticalGradient(
        0f to surface.copy(alpha = 0.34f),
        0.46f to surface.copy(alpha = 0.16f),
        0.76f to surface.copy(alpha = 0.05f),
        1f to Color.Transparent
    )
    val blurMask = Brush.verticalGradient(
        0f to Color.Black,
        0.32f to Color.Black.copy(alpha = 0.92f),
        0.62f to Color.Black.copy(alpha = 0.58f),
        0.82f to Color.Black.copy(alpha = 0.24f),
        1f to Color.Transparent
    )

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = blurMask, blendMode = BlendMode.DstIn)
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = { blur(26f, 26f) },
                                onDrawSurface = {
                                    drawRect(surface.copy(alpha = 0.10f))
                                }
                            )
                        } else {
                            Modifier.background(fallback)
                        }
                    )
            )
        }
        content()
    }
}
