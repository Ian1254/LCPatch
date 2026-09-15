package com.lcpatch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(4f + selectedIndex * 80f) }
    val animatedOffset by animateDpAsState(
        targetValue = (4 + selectedIndex * 80).dp,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 250f),
        label = "floating-navigation-indicator"
    )
    val selectedOffset = if (dragging) dragOffset.dp else animatedOffset
    val containerColor = MiuixTheme.colorScheme.surfaceContainer
    val barModifier = Modifier
        .width(248.dp)
        .height(64.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(36f, 36f) },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.76f)) }
                )
            } else {
                Modifier.background(containerColor.copy(alpha = 0.88f), CircleShape)
            }
        )
        .clip(CircleShape)
        .pointerInput(selectedIndex, density) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragging = true
                    dragOffset = 4f + selectedIndex * 80f
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragOffset = (dragOffset + amount / density.density).coerceIn(4f, 164f)
                },
                onDragEnd = {
                    val target = ((dragOffset - 4f) / 80f).roundToInt().coerceIn(0, 2)
                    dragging = false
                    onSelected(target)
                },
                onDragCancel = { dragging = false }
            )
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = barModifier) {
            Box(
                modifier = Modifier
                    .offset(y = 4.dp)
                    .graphicsLayer { translationX = with(density) { selectedOffset.toPx() } }
                    .width(80.dp)
                    .height(56.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, selectedIndex == 0) { onSelected(0) }
                SukiNavigationItem("日誌", Icons.Default.List, selectedIndex == 1) { onSelected(1) }
                SukiNavigationItem("設定", MiuixIcons.Settings, selectedIndex == 2) { onSelected(2) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SukiNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "navigation-item-color"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp), tint = color)
        Text(label, color = color, fontSize = 11.sp)
    }
}
