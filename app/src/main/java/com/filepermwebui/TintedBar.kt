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
import androidx.compose.ui.graphics.Color
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
internal fun TopLevelCollapsingBar(
    backdrop: LayerBackdrop?,
    title: String,
    collapseProgress: Float,
    contentUnderTopBar: Boolean,
    actions: @Composable () -> Unit = {}
) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val progress = collapseProgress.coerceIn(0f, 1f)
    val materialAlpha = if (contentUnderTopBar) (0.42f + progress * 0.16f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusInset + 116.dp)
    ) {
        if (backdrop != null && materialAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusInset + 64.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(24f, 24f) },
                        onDrawSurface = {
                            drawRect(MiuixTheme.colorScheme.surface.copy(alpha = materialAlpha))
                        }
                    )
            )
        } else if (contentUnderTopBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusInset + 64.dp)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.94f))
            )
        }

        // Expanded and collapsed titles are separate presentations on purpose.
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp)
                .graphicsLayer {
                    alpha = 1f - progress
                    translationY = -progress * 18.dp.toPx()
                },
            color = MiuixTheme.colorScheme.primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusInset + 20.dp)
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 8.dp.toPx()
                },
            color = MiuixTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = statusInset + 10.dp, end = 12.dp)
                .graphicsLayer { alpha = 0.72f + progress * 0.28f }
        ) { actions() }
    }
}

/** Inner-page / standard navigation bar backdrop. */
@Composable
internal fun TintedBar(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    Box {
        if (backdrop != null) {
            Box(
                Modifier.matchParentSize().drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(22f, 22f) },
                    onDrawSurface = { drawRect(surface.copy(alpha = 0.48f)) }
                )
            )
        } else {
            Box(Modifier.matchParentSize().background(surface.copy(alpha = 0.94f)))
        }
        content()
    }
}
