package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

private const val ItemCount = 3
private const val BarWidthDp = 240f
private const val BarHeightDp = 52f
private const val HorizontalPaddingDp = 7f
private const val ItemWidthDp = (BarWidthDp - 2f * HorizontalPaddingDp) / ItemCount
private const val SelectorHeightDp = 45f
private const val SelectorWidthExtraDp = 7f
private const val EdgeOverscrollItems = 0.075f
private const val MaxEdgeStretchDp = 24f
private const val DragFollowMs = 14f
private const val DragTrailRelaxationMs = 24f
private const val DragTrailVelocityWindowMs = 20f

private val PressSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 650f,
    visibilityThreshold = 0.001f
)
private val GestureSettleSpring = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = 520f,
    visibilityThreshold = 0.001f
)
private data class SelectorMotionSegment(
    val startPagerPosition: Float,
    val startLeftPx: Float,
    val startRightPx: Float,
    val startVisualPosition: Float,
    val targetIndex: Int,
    val transactionId: Int
)

@Composable
internal fun SukiFloatingBottomBar(
    currentIndex: Int,
    targetIndex: Int,
    transactionId: Int,
    pagePosition: Float,
    onTargetSelected: (Int) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val safeCurrent = currentIndex.coerceIn(0, ItemCount - 1)
    val safeTarget = targetIndex.coerceIn(0, ItemCount - 1)
    val safePagePosition = pagePosition.coerceIn(0f, (ItemCount - 1).toFloat())
    val currentTarget by rememberUpdatedState(onTargetSelected)
    val latestPagePosition by rememberUpdatedState(safePagePosition)
    val latestTarget by rememberUpdatedState(safeTarget)
    val latestTransaction by rememberUpdatedState(transactionId)
    val press = remember { Animatable(0f, 0.001f) }

    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(safePagePosition) }
    var displayedDragPosition by remember { mutableFloatStateOf(safePagePosition) }
    var dragTrailPx by remember { mutableFloatStateOf(0f) }
    var dragTravelItems by remember { mutableFloatStateOf(0f) }
    var releaseSettling by remember { mutableStateOf(false) }
    var gestureGeneration by remember { mutableIntStateOf(0) }
    var releaseSettleJob by remember { mutableStateOf<Job?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var gestureStartLeftPx by remember { mutableFloatStateOf(0f) }
    var gestureStartRightPx by remember { mutableFloatStateOf(0f) }
    var pressOnSelector by remember { mutableStateOf(false) }
    val containerColor = if (dark) Color(0xFF171719) else Color(0xFFF1F1F3)
    val borderColor = if (dark) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.10f)
    val selectorColor = if (dark) Color(0xFF4B4B4E) else Color(0xFFDADADD)
    val textColor = if (dark) Color.White else Color(0xFF151518)
    val mutedColor = textColor.copy(alpha = if (dark) 0.94f else 0.86f)
    val selectedColor = textColor.copy(alpha = 0.72f)
    val itemWidthPx = with(density) { ItemWidthDp.dp.toPx() }
    val paddingPx = with(density) { HorizontalPaddingDp.dp.toPx() }
    val selectorExtraPx = with(density) { SelectorWidthExtraDp.dp.toPx() }
    val selectorWidthPx = itemWidthPx + selectorExtraPx
    val selectorInsetPx = selectorExtraPx / 2f
    val selectorMinLeftPx = paddingPx - selectorInsetPx
    val selectorMaxRightPx = paddingPx + ItemCount * itemWidthPx + selectorInsetPx
    val dragLeft = remember(itemWidthPx, paddingPx) { Animatable(selectorMinLeftPx + safePagePosition * itemWidthPx, 0.01f) }
    val dragRight = remember(itemWidthPx, paddingPx) { Animatable(selectorMinLeftPx + safePagePosition * itemWidthPx + selectorWidthPx, 0.01f) }
    var dragLeftReady by remember { mutableStateOf(true) }
    var dragRightReady by remember { mutableStateOf(true) }
    val selectorHeightPx = with(density) { SelectorHeightDp.dp.toPx() }
    val selectorPress = if (pressOnSelector) press.value else 0f
    val maxEdgeStretchPx = with(density) { MaxEdgeStretchDp.dp.toPx() }

    // Pager drives programmatic navigation. Pointer gestures and their release
    // keep the two animated edges until they have settled.
    val initialMotionLeftPx = selectorMinLeftPx + safePagePosition * itemWidthPx
    var motionSegment by remember {
        mutableStateOf(
            SelectorMotionSegment(
                startPagerPosition = safePagePosition,
                startLeftPx = initialMotionLeftPx,
                startRightPx = initialMotionLeftPx + selectorWidthPx,
                startVisualPosition = safePagePosition,
                targetIndex = safeTarget,
                transactionId = transactionId
            )
        )
    }

    val motionTargetPosition = motionSegment.targetIndex.toFloat()
    val motionTargetLeftPx = selectorMinLeftPx + motionTargetPosition * itemWidthPx
    val motionTargetRightPx = motionTargetLeftPx + selectorWidthPx
    val motionDistance = motionTargetPosition - motionSegment.startPagerPosition
    val motionProgress = if (abs(motionDistance) < 0.0001f) {
        1f
    } else {
        ((safePagePosition - motionSegment.startPagerPosition) / motionDistance).coerceIn(0f, 1f)
    }
    val leadingProgress = leadingEdgeProgress(motionProgress)
    val trailingProgress = trailingEdgeProgress(motionProgress)

    var motionLeftPx = when {
        motionDistance > 0f ->
            lerpFloat(motionSegment.startLeftPx, motionTargetLeftPx, trailingProgress)
        motionDistance < 0f ->
            lerpFloat(motionSegment.startLeftPx, motionTargetLeftPx, leadingProgress)
        else -> motionTargetLeftPx
    }
    var motionRightPx = when {
        motionDistance > 0f ->
            lerpFloat(motionSegment.startRightPx, motionTargetRightPx, leadingProgress)
        motionDistance < 0f ->
            lerpFloat(motionSegment.startRightPx, motionTargetRightPx, trailingProgress)
        else -> motionTargetRightPx
    }

    // Cap edge separation in physical space. A two-page jump therefore keeps
    // roughly the same liquid stretch as a one-page jump instead of becoming
    // an oversized bar.
    val maxMotionWidthPx = selectorWidthPx + maxEdgeStretchPx
    if (motionRightPx - motionLeftPx > maxMotionWidthPx) {
        if (motionDistance >= 0f) {
            motionLeftPx = motionRightPx - maxMotionWidthPx
        } else {
            motionRightPx = motionLeftPx + maxMotionWidthPx
        }
    }

    // Every owner hands over the rendered left and right edges. In particular,
    // taking over a stretched Pager segment must not reset its width in a frame.
    val gestureOwnsGeometry = gestureActive || dragging || releaseSettling
    val dragEdgesReady = dragLeftReady && dragRightReady
    val gestureStartVisualPosition =
        ((gestureStartLeftPx + gestureStartRightPx) / 2f - paddingPx) / itemWidthPx - 0.5f
    val (fingerLeftPx, fingerRightPx) = dragSelectorEdges(
        position = displayedDragPosition,
        trailPx = dragTrailPx,
        travelItems = dragTravelItems,
        startLeftPx = gestureStartLeftPx,
        startRightPx = gestureStartRightPx,
        itemWidthPx = itemWidthPx,
        paddingPx = paddingPx,
        selectorWidthPx = selectorWidthPx,
        minLeftPx = selectorMinLeftPx,
        maxRightPx = selectorMaxRightPx
    )
    val selectorMotionLeftPx = when {
        dragging -> fingerLeftPx
        releaseSettling && dragEdgesReady -> dragLeft.value
        gestureActive || releaseSettling -> gestureStartLeftPx
        else -> motionLeftPx
    }
    val selectorMotionRightPx = when {
        dragging -> fingerRightPx
        releaseSettling && dragEdgesReady -> dragRight.value
        gestureActive || releaseSettling -> gestureStartRightPx
        else -> motionRightPx
    }
    val motionVisualPosition = lerpFloat(
        motionSegment.startVisualPosition,
        motionTargetPosition,
        motionProgress
    )
    val visualPosition = when {
        dragging -> displayedDragPosition.coerceIn(0f, (ItemCount - 1).toFloat())
        releaseSettling && dragEdgesReady ->
            ((dragLeft.value + dragRight.value) / 2f - paddingPx) / itemWidthPx - 0.5f
        gestureActive || releaseSettling -> gestureStartVisualPosition
        else -> motionVisualPosition
    }
    val latestSelectorMotionLeftPx by rememberUpdatedState(selectorMotionLeftPx)
    val latestSelectorMotionRightPx by rememberUpdatedState(selectorMotionRightPx)
    val latestVisualPosition by rememberUpdatedState(visualPosition)
    // Keep the same outer inset during press, drag and spring settle.
    val selectorLeft = selectorMotionLeftPx.coerceAtLeast(selectorMinLeftPx)
    val selectorRight = selectorMotionRightPx.coerceAtMost(selectorMaxRightPx)
    val dynamicHeight = selectorHeightPx
    val selectorTop = with(density) { BarHeightDp.dp.toPx() } / 2f - dynamicHeight / 2f
    val latestVisibleLeftPx by rememberUpdatedState(selectorLeft)
    val latestVisibleRightPx by rememberUpdatedState(selectorRight)
    val latestVisibleTopPx by rememberUpdatedState(selectorTop)
    val latestVisibleBottomPx by rememberUpdatedState(selectorTop + dynamicHeight)

    // Keep the center close to the pointer. Stretch follows its actual motion,
    // not the distance left by the center filter: the latter grows at the edge
    // and leaves a stale tail when the pointer reverses.
    LaunchedEffect(dragging) {
        if (dragging) {
            var previousFrameNanos = 0L
            while (true) {
                val frameNanos = withFrameNanos { it }
                val elapsedMs = if (previousFrameNanos == 0L) 16f else
                    ((frameNanos - previousFrameNanos) / 1_000_000f).coerceIn(1f, 32f)
                previousFrameNanos = frameNanos
                val follow = 1f - exp(-elapsedMs / DragFollowMs)
                val previousPosition = displayedDragPosition
                displayedDragPosition += (dragPosition - displayedDragPosition) * follow
                if (dragPosition <= (ItemCount - 1).toFloat() &&
                    displayedDragPosition > (ItemCount - 1).toFloat()
                ) displayedDragPosition = (ItemCount - 1).toFloat()
                if (dragPosition >= 0f && displayedDragPosition < 0f) displayedDragPosition = 0f
                if (abs(dragPosition - displayedDragPosition) < 0.001f) {
                    displayedDragPosition = dragPosition
                }
                val centerStepPx = (displayedDragPosition - previousPosition) * itemWidthPx
                val targetTrail = (centerStepPx * DragTrailVelocityWindowMs / elapsedMs)
                    .coerceIn(-maxEdgeStretchPx * 0.45f, maxEdgeStretchPx * 0.45f)
                val trailBlend = 1f - exp(-elapsedMs / DragTrailRelaxationMs)
                dragTrailPx += (targetTrail - dragTrailPx) * trailBlend
                if (abs(dragTrailPx) < 0.1f && targetTrail == 0f) dragTrailPx = 0f
            }
        }
    }

    // On rapid retarget, capture the capsule exactly as currently rendered.
    // The new transaction starts from those two real edges, so there is no
    // reset-to-normal-width frame before reversing direction.
    LaunchedEffect(transactionId, safeTarget, gestureOwnsGeometry) {
        if (
            !gestureOwnsGeometry &&
            (motionSegment.transactionId != transactionId || motionSegment.targetIndex != safeTarget)
        ) {
            motionSegment = SelectorMotionSegment(
                startPagerPosition = latestPagePosition,
                startLeftPx = latestSelectorMotionLeftPx,
                startRightPx = latestSelectorMotionRightPx,
                startVisualPosition = latestVisualPosition,
                targetIndex = safeTarget,
                transactionId = transactionId
            )
        }
    }

    Box(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(BarWidthDp.dp)
                .height(BarHeightDp.dp)
                .background(containerColor, CircleShape)
                .border(1.dp, borderColor, CircleShape)
                .clip(CircleShape)
        ) {
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                lerp(
                                    selectorColor,
                                    if (dark) Color(0xFF646469) else Color(0xFFB9B9BE),
                                    selectorPress.coerceIn(0f, 1f)
                                ),
                                CircleShape
                            )
                    )
                }
            ) { measurables, constraints ->
                val left = selectorLeft.roundToInt()
                val right = selectorRight.roundToInt().coerceAtLeast(left + 1)
                val top = selectorTop.roundToInt()
                val bottom = (selectorTop + dynamicHeight).roundToInt().coerceAtLeast(top + 1)
                val placeable = measurables.single().measure(
                    Constraints.fixed(right - left, bottom - top)
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(left, top)
                }
            }

            NavigationRow(
                visualPosition = visualPosition,
                semanticsIndex = safeCurrent,
                mutedColor = mutedColor,
                selectedColor = selectedColor,
                modifier = Modifier.fillMaxSize().padding(horizontal = HorizontalPaddingDp.dp)
            )

            // Navigation commits only on release. Only a gesture beginning
            // inside the selected capsule can move it horizontally.
            Box(
                Modifier.fillMaxSize().pointerInput(itemWidthPx, paddingPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        gestureGeneration += 1
                        val generation = gestureGeneration
                        // Snapshot before cancelling the previous owner. The
                        // new gesture keeps rendering this exact geometry.
                        gestureStartLeftPx = latestSelectorMotionLeftPx
                        gestureStartRightPx = latestSelectorMotionRightPx
                        val startVisualPosition =
                            ((gestureStartLeftPx + gestureStartRightPx) / 2f - paddingPx) / itemWidthPx - 0.5f
                        gestureActive = true
                        releaseSettleJob?.cancel()
                        releaseSettleJob = null
                        dragLeftReady = false
                        dragRightReady = false
                        releaseSettling = false
                        dragging = false
                        val downIndex = floor(
                            (down.position.x - paddingPx) / itemWidthPx
                        ).toInt().coerceIn(0, ItemCount - 1)
                        val startedOnSelector =
                            down.position.x in latestVisibleLeftPx..latestVisibleRightPx &&
                                down.position.y in latestVisibleTopPx..latestVisibleBottomPx
                        // The selected capsule follows the pointer from the first
                        // move. Waiting for touch slop and then subtracting it
                        // makes the capsule visibly lag behind the finger.
                        if (startedOnSelector) {
                            dragPosition = startVisualPosition
                            displayedDragPosition = startVisualPosition
                            dragTrailPx = 0f
                            dragTravelItems = 0f
                            dragging = true
                        }
                        pressOnSelector = startedOnSelector
                        var cancelled = false
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }

                        scope.launch { press.animateTo(1f, PressSpring) }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val totalDx = change.position.x - down.position.x
                            if (dragging) {
                                dragTravelItems = maxOf(dragTravelItems, abs(totalDx / itemWidthPx))
                                dragPosition = rubberBand(
                                    startVisualPosition + totalDx / itemWidthPx,
                                    0f,
                                    (ItemCount - 1).toFloat()
                                )
                                change.consume()
                            }
                            if (!change.pressed) break
                        }

                        val wasDragging = dragging
                        val heldPosition = dragPosition
                        val target = when {
                            cancelled -> latestTarget
                            wasDragging -> heldPosition.roundToInt()
                            else -> downIndex
                        }.coerceIn(0, ItemCount - 1)
                        val heldDragEdges = dragSelectorEdges(
                            displayedDragPosition, dragTrailPx, dragTravelItems,
                            gestureStartLeftPx, gestureStartRightPx, itemWidthPx, paddingPx,
                            selectorWidthPx, selectorMinLeftPx, selectorMaxRightPx
                        )
                        val heldLeftPx = if (wasDragging) heldDragEdges.first else gestureStartLeftPx
                        val heldRightPx = if (wasDragging) heldDragEdges.second else gestureStartRightPx
                        val fingerVelocity = if (wasDragging && !cancelled) {
                            velocityTracker.calculateVelocity().x.coerceIn(
                                -itemWidthPx * 2f, itemWidthPx * 2f
                            )
                        } else 0f
                        gestureStartLeftPx = heldLeftPx
                        gestureStartRightPx = heldRightPx
                        dragging = false

                        val targetLeftPx = selectorMinLeftPx + target * itemWidthPx
                        gestureActive = false
                        if (wasDragging) {
                            // Drag release keeps the edge velocity until the
                            // capsule settles. A tap instead lets Pager own
                            // both the page and selector from the same frame.
                            releaseSettling = true
                            releaseSettleJob = scope.launch {
                                dragLeft.snapTo(heldLeftPx)
                                dragRight.snapTo(heldRightPx)
                                if (gestureGeneration != generation) return@launch
                                dragLeftReady = true
                                dragRightReady = true
                                kotlinx.coroutines.coroutineScope {
                                    launch {
                                        dragLeft.animateTo(
                                            targetLeftPx, GestureSettleSpring,
                                            initialVelocity = fingerVelocity
                                        )
                                    }
                                    launch {
                                        dragRight.animateTo(
                                            targetLeftPx + selectorWidthPx, GestureSettleSpring,
                                            initialVelocity = fingerVelocity
                                        )
                                    }
                                }
                                if (gestureGeneration == generation) {
                                    motionSegment = SelectorMotionSegment(
                                        startPagerPosition = latestPagePosition,
                                        startLeftPx = targetLeftPx,
                                        startRightPx = targetLeftPx + selectorWidthPx,
                                        startVisualPosition = target.toFloat(),
                                        targetIndex = target,
                                        transactionId = latestTransaction
                                    )
                                    releaseSettling = false
                                }
                            }
                        }

                        if (!cancelled) currentTarget(target)
                        scope.launch {
                            press.animateTo(0f, PressSpring)
                            if (gestureGeneration == generation) pressOnSelector = false
                        }
                    }
                }
            )
        }
    }
}

