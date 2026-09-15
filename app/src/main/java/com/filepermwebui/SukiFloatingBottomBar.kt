package com.lcpatch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
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

private const val NavigationItemCount = 3
private const val NavigationItemWidthDp = 80f
private const val NavigationHorizontalPaddingDp = 4f

@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDragProgress: (Float) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragAccepted by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    val animatedOffset by animateDpAsState(
        targetValue = (NavigationHorizontalPaddingDp + selectedIndex * NavigationItemWidthDp).dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f),
        label = "floating-navigation-indicator"
    )
    val indicatorOffset = if (dragging) {
        (NavigationHorizontalPaddingDp + dragPosition * NavigationItemWidthDp).dp
    } else animatedOffset
    val visualPosition = if (dragging) {
        dragPosition
    } else {
        (animatedOffset.value - NavigationHorizontalPaddingDp) / NavigationItemWidthDp
    }
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
        // Listen on the bar container rather than the indicator itself. The navigation
        // items are drawn above the indicator, so putting pointerInput on the indicator
        // made their clickable modifiers win hit testing and prevented the drag from
        // starting. We still accept a drag only when it began inside the selected capsule.
        .pointerInput(selectedIndex, density) {
            val itemWidthPx = NavigationItemWidthDp * density.density
            val horizontalPaddingPx = NavigationHorizontalPaddingDp * density.density
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    val selectedStart = horizontalPaddingPx + selectedIndex * itemWidthPx
                    val selectedEnd = selectedStart + itemWidthPx
                    dragAccepted = start.x in selectedStart..selectedEnd
                    if (dragAccepted) {
                        dragging = true
                        dragPosition = selectedIndex.toFloat()
                        onDragProgress(dragPosition)
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (dragAccepted) {
                        change.consume()
                        dragPosition = (dragPosition + amount / itemWidthPx)
                            .coerceIn(0f, (NavigationItemCount - 1).toFloat())
                        onDragProgress(dragPosition)
                    }
                },
                onDragEnd = {
                    if (dragAccepted) {
                        val target = dragPosition.roundToInt().coerceIn(0, NavigationItemCount - 1)
                        dragging = false
                        dragAccepted = false
                        onSelected(target)
                    }
                },
                onDragCancel = {
                    if (dragAccepted) {
                        dragging = false
                        dragAccepted = false
                        onSelected(selectedIndex)
                    }
                }
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
                    .graphicsLayer { translationX = with(density) { indicatorOffset.toPx() } }
                    .width(80.dp)
                    .height(56.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, visualPosition, 0) { onSelected(0) }
                SukiNavigationItem("日誌", Icons.Default.List, visualPosition, 1) { onSelected(1) }
                SukiNavigationItem("設定", MiuixIcons.Settings, visualPosition, 2) { onSelected(2) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SukiNavigationItem(
    label: String,
    icon: ImageVector,
    selectionPosition: Float,
    index: Int,
    onClick: () -> Unit
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val selectedColor = MiuixTheme.colorScheme.primary
    val unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val color: Color = lerp(unselectedColor, selectedColor, selectedAmount)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                val scale = 1f + selectedAmount * 0.06f
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp), tint = color)
        Text(label, color = color, fontSize = 11.sp)
    }
}
