package com.lcpatch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
private const val IndicatorVerticalInsetDp = 4f
private const val IndicatorRestingWidthDp = 72f
private const val IndicatorVelocityStretchDp = 10f
private const val BarWidthDp = 248f

/**
 * Floating navigation whose settling position is driven by the pager itself.
 * Dragging only previews the pill; releasing selects a page, then page + pill move on one progress.
 */
@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    pagePosition: Float,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val updatedOnSelected by rememberUpdatedState(onSelected)
    val updatedSelectedIndex by rememberUpdatedState(selectedIndex)
    val fontScale = density.fontScale
    val barHeightDp = 62f + ((fontScale - 1f).coerceIn(0f, 1f) * 10f)
    val indicatorRestingHeightDp = barHeightDp - IndicatorVerticalInsetDp * 2f
    val itemWidthPx = NavigationItemWidthDp * density.density

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(pagePosition) }
    var gestureVelocity by remember { mutableFloatStateOf(0f) }
    var gestureGeneration by remember { mutableIntStateOf(0) }

    val pressedScale by animateFloatAsState(
        targetValue = if (pressedIndex >= 0) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 780f),
        label = "navigation-press"
    )
    val pressFraction = ((1f - pressedScale) / 0.035f).coerceIn(0f, 1f)
    val centerPosition = if (dragging) dragPosition else pagePosition.coerceIn(0f, 2f)
    val speedFactor = (abs(gestureVelocity) / 7.5f).coerceIn(0f, 1f)
    val stretchDp = IndicatorVelocityStretchDp * speedFactor
    val direction = when {
        gestureVelocity > 0.06f -> 1f
        gestureVelocity < -0.06f -> -1f
        else -> 0f
    }

    val indicatorHeight = indicatorRestingHeightDp *
        if (pressedIndex == updatedSelectedIndex) pressedScale else 1f
    val baseCenter = NavigationHorizontalPaddingDp +
        centerPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val baseLeft = baseCenter - IndicatorRestingWidthDp / 2f
    val baseRight = baseCenter + IndicatorRestingWidthDp / 2f
    val frontStretch = stretchDp * 0.70f
    val backStretch = stretchDp * 0.30f
    var rawLeft = when {
        direction > 0f -> baseLeft - backStretch
        direction < 0f -> baseLeft - frontStretch
        else -> baseLeft - stretchDp / 2f
    }
    var rawRight = when {
        direction > 0f -> baseRight + frontStretch
        direction < 0f -> baseRight + backStretch
        else -> baseRight + stretchDp / 2f
    }
    val minLeft = NavigationHorizontalPaddingDp
    val maxRight = BarWidthDp - NavigationHorizontalPaddingDp
    if (rawLeft < minLeft) rawLeft = minLeft - (minLeft - rawLeft) * 0.22f
    if (rawRight > maxRight) rawRight = maxRight + (rawRight - maxRight) * 0.22f
    val indicatorLeft = rawLeft.coerceAtLeast(0f)
    val indicatorRight = rawRight.coerceAtMost(BarWidthDp)
    val indicatorWidth = (indicatorRight - indicatorLeft).coerceAtLeast(1f)

    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (dark) 0.48f else 0.40f)
    val indicatorColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = if (dark) 0.82f else 0.78f)
    val outlineColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    val barModifier = Modifier
        .width(BarWidthDp.dp)
        .height(barHeightDp.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(28f, 28f) },
                    onDrawSurface = { drawRect(containerColor) }
                )
            } else Modifier.background(containerColor, CircleShape)
        )
        .border(1.dp, outlineColor, CircleShape)
        .clip(CircleShape)

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
                        translationX = with(density) { indicatorLeft.dp.toPx() }
                        translationY = with(density) { ((barHeightDp - indicatorHeight) / 2f).dp.toPx() }
                    }
                    .width(indicatorWidth.dp)
                    .height(indicatorHeight.dp)
                    .background(indicatorColor, CircleShape)
            )

            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = NavigationHorizontalPaddingDp.dp)
            ) {
                SukiNavigationItem("概觀", MiuixIcons.Home, centerPosition, 0, dark, pressedIndex, pressFraction)
                SukiNavigationItem("日誌", Icons.Default.List, centerPosition, 1, dark, pressedIndex, pressFraction)
                SukiNavigationItem("設定", MiuixIcons.Settings, centerPosition, 2, dark, pressedIndex, pressFraction)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(density, viewConfiguration.touchSlop) {
                        val paddingPx = NavigationHorizontalPaddingDp * density.density
                        val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val generation = ++gestureGeneration
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPosition = pagePosition.coerceIn(0f, 2f)
                            val downIndex = (((down.position.x - paddingPx) / itemWidthPx).toInt()).coerceIn(0, 2)
                            val selectedCenter = paddingPx + downPosition * itemWidthPx + itemWidthPx / 2f
                            val startsInSelectedCapsule = down.position.x in
                                (selectedCenter - halfIndicatorPx)..(selectedCenter + halfIndicatorPx)

                            pressedIndex = downIndex
                            dragging = false
                            dragPosition = downPosition
                            gestureVelocity = 0f
                            val downX = down.position.x
                            var lastX = downX
                            var lastSampleNanos = System.nanoTime()

                            while (generation == gestureGeneration) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    pressedIndex = -1
                                    dragging = false
                                    gestureVelocity = 0f
                                    break
                                }

                                if (!change.pressed) {
                                    val wasDragging = dragging
                                    val target = if (wasDragging) {
                                        (dragPosition + gestureVelocity.coerceIn(-6f, 6f) * 0.075f)
                                            .roundToInt().coerceIn(0, NavigationItemCount - 1)
                                    } else downIndex
                                    pressedIndex = -1
                                    dragging = false
                                    gestureVelocity = 0f
                                    if (target != updatedSelectedIndex) updatedOnSelected(target)
                                    break
                                }

                                val dx = change.position.x - lastX
                                val totalDx = change.position.x - downX
                                if (startsInSelectedCapsule && !dragging && abs(totalDx) > touchSlop) {
                                    dragging = true
                                }

                                if (dragging && abs(dx) > 0.01f) {
                                    change.consume()
                                    val next = dragPosition + dx / itemWidthPx
                                    dragPosition = when {
                                        next < 0f -> next * 0.22f
                                        next > 2f -> 2f + (next - 2f) * 0.22f
                                        else -> next
                                    }
                                    val now = System.nanoTime()
                                    val dt = (now - lastSampleNanos) / 1_000_000_000f
                                    if (dt in 0.001f..0.08f) {
                                        val instantaneous = (dx / dt) / itemWidthPx
                                        gestureVelocity = gestureVelocity * 0.55f + instantaneous * 0.45f
                                    }
                                    lastSampleNanos = now
                                }
                                lastX = change.position.x
                            }
                        }
                    }
            )
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
    pressedIndex: Int,
    pressFraction: Float
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val localPress = if (pressedIndex == index) pressFraction else 0f
    val selectedColor = if (dark) Color.White else MiuixTheme.colorScheme.onSurface
    val unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val color = lerp(unselectedColor, selectedColor, selectedAmount)
    val contentScale = (0.95f + selectedAmount * 0.05f) * (1f - localPress * 0.035f)
    val contentAlpha = 1f - localPress * 0.12f

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                selected = selectedAmount > 0.5f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                alpha = contentAlpha
            },
            tint = color
        )
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = 1f - localPress * 0.025f
                scaleY = 1f - localPress * 0.025f
                alpha = contentAlpha
            }
        )
    }
}
