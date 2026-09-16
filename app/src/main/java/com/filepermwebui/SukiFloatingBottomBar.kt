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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
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
private const val IndicatorRestingWidthDp = 68f
private const val IndicatorRestingHeightDp = 50f

@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
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

    val selected = selectedIndex.coerceIn(0, NavigationItemCount - 1)
    val visualTarget = if (dragging) dragPosition else (pendingTarget ?: selected).toFloat()
    val visualPosition by animateFloatAsState(
        targetValue = visualTarget.coerceIn(0f, (NavigationItemCount - 1).toFloat()),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.8f, stiffness = 430f),
        label = "floating-navigation-selection"
    )

    val restingHalfWidth = IndicatorRestingWidthDp / 2f
    val selectedCenter = NavigationHorizontalPaddingDp + selected * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val dragCenter = NavigationHorizontalPaddingDp + dragPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val settledCenter = NavigationHorizontalPaddingDp + visualPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f

    val rawLeft: Float
    val rawRight: Float
    if (dragging) {
        val leadingCenter = dragCenter
        val distance = abs(leadingCenter - selectedCenter)
        val trailingLag = distance * 0.18f
        if (leadingCenter >= selectedCenter) {
            rawLeft = selectedCenter - restingHalfWidth + trailingLag
            rawRight = leadingCenter + restingHalfWidth
        } else {
            rawLeft = leadingCenter - restingHalfWidth
            rawRight = selectedCenter + restingHalfWidth - trailingLag
        }
    } else {
        rawLeft = settledCenter - restingHalfWidth
        rawRight = settledCenter + restingHalfWidth
    }

    val indicatorLeft by animateFloatAsState(
        targetValue = rawLeft,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.76f, stiffness = 520f),
        label = "floating-navigation-left-edge"
    )
    val indicatorRight by animateFloatAsState(
        targetValue = rawRight,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.72f, stiffness = 470f),
        label = "floating-navigation-right-edge"
    )
    val pressedScale by animateFloatAsState(
        targetValue = when {
            pressed && !dragging -> 0.91f
            dragging -> 0.96f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 620f),
        label = "floating-navigation-press-scale"
    )

    val indicatorWidth = max(IndicatorRestingWidthDp, indicatorRight - indicatorLeft)
    val indicatorCenter = (indicatorLeft + indicatorRight) / 2f
    val indicatorTranslationDp = indicatorCenter - indicatorWidth / 2f
    val containerColor = MiuixTheme.colorScheme.surfaceContainer

    val barModifier = Modifier
        .width(248.dp)
        .height(62.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(36f, 36f) },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.72f)) }
                )
            } else {
                Modifier.background(containerColor.copy(alpha = 0.86f), CircleShape)
            }
        )
        .clip(CircleShape)
        .pointerInput(density) {
            val itemWidthPx = NavigationItemWidthDp * density.density
            val paddingPx = NavigationHorizontalPaddingDp * density.density
            val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { change ->
                        if (!change.previousPressed && change.pressed) {
                            val current = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
                            val center = paddingPx + current * itemWidthPx + itemWidthPx / 2f
                            if (change.position.x in (center - halfIndicatorPx)..(center + halfIndicatorPx)) {
                                pressed = updatedCanDrag
                            }
                        } else if (change.previousPressed && !change.pressed) {
                            pressed = false
                        }
                    }
                }
            }
        }
        .pointerInput(density) {
            val itemWidthPx = NavigationItemWidthDp * density.density
            val paddingPx = NavigationHorizontalPaddingDp * density.density
            val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    val current = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
                    val center = paddingPx + current * itemWidthPx + itemWidthPx / 2f
                    dragAccepted = updatedCanDrag && start.x in (center - halfIndicatorPx)..(center + halfIndicatorPx)
                    if (dragAccepted) {
                        pressed = true
                        dragging = true
                        dragPosition = current.toFloat()
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
                        pressed = false
                        updatedOnSelected(target)
                    }
                },
                onDragCancel = {
                    if (dragAccepted) {
                        dragAccepted = false
                        dragging = false
                        pressed = false
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
                    .graphicsLayer {
                        translationX = with(density) { indicatorTranslationDp.dp.toPx() }
                        scaleX = pressedScale
                        scaleY = pressedScale
                    }
                    .offset(y = 6.dp)
                    .width(indicatorWidth.dp)
                    .height(IndicatorRestingHeightDp.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.11f), CircleShape)
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, visualPosition, 0) {
                    if (pendingTarget == null) {
                        pendingTarget = 0
                        updatedOnSelected(0)
                    }
                }
                SukiNavigationItem("日誌", Icons.Default.List, visualPosition, 1) {
                    if (pendingTarget == null) {
                        pendingTarget = 1
                        updatedOnSelected(1)
                    }
                }
                SukiNavigationItem("設定", MiuixIcons.Settings, visualPosition, 2) {
                    if (pendingTarget == null) {
                        pendingTarget = 2
                        updatedOnSelected(2)
                    }
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
                val scale = 1f + selectedAmount * 0.035f
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = color)
        Text(label, color = color, fontSize = 11.sp)
    }
}
