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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
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

private const val NavigationItemCount = 3
private const val NavigationItemWidthDp = 80f
private const val NavigationHorizontalPaddingDp = 4f
private const val IndicatorRestingWidthDp = 72f
private const val IndicatorRestingHeightDp = 54f
private const val IndicatorVelocityStretchDp = 12f
private const val BarWidthDp = 248f
private const val BarHeightDp = 62f

/**
 * Floating navigation whose selected position is driven by the same pager position as the page.
 * The capsule stays anchored while the user drags and only starts travelling after release, while
 * horizontal velocity still deforms it during the gesture. This removes the second independent
 * settle animation that previously caused replay and navigation/page desynchronisation.
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
    val dark = isMiuixDarkTheme()

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var selectedGesture by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var gestureVelocity by remember { mutableFloatStateOf(0f) }
    var pageMotionVelocity by remember { mutableFloatStateOf(0f) }
    var lastPagePosition by remember { mutableFloatStateOf(pagePosition) }
    var lastPageSampleNanos by remember { mutableStateOf(System.nanoTime()) }

    LaunchedEffect(pagePosition) {
        val now = System.nanoTime()
        val dt = (now - lastPageSampleNanos) / 1_000_000_000f
        val delta = pagePosition - lastPagePosition
        if (dt in 0.001f..0.08f) {
            pageMotionVelocity = if (abs(delta) < 0.0001f) 0f else delta / dt
        }
        lastPagePosition = pagePosition
        lastPageSampleNanos = now
    }
    LaunchedEffect(selectedIndex, pagePosition) {
        if (abs(pagePosition - selectedIndex) < 0.001f && !dragging) {
            pageMotionVelocity = 0f
        }
    }

    val pressFraction by animateFloatAsState(
        targetValue = if (pressedIndex >= 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
        label = "floating-navigation-press"
    )

    val selectedPress = if (selectedGesture && pressedIndex >= 0) pressFraction else 0f
    val pressedScale = 1f - selectedPress * 0.055f
    val centerPosition = pagePosition.coerceIn(0f, (NavigationItemCount - 1).toFloat())
    val activeVelocity = if (dragging) gestureVelocity else pageMotionVelocity
    val speedFactor = (abs(activeVelocity) / 7.5f).coerceIn(0f, 1f)
    val stretchDp = IndicatorVelocityStretchDp * speedFactor
    val motionDirection = when {
        activeVelocity > 0.06f -> 1f
        activeVelocity < -0.06f -> -1f
        else -> 0f
    }

    val baseWidth = IndicatorRestingWidthDp * pressedScale
    val indicatorHeight = IndicatorRestingHeightDp * pressedScale
    val baseCenter = NavigationHorizontalPaddingDp +
        centerPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val baseLeft = baseCenter - baseWidth / 2f
    val baseRight = baseCenter + baseWidth / 2f
    val minLeft = NavigationHorizontalPaddingDp
    val maxRight = BarWidthDp - NavigationHorizontalPaddingDp

    fun resistedStretch(amount: Float, available: Float): Float {
        if (amount <= 0f || available <= 0f) return 0f
        return amount / (1f + amount / (available * 0.85f).coerceAtLeast(0.5f))
    }

    val desiredFront = stretchDp * 0.74f
    val desiredBack = stretchDp * 0.26f
    val indicatorLeft = when {
        motionDirection < 0f -> baseLeft - resistedStretch(desiredFront, baseLeft - minLeft)
        motionDirection > 0f -> baseLeft - resistedStretch(desiredBack, baseLeft - minLeft)
        else -> baseLeft
    }.coerceAtLeast(minLeft)
    val indicatorRight = when {
        motionDirection > 0f -> baseRight + resistedStretch(desiredFront, maxRight - baseRight)
        motionDirection < 0f -> baseRight + resistedStretch(desiredBack, maxRight - baseRight)
        else -> baseRight
    }.coerceAtMost(maxRight)
    val indicatorWidth = (indicatorRight - indicatorLeft).coerceAtLeast(1f)

    val containerTint = if (dark) Color(0xFF1E1E21) else Color(0xFFF4F4F5)
    val fallbackContainer = containerTint.copy(alpha = if (dark) 0.94f else 0.96f)
    val indicatorColor = if (dark) Color(0xE63B3B3F) else Color(0xECE0E0E3)
    val outlineColor = if (dark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.07f)

    val barModifier = Modifier
        .width(BarWidthDp.dp)
        .height(BarHeightDp.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(30f, 30f) },
                    onDrawSurface = { drawRect(containerTint.copy(alpha = 0.58f)) }
                )
            } else {
                Modifier.background(fallbackContainer, CircleShape)
            }
        )
        .border(1.dp, outlineColor, CircleShape)
        .clip(CircleShape)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = barModifier) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = with(density) { indicatorLeft.dp.toPx() }
                        translationY = with(density) {
                            ((BarHeightDp - indicatorHeight) / 2f).dp.toPx()
                        }
                    }
                    .width(indicatorWidth.dp)
                    .height(indicatorHeight.dp)
                    .background(indicatorColor, CircleShape)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = NavigationHorizontalPaddingDp.dp)
            ) {
                SukiNavigationItem(
                    label = "概觀",
                    icon = MiuixIcons.Home,
                    selectionPosition = centerPosition,
                    index = 0,
                    dark = dark,
                    pressFraction = if (pressedIndex == 0) pressFraction else 0f,
                    selected = selectedIndex == 0,
                    onSemanticClick = { onSelected(0) }
                )
                SukiNavigationItem(
                    label = "日誌",
                    icon = Icons.Default.List,
                    selectionPosition = centerPosition,
                    index = 1,
                    dark = dark,
                    pressFraction = if (pressedIndex == 1) pressFraction else 0f,
                    selected = selectedIndex == 1,
                    onSemanticClick = { onSelected(1) }
                )
                SukiNavigationItem(
                    label = "設定",
                    icon = MiuixIcons.Settings,
                    selectionPosition = centerPosition,
                    index = 2,
                    dark = dark,
                    pressFraction = if (pressedIndex == 2) pressFraction else 0f,
                    selected = selectedIndex == 2,
                    onSemanticClick = { onSelected(2) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(density, viewConfiguration.touchSlop, selectedIndex) {
                        val paddingPx = NavigationHorizontalPaddingDp * density.density
                        val itemWidthPx = NavigationItemWidthDp * density.density
                        val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
                        val touchSlop = viewConfiguration.touchSlop

                        fun itemAt(x: Float): Int =
                            (((x - paddingPx) / itemWidthPx).toInt())
                                .coerceIn(0, NavigationItemCount - 1)

                        fun positionAt(x: Float): Float =
                            ((x - paddingPx - itemWidthPx / 2f) / itemWidthPx)
                                .coerceIn(0f, (NavigationItemCount - 1).toFloat())

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val selectedCenterPx = paddingPx +
                                centerPosition * itemWidthPx + itemWidthPx / 2f
                            val startsInSelectedCapsule = down.position.x in
                                (selectedCenterPx - halfIndicatorPx)..(selectedCenterPx + halfIndicatorPx)

                            pressedIndex = itemAt(down.position.x)
                            selectedGesture = startsInSelectedCapsule
                            dragging = false
                            gestureVelocity = 0f

                            val downX = down.position.x
                            var lastX = downX
                            var lastSampleNanos = System.nanoTime()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    pressedIndex = -1
                                    selectedGesture = false
                                    dragging = false
                                    gestureVelocity = 0f
                                    break
                                }

                                if (!change.pressed) {
                                    val releaseX = change.position.x
                                    val velocityItems = gestureVelocity
                                    val target = if (dragging) {
                                        val projected = positionAt(releaseX) +
                                            velocityItems.coerceIn(-6f, 6f) * 0.055f
                                        projected.roundToInt().coerceIn(0, NavigationItemCount - 1)
                                    } else {
                                        itemAt(releaseX)
                                    }

                                    pressedIndex = -1
                                    selectedGesture = false
                                    dragging = false
                                    gestureVelocity = 0f
                                    if (target != selectedIndex) onSelected(target)
                                    break
                                }

                                val dx = change.position.x - lastX
                                val totalDx = change.position.x - downX
                                val horizontalDominant =
                                    abs(totalDx) > abs(change.position.y - down.position.y)

                                if (
                                    startsInSelectedCapsule && horizontalDominant &&
                                    !dragging && abs(totalDx) > touchSlop
                                ) {
                                    dragging = true
                                }

                                if (startsInSelectedCapsule && abs(dx) > 0.01f) {
                                    val now = System.nanoTime()
                                    val dt = (now - lastSampleNanos) / 1_000_000_000f
                                    if (dt in 0.001f..0.08f) {
                                        val instantaneousItems = (dx / dt) / itemWidthPx
                                        gestureVelocity =
                                            gestureVelocity * 0.55f + instantaneousItems * 0.45f
                                    }
                                    lastSampleNanos = now
                                }

                                if (dragging) change.consume()
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
    pressFraction: Float,
    selected: Boolean,
    onSemanticClick: () -> Unit
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val selectedColor = if (dark) Color.White else Color(0xFF171719)
    val unselectedColor = if (dark) Color(0xFF8A8A90) else Color(0xFF737378)
    val color = lerp(unselectedColor, selectedColor, selectedAmount)
    val baseScale = 0.95f + selectedAmount * 0.05f
    val contentScale = baseScale * (1f - pressFraction * 0.035f)
    val contentAlpha = 1f - pressFraction * 0.12f

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                onClick(label = label) {
                    onSemanticClick()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
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
            modifier = Modifier.graphicsLayer {
                scaleX = 1f - pressFraction * 0.025f
                scaleY = 1f - pressFraction * 0.025f
                alpha = contentAlpha
            }
        )
    }
}
