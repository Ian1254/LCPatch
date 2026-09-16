package com.lcpatch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings

private const val NavigationItemCount = 3
private const val NavigationItemWidthDp = 80f
private const val NavigationHorizontalPaddingDp = 4f
private const val IndicatorRestingWidthDp = 68f
private const val IndicatorRestingHeightDp = 50f
private const val IndicatorMaxStretchDp = 30f

/**
 * HyperOS-style selector: dragging only previews the capsule. Page navigation is committed on
 * release, never while the pointer is down.
 */
@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragAccepted by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    val updatedSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)
    val interactionLocked = pendingTarget != null

    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) {
            dragPosition = selectedIndex.toFloat()
            if (pendingTarget == selectedIndex) pendingTarget = null
        }
    }

    val selected = selectedIndex.coerceIn(0, NavigationItemCount - 1)
    val visualTarget = if (dragging) dragPosition else (pendingTarget ?: selected).toFloat()
    val visualPosition by animateFloatAsState(
        targetValue = visualTarget.coerceIn(0f, (NavigationItemCount - 1).toFloat()),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.84f, stiffness = 520f),
        label = "hyperos-navigation-position"
    )

    val lower = floor(visualPosition)
    val segmentFraction = (visualPosition - lower).coerceIn(0f, 1f)
    val segmentStretch = if (dragging) {
        sin(Math.PI.toFloat() * segmentFraction).coerceIn(0f, 1f)
    } else 0f
    val direction = when {
        !dragging -> 0f
        dragPosition > selected -> 1f
        dragPosition < selected -> -1f
        else -> 0f
    }

    val pressedScale by animateFloatAsState(
        targetValue = when {
            dragging -> 0.975f
            pressed -> 0.955f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 760f),
        label = "hyperos-navigation-press"
    )
    val indicatorWidthTarget = IndicatorRestingWidthDp * pressedScale +
        if (dragging) IndicatorMaxStretchDp * segmentStretch else 0f
    val indicatorHeightTarget = IndicatorRestingHeightDp *
        (pressedScale - if (dragging) 0.018f * segmentStretch else 0f)
    val baseCenter = NavigationHorizontalPaddingDp +
        visualPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val indicatorCenterTarget = baseCenter + direction * segmentStretch * 3.5f

    val indicatorCenter by animateFloatAsState(
        targetValue = indicatorCenterTarget,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.82f, stiffness = 560f),
        label = "hyperos-navigation-center"
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = indicatorWidthTarget,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "hyperos-navigation-width"
    )
    val indicatorHeight by animateFloatAsState(
        targetValue = indicatorHeightTarget,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 700f),
        label = "hyperos-navigation-height"
    )

    val containerColor = if (dark) Color(0xE61B1B1D) else Color(0xE6F4F4F5)
    val indicatorColor = if (dark) Color(0xFF3A3A3E) else Color(0xFFE0E0E3)
    val indicatorTranslationDp = indicatorCenter - indicatorWidth / 2f

    fun commitSelection(target: Int) {
        val safeTarget = target.coerceIn(0, NavigationItemCount - 1)
        if (safeTarget == updatedSelectedIndex) {
            pendingTarget = null
            dragPosition = updatedSelectedIndex.toFloat()
            return
        }
        if (pendingTarget != null) return
        pendingTarget = safeTarget
        updatedOnSelected(safeTarget)
    }

    val barModifier = Modifier
        .width(248.dp)
        .height(62.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(36f, 36f) },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.78f)) }
                )
            } else Modifier.background(containerColor, CircleShape)
        )
        .clip(CircleShape)
        .pointerInput(density, interactionLocked) {
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
                            pressed = !interactionLocked &&
                                change.position.x in (center - halfIndicatorPx)..(center + halfIndicatorPx)
                        } else if (change.previousPressed && !change.pressed) pressed = false
                    }
                }
            }
        }
        .pointerInput(density, interactionLocked) {
            val itemWidthPx = NavigationItemWidthDp * density.density
            val paddingPx = NavigationHorizontalPaddingDp * density.density
            val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    val current = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
                    val center = paddingPx + current * itemWidthPx + itemWidthPx / 2f
                    dragAccepted = !interactionLocked &&
                        start.x in (center - halfIndicatorPx)..(center + halfIndicatorPx)
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
                        val target = dragPosition.roundToInt()
                        dragAccepted = false
                        dragging = false
                        pressed = false
                        commitSelection(target)
                    }
                },
                onDragCancel = {
                    if (dragAccepted) {
                        dragAccepted = false
                        dragging = false
                        pressed = false
                        dragPosition = updatedSelectedIndex.toFloat()
                    }
                }
            )
        }

    Box(
        modifier = Modifier.fillMaxWidth().padding(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = barModifier) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = with(density) { indicatorTranslationDp.dp.toPx() }
                        translationY = with(density) { ((62f - indicatorHeight) / 2f).dp.toPx() }
                    }
                    .width(indicatorWidth.dp)
                    .height(indicatorHeight.dp)
                    .background(indicatorColor, CircleShape)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, visualPosition, 0, dark) { commitSelection(0) }
                SukiNavigationItem("日誌", Icons.Default.List, visualPosition, 1, dark) { commitSelection(1) }
                SukiNavigationItem("設定", MiuixIcons.Settings, visualPosition, 2, dark) { commitSelection(2) }
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
    dark: Boolean,
    onClick: () -> Unit
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val selectedColor = if (dark) Color.White else Color(0xFF171719)
    val unselectedColor = if (dark) Color(0xFF929298) else Color(0xFF707075)
    val color = lerp(unselectedColor, selectedColor, selectedAmount)
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = 0.94f + selectedAmount * 0.06f
                scaleY = 0.94f + selectedAmount * 0.06f
            },
            tint = color
        )
        Text(label, color = color, fontSize = 11.sp)
    }
}
