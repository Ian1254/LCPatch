package com.lcpatch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragAccepted by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    val updatedSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)
    val updatedCanDrag by rememberUpdatedState(pendingTarget == null)

    LaunchedEffect(selectedIndex, pendingTarget) {
        if (!dragging && pendingTarget == selectedIndex) pendingTarget = null
    }

    val visualTarget = if (dragging) {
        dragPosition
    } else {
        (pendingTarget ?: selectedIndex).toFloat()
    }
    val visualPosition by animateFloatAsState(
        targetValue = visualTarget.coerceIn(0f, (NavigationItemCount - 1).toFloat()),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.82f, stiffness = 460f),
        label = "floating-navigation-position"
    )

    val indicatorTranslationDp = NavigationHorizontalPaddingDp + visualPosition * NavigationItemWidthDp
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
        .pointerInput(density) {
            val itemWidthPx = NavigationItemWidthDp * density.density
            val horizontalPaddingPx = NavigationHorizontalPaddingDp * density.density
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    val selected = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
                    val selectedStart = horizontalPaddingPx + selected * itemWidthPx
                    val selectedEnd = selectedStart + itemWidthPx
                    dragAccepted = updatedCanDrag && start.x in selectedStart..selectedEnd
                    if (dragAccepted) {
                        dragging = true
                        dragPosition = selected.toFloat()
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (dragAccepted) {
                        change.consume()
                        dragPosition = (dragPosition + amount / itemWidthPx)
                            .coerceIn(0f, (NavigationItemCount - 1).toFloat())
                    }
                },
                onDragEnd = {
                    if (dragAccepted) {
                        val target = dragPosition.roundToInt().coerceIn(0, NavigationItemCount - 1)
                        pendingTarget = target
                        dragAccepted = false
                        dragging = false
                        updatedOnSelected(target)
                    }
                },
                onDragCancel = {
                    if (dragAccepted) {
                        dragAccepted = false
                        dragging = false
                        pendingTarget = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
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
                    .graphicsLayer {
                        translationX = with(density) { indicatorTranslationDp.dp.toPx() }
                    }
                    .width(NavigationItemWidthDp.dp)
                    .height(56.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, visualPosition, 0) {
                    pendingTarget = 0
                    updatedOnSelected(0)
                }
                SukiNavigationItem("日誌", Icons.Default.List, visualPosition, 1) {
                    pendingTarget = 1
                    updatedOnSelected(1)
                }
                SukiNavigationItem("設定", MiuixIcons.Settings, visualPosition, 2) {
                    pendingTarget = 2
                    updatedOnSelected(2)
                }
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