private const val EdgeProgressOffset = 0.12f

private fun edgeStretchProfile(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return 4f * t * (1f - t)
}

private fun leadingEdgeProgress(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    val offset = EdgeProgressOffset * edgeStretchProfile(t)
    return (t + offset).coerceIn(0f, 1f)
}

private fun trailingEdgeProgress(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    val offset = EdgeProgressOffset * edgeStretchProfile(t)
    return (t - offset).coerceIn(0f, 1f)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun dragSelectorEdges(
    position: Float,
    trailPx: Float,
    travelItems: Float,
    startLeftPx: Float,
    startRightPx: Float,
    itemWidthPx: Float,
    paddingPx: Float,
    selectorWidthPx: Float,
    minLeftPx: Float,
    maxRightPx: Float
): Pair<Float, Float> {
    val boundedPosition = position.coerceIn(0f, (ItemCount - 1).toFloat())
    val width = lerpFloat(
        startRightPx - startLeftPx,
        selectorWidthPx,
        (travelItems / 0.35f).coerceIn(0f, 1f)
    )
    val overscrollPx = abs(position - boundedPosition) * itemWidthPx
    val edgeDistancePx = minOf(boundedPosition, (ItemCount - 1).toFloat() - boundedPosition) * itemWidthPx
    val trail = abs(trailPx) * (edgeDistancePx / (itemWidthPx * 0.18f)).coerceIn(0f, 1f)
    // At the rim, only resisted overscroll changes the width. A lagging tail
    // here would enlarge the pill while it is pinned and delay its reversal.
    if (position < 0f) {
        return Pair(minLeftPx, minLeftPx + width + overscrollPx)
    }
    if (position > (ItemCount - 1).toFloat()) {
        return Pair(maxRightPx - width - overscrollPx, maxRightPx)
    }
    val center = paddingPx + (boundedPosition + 0.5f) * itemWidthPx
    val left = center - width / 2f - if (trailPx > 0f) trail else 0f
    val right = center + width / 2f + if (trailPx < 0f) trail else 0f
    return Pair(left.coerceAtLeast(minLeftPx), right.coerceAtMost(maxRightPx))
}

private fun rubberBand(value: Float, min: Float, max: Float): Float = when {
    value < min -> min - (min - value).coerceAtMost(1f) * EdgeOverscrollItems /
        (EdgeOverscrollItems + (min - value).coerceAtMost(1f))
    value > max -> max + (value - max).coerceAtMost(1f) * EdgeOverscrollItems /
        (EdgeOverscrollItems + (value - max).coerceAtMost(1f))
    else -> value
}

@Composable
private fun NavigationRow(
    visualPosition: Float,
    semanticsIndex: Int,
    mutedColor: Color,
    selectedColor: Color,
    modifier: Modifier
) {
    Row(modifier) {
        NavigationItem("概觀", MiuixIcons.Home, 0, visualPosition, semanticsIndex, mutedColor, selectedColor)
        NavigationItem("日誌", Icons.Default.List, 1, visualPosition, semanticsIndex, mutedColor, selectedColor)
        NavigationItem("設定", MiuixIcons.Settings, 2, visualPosition, semanticsIndex, mutedColor, selectedColor)
    }
}

@Composable
private fun RowScope.NavigationItem(
    label: String,
    icon: ImageVector,
    index: Int,
    visualPosition: Float,
    semanticsIndex: Int,
    mutedColor: Color,
    selectedColor: Color
) {
    val selectedFraction = (1f - abs(visualPosition - index)).coerceIn(0f, 1f)
    val contentColor = lerp(mutedColor, selectedColor, selectedFraction)
    val itemScale = 0.985f + 0.015f * selectedFraction
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                selected = semanticsIndex == index
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            },
            tint = contentColor
        )
        Text(
            text = label,
            modifier = Modifier.graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            },
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
