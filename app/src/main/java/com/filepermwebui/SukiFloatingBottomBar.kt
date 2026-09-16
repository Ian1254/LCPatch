package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
private const val IndicatorRestingWidthDp = 74f
private const val IndicatorRestingHeightDp = 54f
private const val IndicatorVelocityStretchDp = 11f
private const val IndicatorDragStretchDp = 8f
private const val MaxDragPreviewItems = 0.18f

/**
 * HyperOS-like floating navigation.
 *
 * While the finger is down the selected capsule only shows local tension and a small directional
 * preview; the page does not move. Release commits navigation: the capsule leads, the page follows,
 * and arrival is confirmed by a deliberately strong shrink-and-settle selection motion.
 */
@Composable
internal fun SukiFloatingBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragAccepted by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }
    var transitionDirection by remember { mutableFloatStateOf(0f) }
    var motionVelocity by remember { mutableFloatStateOf(0f) }
    val visualPosition = remember { Animatable(selectedIndex.toFloat()) }
    val selectionPulse = remember { Animatable(1f) }

    val updatedSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)
    val interactionLocked = pendingTarget != null
    val itemWidthPx = NavigationItemWidthDp * density.density

    fun playSelectionSnap() {
        coroutineScope.launch {
            selectionPulse.stop()
            selectionPulse.snapTo(1f)
            selectionPulse.animateTo(
                0.84f,
                animationSpec = spring(dampingRatio = 0.76f, stiffness = 980f)
            )
            selectionPulse.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f)
            )
        }
    }

    LaunchedEffect(selectedIndex) {
        if (pendingTarget == selectedIndex) {
            pendingTarget = null
        } else if (!dragging && pendingTarget == null && abs(visualPosition.value - selectedIndex) > 0.001f) {
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
            playSelectionSnap()
        }
        if (!dragging) dragPosition = selectedIndex.toFloat()
    }

    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 900f),
        label = "hyperos-navigation-press"
    )

    val currentIndex = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
    val rawDragDelta = if (dragging) dragPosition - currentIndex else 0f
    val previewOffset = rawDragDelta.coerceIn(-MaxDragPreviewItems, MaxDragPreviewItems)
    val dragTension = (abs(rawDragDelta) / 0.70f).coerceIn(0f, 1f)
    val speedFactor = (abs(motionVelocity) / 7.5f).coerceIn(0f, 1f)
    val dragStretch = if (dragging) IndicatorDragStretchDp * dragTension else 0f
    val velocityStretch = if (!dragging) IndicatorVelocityStretchDp * speedFactor else 0f
    val stretchDp = dragStretch + velocityStretch
    val motionDirection = when {
        dragging && rawDragDelta > 0.01f -> 1f
        dragging && rawDragDelta < -0.01f -> -1f
        motionVelocity > 0.06f -> 1f
        motionVelocity < -0.06f -> -1f
        else -> transitionDirection
    }
    val selectedScale = selectionPulse.value
    val indicatorWidth = (IndicatorRestingWidthDp * pressedScale + stretchDp) * selectedScale
    val indicatorHeight = IndicatorRestingHeightDp * pressedScale * selectedScale
    val centerPosition = visualPosition.value + previewOffset
    val baseCenter = NavigationHorizontalPaddingDp +
        centerPosition * NavigationItemWidthDp + NavigationItemWidthDp / 2f
    val indicatorCenter = baseCenter + motionDirection * stretchDp * 0.16f
    val indicatorTranslationDp = indicatorCenter - indicatorWidth / 2f

    val containerColor = if (dark) Color(0xD91E1E21) else Color(0xEAF4F4F5)
    val indicatorColor = if (dark) Color(0xFF3B3B3F) else Color(0xFFE0E0E3)
    val outlineColor = if (dark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.07f)

    fun commitSelection(target: Int, releaseVelocityItems: Float = 0f) {
        val safeTarget = target.coerceIn(0, NavigationItemCount - 1)
        val current = updatedSelectedIndex.coerceIn(0, NavigationItemCount - 1)
        if (safeTarget == current) {
            pendingTarget = null
            dragPosition = current.toFloat()
            playSelectionSnap()
            return
        }
        if (pendingTarget != null) return

        val direction = if (safeTarget > current) 1f else -1f
        val distance = abs(safeTarget - current).toFloat()
        val carriedVelocity = if (abs(releaseVelocityItems) >= 0.18f) {
            releaseVelocityItems.coerceIn(-9f, 9f)
        } else {
            direction * (2.2f + 0.55f * (distance - 1f).coerceAtLeast(0f))
        }
        val stiffness = 500f + min(abs(carriedVelocity) * 24f, 170f)
        val leadDelayMs = (108f - min(abs(carriedVelocity) * 7f, 44f)).roundToInt().coerceIn(62, 108)

        pendingTarget = safeTarget
        transitionDirection = direction

        coroutineScope.launch {
            visualPosition.animateTo(
                targetValue = safeTarget.toFloat(),
                animationSpec = spring(dampingRatio = 0.86f, stiffness = stiffness),
                initialVelocity = carriedVelocity
            ) { motionVelocity = velocity }
            motionVelocity = 0f
            transitionDirection = 0f
            playSelectionSnap()
        }
        coroutineScope.launch {
            delay(leadDelayMs.toLong())
            if (pendingTarget == safeTarget) updatedOnSelected(safeTarget)
        }
    }

    val barModifier = Modifier
        .width(248.dp)
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
        .pointerInput(density, interactionLocked) {
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
                        } else if (change.previousPressed && !change.pressed) {
                            pressed = false
                        }
                    }
                }
            }
        }
        .pointerInput(density, interactionLocked) {
            val paddingPx = NavigationHorizontalPaddingDp * density.density
            val halfIndicatorPx = IndicatorRestingWidthDp * density.density / 2f
            var lastSampleNanos = 0L
            var releaseVelocityPx = 0f
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
                        lastSampleNanos = System.nanoTime()
                        releaseVelocityPx = 0f
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (dragAccepted) {
                        change.consume()
                        dragPosition = (dragPosition + amount / itemWidthPx)
                            .coerceIn(0f, (NavigationItemCount - 1).toFloat())
                        val now = System.nanoTime()
                        val dt = (now - lastSampleNanos) / 1_000_000_000f
                        if (dt in 0.001f..0.08f) {
                            val instantaneous = amount / dt
                            releaseVelocityPx = releaseVelocityPx * 0.62f + instantaneous * 0.38f
                        }
                        lastSampleNanos = now
                    }
                },
                onDragEnd = {
                    if (dragAccepted) {
                        val velocityItems = releaseVelocityPx / itemWidthPx
                        val projectedPosition = dragPosition + velocityItems.coerceIn(-6f, 6f) * 0.075f
                        val target = projectedPosition.roundToInt().coerceIn(0, NavigationItemCount - 1)
                        dragAccepted = false
                        dragging = false
                        pressed = false
                        commitSelection(target, velocityItems)
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
                SukiNavigationItem("概觀", MiuixIcons.Home, centerPosition, 0, dark, selectionPulse.value) {
                    commitSelection(0)
                }
                SukiNavigationItem("日誌", Icons.Default.List, centerPosition, 1, dark, selectionPulse.value) {
                    commitSelection(1)
                }
                SukiNavigationItem("設定", MiuixIcons.Settings, centerPosition, 2, dark, selectionPulse.value) {
                    commitSelection(2)
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
    dark: Boolean,
    selectionScale: Float,
    onClick: () -> Unit
) {
    val selectedAmount = (1f - abs(selectionPosition - index)).coerceIn(0f, 1f)
    val selectedColor = if (dark) Color.White else Color(0xFF171719)
    val unselectedColor = if (dark) Color(0xFF8A8A90) else Color(0xFF737378)
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
                val base = 0.95f + selectedAmount * 0.05f
                val pulse = 1f - selectedAmount * (1f - selectionScale) * 0.45f
                scaleX = base * pulse
                scaleY = base * pulse
            },
            tint = color
        )
        Text(label, color = color, fontSize = 11.sp)
    }
}
