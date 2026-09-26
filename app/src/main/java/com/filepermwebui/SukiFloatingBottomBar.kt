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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private const val ItemCount = 3
private const val ItemWidthDp = 80f
private const val HorizontalPaddingDp = 4f
private const val BarWidthDp = 248f
private const val BarHeightDp = 62f
private const val SelectorHeightDp = 54f
private const val EdgeOverscrollItems = 0.075f
private const val MaxEdgeStretchDp = 24f
private const val DragTrailScale = 4f

private val PressSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 650f,
    visibilityThreshold = 0.001f
)
private val GestureSettleSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 680f,
    visibilityThreshold = 0.001f
)
private val DragFollowSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 1100f,
    visibilityThreshold = 0.01f
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
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val safeCurrent = currentIndex.coerceIn(0, ItemCount - 1)
    val safeTarget = targetIndex.coerceIn(0, ItemCount - 1)
    val safePagePosition = pagePosition.coerceIn(0f, (ItemCount - 1).toFloat())
    val currentTarget by rememberUpdatedState(onTargetSelected)
    val latestPagePosition by rememberUpdatedState(safePagePosition)
    val latestTarget by rememberUpdatedState(safeTarget)
    val press = remember { Animatable(0f, 0.001f) }

    var dragging by remember { mutableStateOf(false) }
    var pressPreviewActive by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(safePagePosition) }
    var releaseSettling by remember { mutableStateOf(false) }
    var gestureGeneration by remember { mutableIntStateOf(0) }
    var releaseSettleJob by remember { mutableStateOf<Job?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var gestureStartLeftPx by remember { mutableFloatStateOf(0f) }
    var gestureStartRightPx by remember { mutableFloatStateOf(0f) }
    var pressOnSelector by remember { mutableStateOf(false) }
    val containerColor = if (dark) Color(0xFF101012) else Color(0xFFF1F1F3)
    val borderColor = if (dark) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.10f)
    val selectorColor = if (dark) Color(0xFF38383B) else Color(0xFFDADADD)
    val textColor = if (dark) Color.White else Color(0xFF151518)
    val mutedColor = textColor.copy(alpha = 0.58f)
    val itemWidthPx = with(density) { ItemWidthDp.dp.toPx() }
    val paddingPx = with(density) { HorizontalPaddingDp.dp.toPx() }
    val dragLeft = remember(itemWidthPx, paddingPx) { Animatable(paddingPx + safePagePosition * itemWidthPx, 0.01f) }
    val dragRight = remember(itemWidthPx, paddingPx) { Animatable(paddingPx + (safePagePosition + 1f) * itemWidthPx, 0.01f) }
    var dragLeftJob by remember { mutableStateOf<Job?>(null) }
    var dragRightJob by remember { mutableStateOf<Job?>(null) }
    val releaseLeft = remember { Animatable(paddingPx + safePagePosition * itemWidthPx, 0.01f) }
    val releaseRight = remember { Animatable(paddingPx + (safePagePosition + 1f) * itemWidthPx, 0.01f) }
    val selectorHeightPx = with(density) { SelectorHeightDp.dp.toPx() }
    val selectorPress = if (pressOnSelector) press.value else 0f
    val maxEdgeStretchPx = with(density) { MaxEdgeStretchDp.dp.toPx() }

    // Navigation geometry is now a true two-edge morph. Pager still owns the
    // timeline, but the leading and trailing physical edges consume that same
    // progress with different monotonic curves.
    val initialMotionLeftPx = paddingPx + safePagePosition * itemWidthPx
    var motionSegment by remember {
        mutableStateOf(
            SelectorMotionSegment(
                startPagerPosition = safePagePosition,
                startLeftPx = initialMotionLeftPx,
                startRightPx = initialMotionLeftPx + itemWidthPx,
                startVisualPosition = safePagePosition,
                targetIndex = safeTarget,
                transactionId = transactionId
            )
        )
    }

    val motionTargetPosition = motionSegment.targetIndex.toFloat()
    val motionTargetLeftPx = paddingPx + motionTargetPosition * itemWidthPx
    val motionTargetRightPx = motionTargetLeftPx + itemWidthPx
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
    val maxMotionWidthPx = itemWidthPx + maxEdgeStretchPx
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
    val gestureStartVisualPosition =
        ((gestureStartLeftPx + gestureStartRightPx) / 2f - paddingPx) / itemWidthPx - 0.5f
    val selectorMotionLeftPx = when {
        dragging || pressPreviewActive -> dragLeft.value
        releaseSettling -> releaseLeft.value
        gestureActive -> gestureStartLeftPx
        else -> motionLeftPx
    }
    val selectorMotionRightPx = when {
        dragging || pressPreviewActive -> dragRight.value
        releaseSettling -> releaseRight.value
        gestureActive -> gestureStartRightPx
        else -> motionRightPx
    }
    val motionVisualPosition = lerpFloat(
        motionSegment.startVisualPosition,
        motionTargetPosition,
        motionProgress
    )
    val visualPosition = when {
        dragging || pressPreviewActive ->
            ((dragLeft.value + dragRight.value) / 2f - paddingPx) / itemWidthPx - 0.5f
        releaseSettling ->
            ((releaseLeft.value + releaseRight.value) / 2f - paddingPx) / itemWidthPx - 0.5f
        gestureActive -> gestureStartVisualPosition
        else -> motionVisualPosition
    }
    val latestSelectorMotionLeftPx by rememberUpdatedState(selectorMotionLeftPx)
    val latestSelectorMotionRightPx by rememberUpdatedState(selectorMotionRightPx)
    val latestVisualPosition by rememberUpdatedState(visualPosition)

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
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
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
            val pressInset = with(density) { (3f * selectorPress).dp.toPx() }
            val selectorLeft = selectorMotionLeftPx + pressInset
            val selectorRight = selectorMotionRightPx - pressInset
            val dynamicHeight = selectorHeightPx - 2f * pressInset
            val selectorTop = with(density) { BarHeightDp.dp.toPx() } / 2f - dynamicHeight / 2f

            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(selectorColor, CircleShape)
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
                textColor = textColor,
                mutedColor = mutedColor,
                modifier = Modifier.fillMaxSize().padding(horizontal = HorizontalPaddingDp.dp)
            )

            // Normal tap commits only on release. Drag keeps beta.19's single
            // gesture owner, touchSlop, rubber band and target calculation.
            Box(
                Modifier.fillMaxSize().pointerInput(
                    itemWidthPx,
                    paddingPx,
                    viewConfiguration.touchSlop
                ) {
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
                        dragLeftJob?.cancel()
                        dragRightJob?.cancel()
                        dragLeft.snapTo(gestureStartLeftPx)
                        dragRight.snapTo(gestureStartRightPx)
                        releaseSettling = false
                        dragging = false
                        pressPreviewActive = false
                        val downIndex = floor(
                            (down.position.x - paddingPx) / itemWidthPx
                        ).toInt().coerceIn(0, ItemCount - 1)
                        val selectorCenter = paddingPx + (latestVisualPosition + 0.5f) * itemWidthPx
                        val startedOnSelector =
                            abs(down.position.x - selectorCenter) <= itemWidthPx * 0.55f
                        pressPreviewActive = !startedOnSelector && downIndex != latestTarget
                        if (pressPreviewActive) {
                            val previewLeft = paddingPx + downIndex * itemWidthPx
                            dragLeftJob = scope.launch { dragLeft.animateTo(previewLeft, GestureSettleSpring) }
                            dragRightJob = scope.launch { dragRight.animateTo(previewLeft + itemWidthPx, GestureSettleSpring) }
                        }
                        pressOnSelector = startedOnSelector || pressPreviewActive
                        var lastX = down.position.x
                        var cancelled = false

                        scope.launch { press.animateTo(1f, PressSpring) }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            val totalDx = change.position.x - down.position.x
                            if (
                                startedOnSelector &&
                                !dragging &&
                                abs(totalDx) > viewConfiguration.touchSlop
                            ) {
                                dragging = true
                                dragPosition = startVisualPosition
                                lastX = down.position.x + viewConfiguration.touchSlop * sign(totalDx)
                            }
                            if (dragging) {
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                dragPosition = rubberBand(
                                    dragPosition + dx / itemWidthPx,
                                    0f,
                                    (ItemCount - 1).toFloat()
                                )
                                val centerPx = paddingPx + (dragPosition + 0.5f) * itemWidthPx
                                val trailPx = (abs(dx) * DragTrailScale).coerceAtMost(maxEdgeStretchPx)
                                val targetLeft = centerPx - itemWidthPx / 2f - if (dx > 0f) trailPx else 0f
                                val targetRight = centerPx + itemWidthPx / 2f + if (dx < 0f) trailPx else 0f
                                dragLeftJob?.cancel()
                                dragRightJob?.cancel()
                                dragLeftJob = scope.launch { dragLeft.animateTo(targetLeft, DragFollowSpring) }
                                dragRightJob = scope.launch { dragRight.animateTo(targetRight, DragFollowSpring) }
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
                        dragLeftJob?.cancel()
                        dragRightJob?.cancel()
                        val heldLeftPx = if (wasDragging || pressPreviewActive) dragLeft.value else gestureStartLeftPx
                        val heldRightPx = if (wasDragging || pressPreviewActive) dragRight.value else gestureStartRightPx
                        if (wasDragging) {
                            // Keep the exact release geometry visible until
                            // either Pager or the same-page settle takes over.
                            gestureStartLeftPx = heldLeftPx
                            gestureStartRightPx = heldRightPx
                        }
                        dragging = false
                        pressPreviewActive = false

                        val targetLeftPx = paddingPx + target * itemWidthPx
                        val pagerAlreadyAtTarget = abs(latestPagePosition - target.toFloat()) < 0.001f
                        if (!cancelled && pagerAlreadyAtTarget &&
                            (abs(heldLeftPx - targetLeftPx) > 0.01f ||
                                abs(heldRightPx - targetLeftPx - itemWidthPx) > 0.01f)
                        ) {
                            // The Pager cannot supply progress if it is already
                            // at the destination. Settle both actual edges instead.
                            releaseSettleJob = scope.launch {
                                releaseLeft.snapTo(heldLeftPx)
                                releaseRight.snapTo(heldRightPx)
                                if (gestureGeneration != generation) return@launch
                                releaseSettling = true
                                gestureActive = false
                                coroutineScope {
                                    launch { releaseLeft.animateTo(targetLeftPx, GestureSettleSpring) }
                                    launch { releaseRight.animateTo(targetLeftPx + itemWidthPx, GestureSettleSpring) }
                                }
                                if (gestureGeneration == generation) {
                                    motionSegment = SelectorMotionSegment(
                                        startPagerPosition = latestPagePosition,
                                        startLeftPx = targetLeftPx,
                                        startRightPx = targetLeftPx + itemWidthPx,
                                        startVisualPosition = target.toFloat(),
                                        targetIndex = target,
                                        transactionId = transactionId
                                    )
                                    releaseSettling = false
                                }
                            }
                        } else if (wasDragging && !cancelled) {
                            // Cross-page release continues from the actual drag shape.
                            motionSegment = SelectorMotionSegment(
                                startPagerPosition = latestPagePosition,
                                startLeftPx = heldLeftPx,
                                startRightPx = heldRightPx,
                                startVisualPosition =
                                    ((heldLeftPx + heldRightPx) / 2f - paddingPx) / itemWidthPx - 0.5f,
                                targetIndex = target,
                                transactionId = transactionId + 1
                            )
                            gestureActive = false
                        } else {
                            // Establish the new segment before Pager geometry
                            // is exposed. Same-target recovery uses this path.
                            motionSegment = SelectorMotionSegment(
                                startPagerPosition = latestPagePosition,
                                startLeftPx = heldLeftPx,
                                startRightPx = heldRightPx,
                                startVisualPosition =
                                    ((heldLeftPx + heldRightPx) / 2f - paddingPx) / itemWidthPx - 0.5f,
                                targetIndex = target,
                                transactionId = if (
                                    safeCurrent == target &&
                                    abs(latestPagePosition - target.toFloat()) < 0.001f
                                ) transactionId else transactionId + 1
                            )
                            gestureActive = false
                        }

                        currentTarget(target)
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
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier
) {
    Row(modifier) {
        NavigationItem("概觀", MiuixIcons.Home, 0, visualPosition, semanticsIndex, textColor, mutedColor)
        NavigationItem("日誌", Icons.Default.List, 1, visualPosition, semanticsIndex, textColor, mutedColor)
        NavigationItem("設定", MiuixIcons.Settings, 2, visualPosition, semanticsIndex, textColor, mutedColor)
    }
}

@Composable
private fun RowScope.NavigationItem(
    label: String,
    icon: ImageVector,
    index: Int,
    visualPosition: Float,
    semanticsIndex: Int,
    textColor: Color,
    mutedColor: Color
) {
    val selectedFraction = (1f - abs(visualPosition - index)).coerceIn(0f, 1f)
    val contentColor = lerp(mutedColor, textColor, selectedFraction)
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
                alpha = 0.86f + 0.14f * selectedFraction
            },
            tint = contentColor
        )
        Text(
            text = label,
            modifier = Modifier.graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
                alpha = 0.86f + 0.14f * selectedFraction
            },
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
