package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
private const val IndicatorVerticalInsetDp = 4f
private const val IndicatorRestingWidthDp = NavigationItemWidthDp
private const val IndicatorRestingHeightDp = 62f - IndicatorVerticalInsetDp * 2f
private const val IndicatorVelocityStretchDp = 11f
private const val BarWidthDp = 248f

/**
 * HyperOS-like floating navigation.
 *
 * A single pointer stream owns press, drag, tap and release. The selected capsule follows the
 * finger, while its deformation is driven only by horizontal velocity. A new gesture immediately
 * invalidates the previous settle request so old delayed navigation cannot replay over the latest
 * gesture. The outer 4 dp inset is preserved even while the capsule stretches.
 */
@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var releaseHoldPosition by remember { mutableStateOf<Float?>(null) }
    var transitionDirection by remember { mutableFloatStateOf(0f) }
    var motionVelocity by remember { mutableFloatStateOf(0f) }
    var gestureVelocity by remember { mutableFloatStateOf(0f) }
    var navigationGeneration by remember { mutableIntStateOf(0) }
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val visualPosition = remember { Animatable(selectedIndex.toFloat()) }

    val updatedSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)
    val itemWidthPx = NavigationItemWidthDp * density.density

    LaunchedEffect(selectedIndex) {
        if (
            !dragging && navigationJob?.isActive != true && releaseHoldPosition == null &&
            abs(visualPosition.value - selectedIndex) > 0.001f
        ) {
            transitionDirection = when {
                selectedIndex > visualPosition.value -> 1f
                selectedIndex < visualPosition.value -> -1f
                else -> 0f
            }
            visualPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 560f)
            ) { motionVelocity = velocity }
            motionVelocity = 0f
            transitionDirection = 0f
        }
        if (!dragging && navigationJob?.isActive != true) {
            dragPosition = selectedIndex.toFloat()
        }
    }

    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 900f),
        label = "hyperos-navigation-press"
    )

    val centerPosition = when {
        dragging -> dragPosition
        releaseHoldPosition != null -> releaseHoldPosition ?: visualPosition.value
        else -> visualPosition.value
    }
    val activeVelocity = if (dragging) gestureVelocity else motionVelocity
    val speedFactor = (abs(activeVelocity) / 7.5f).coerceIn(0f, 1f)
    val stretchDp = IndicatorVelocityStretchDp * speedFactor
    val motionDirection = when {
        dragging && gestureVelocity > 0.06f -> 1f
        dragging && gestureVelocity < -0.06f -> -1f
        motionVelocity > 0.06f -> 1f
        motionVelocity < -0.06f -> -1f
        else -> transitionDirection
    }

    val baseWidth = IndicatorRestingWidthDp * pressedScale
    val indicatorHeight = IndicatorRestingHeightDp * pressedScale
    val baseCenter = NavigationHorizontalPaddingDp +
        centerPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val baseLeft = baseCenter - baseWidth / 2f
    val baseRight = baseCenter + baseWidth / 2f
    val frontStretch = stretchDp * 0.72f
    val backStretch = stretchDp * 0.28f
    val rawLeft = when {
        motionDirection > 0f -> baseLeft - backStretch
        motionDirection < 0f -> baseLeft - frontStretch
        else -> baseLeft - stretchDp / 2f
    }
    val rawRight = when {
        motionDirection > 0f -> baseRight + frontStretch
        motionDirection < 0f -> baseRight + backStretch
        else -> baseRight + stretchDp / 2f
    }
    val minLeft = NavigationHorizontalPaddingDp
    val maxRight = BarWidthDp - NavigationHorizontalPaddingDp
    val leftOverflow = max(0f, minLeft - rawLeft)
    val rightOverflow = max(0f, rawRight - maxRight)
    val indicatorLeft = (rawLeft - rightOverflow).coerceAtLeast(minLeft)
    val indicatorRight = (rawRight + leftOverflow).coerceAtMost(maxRight)
    val indicatorWidth = (indicatorRight - indicatorLeft).coerceAtLeast(1f)

    val containerColor = if (dark) Color(0xD91E1E21) else Color(0xEAF4F4F5)
    val indicatorColor = if (dark) Color(0xFF3B3B3F) else Color(0xFFE0E0E3)
    val outlineColor = if (dark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.07f)

    fun commitSelection(
        target: Int,
        releaseVelocityItems: Float = 0f,
        startPosition: Float? = null
    ) {
        val safeTarget = target.coerceIn(0, NavigationItemCount - 1)
        val current = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
        val fromPosition = (startPosition ?: visualPosition.value)
            .coerceIn(0f, (NavigationItemCount - 1).toFloat())
        val delta = safeTarget - fromPosition

        navigationGeneration += 1
        val generation = navigationGeneration
        navigationJob?.cancel()

        if (safeTarget == current && startPosition == null && abs(delta) < 0.001f) {
            motionVelocity = 0f
            gestureVelocity = 0f
            transitionDirection = 0f
            releaseHoldPosition = null
            dragPosition = current.toFloat()
            navigationJob = null
            return
        }

        if (abs(delta) < 0.001f) {
            motionVelocity = 0f
            gestureVelocity = 0f
            transitionDirection = 0f
            releaseHoldPosition = null
            dragPosition = safeTarget.toFloat()
            navigationJob = null
            if (safeTarget != current && generation == navigationGeneration) {
                updatedOnSelected(safeTarget)
            }
            return
        }

        val direction = if (delta > 0f) 1f else -1f
        val distance = abs(delta)
        val carriedVelocity = if (
            abs(releaseVelocityItems) >= 0.18f && releaseVelocityItems * direction > 0f
        ) {
            releaseVelocityItems.coerceIn(-9f, 9f)
        } else {
            direction * (1.9f + 0.45f * (distance - 1f).coerceAtLeast(0f))
        }
        val stiffness = 500f + min(abs(carriedVelocity) * 24f, 170f)
        val leadDelayMs = (96f - min(abs(carriedVelocity) * 6f, 34f)).roundToInt().coerceIn(62, 96)
        val changesPage = safeTarget != current

        transitionDirection = direction
        releaseHoldPosition = fromPosition
        gestureVelocity = carriedVelocity
        navigationJob = coroutineScope.launch {
            visualPosition.snapTo(fromPosition)
            releaseHoldPosition = null
            motionVelocity = carriedVelocity

            val pageJob = if (changesPage) {
                launch {
                    delay(leadDelayMs.toLong())
                    if (generation == navigationGeneration) {
                        updatedOnSelected(safeTarget)
                    }
                }
            } else null

            visualPosition.animateTo(
                targetValue = safeTarget.toFloat(),
                animationSpec = spring(dampingRatio = 0.88f, stiffness = stiffness),
                initialVelocity = carriedVelocity
            ) { motionVelocity = velocity }

            pageJob?.join()
            if (generation == navigationGeneration) {
                motionVelocity = 0f
                gestureVelocity = 0f
                transitionDirection = 0f
                dragPosition = safeTarget.toFloat()
                navigationJob = null
            }
        }
    }

    val barModifier = Modifier
        .width(BarWidthDp.dp)
        .height(62.dp)
        .then(
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { blur(34f, 34f) },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.80f)) }
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
                        translationY = with(density) { ((62f - indicatorHeight) / 2f).dp.toPx() }
                    }
                    .width(indicatorWidth.dp)
                    .height(indicatorHeight.dp)
                    .background(indicatorColor, CircleShape)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = NavigationHorizontalPaddingDp.dp)) {
                SukiNavigationItem("概觀", MiuixIcons.Home, centerPosition, 0, dark)
                SukiNavigationItem("日誌", Icons.Default.List, centerPosition, 1, dark)
                SukiNavigationItem("設定", MiuixIcons.Settings, centerPosition, 2, dark)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(density, viewConfiguration.touchSlop) {
                        val paddingPx = NavigationHorizontalPaddingDp * density.density
                        val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            navigationGeneration += 1
                            navigationJob?.cancel()
                            navigationJob = null
                            releaseHoldPosition = null
                            transitionDirection = 0f
                            motionVelocity = 0f
                            gestureVelocity = 0f
                            visualPosition.stop()

                            val downVisualPosition = visualPosition.value
                                .coerceIn(0f, (NavigationItemCount - 1).toFloat())
                            val selectedCenter = paddingPx +
                                downVisualPosition * itemWidthPx + itemWidthPx / 2f
                            val startsInSelectedCapsule = down.position.x in
                                (selectedCenter - halfIndicatorPx)..(selectedCenter + halfIndicatorPx)

                            pressed = startsInSelectedCapsule
                            dragging = false
                            dragPosition = downVisualPosition

                            val downX = down.position.x
                            var lastX = downX
                            var lastSampleNanos = System.nanoTime()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    pressed = false
                                    dragging = false
                                    gestureVelocity = 0f
                                    break
                                }

                                if (!change.pressed) {
                                    val wasDragging = dragging
                                    val releaseStart = if (wasDragging) dragPosition else null
                                    val velocityItems = gestureVelocity
                                    val projectedPosition = dragPosition +
                                        velocityItems.coerceIn(-6f, 6f) * 0.075f
                                    val releaseX = change.position.x
                                    val target = if (wasDragging) {
                                        projectedPosition.roundToInt().coerceIn(0, NavigationItemCount - 1)
                                    } else {
                                        (((releaseX - paddingPx) / itemWidthPx).toInt())
                                            .coerceIn(0, NavigationItemCount - 1)
                                    }

                                    pressed = false
                                    dragging = false

                                    if (wasDragging) {
                                        commitSelection(target, velocityItems, releaseStart)
                                    } else {
                                        gestureVelocity = 0f
                                        commitSelection(target)
                                    }
                                    break
                                }

                                val dx = change.position.x - lastX
                                val totalDx = change.position.x - downX

                                if (startsInSelectedCapsule && !dragging && abs(totalDx) > touchSlop) {
                                    dragging = true
                                }

                                if (dragging && abs(dx) > 0.01f) {
                                    change.consume()
                                    dragPosition = (dragPosition + dx / itemWidthPx)
                                        .coerceIn(0f, (NavigationItemCount - 1).toFloat())

                                    val now = System.nanoTime()
                                    val dt = (now - lastSampleNanos) / 1_000_000_000f
                                    if (dt in 0.001f..0.08f) {
                                        val instantaneousItems = (dx / dt) / itemWidthPx
                                        gestureVelocity =
                                            gestureVelocity * 0.55f + instantaneousItems * 0.45f
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
    dark: Boolean
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val selectedColor = if (dark) Color.White else Color(0xFF171719)
    val unselectedColor = if (dark) Color(0xFF8A8A90) else Color(0xFF737378)
    val color = lerp(unselectedColor, selectedColor, selectedAmount)
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp).graphicsLayer {
                val base = 0.95f + selectedAmount * 0.05f
                scaleX = base
                scaleY = base
            },
            tint = color
        )
        Text(label, color = color, fontSize = 11.sp)
    }
}
