package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ItemCount = 3
private const val ItemWidthDp = 80f
private const val HorizontalPaddingDp = 4f
private const val BarWidthDp = 248f
private const val BarHeightDp = 62f
private const val SelectorHeightDp = 54f
private const val EdgeOverscrollItems = 0.075f
private const val MaxEdgeStretchDp = 24f

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

@Composable
internal fun SukiFloatingBottomBar(
    currentIndex: Int,
    targetIndex: Int,
    transactionId: Int,
    pagePosition: Float,
    onTargetSelected: (Int) -> Unit,
    backdrop: Backdrop?
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
    val releaseSettle = remember { Animatable(safePagePosition, 0.001f) }

    // Preserve beta.20's glass ownership: page -> container -> selector -> glyphs.
    val containerBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, containerBackdrop) }

    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(safePagePosition) }
    var releaseHoldActive by remember { mutableStateOf(false) }
    var releaseSettling by remember { mutableStateOf(false) }
    var releaseHoldPosition by remember { mutableFloatStateOf(safePagePosition) }
    var releasePagerStart by remember { mutableFloatStateOf(safePagePosition) }
    var releaseTarget by remember { mutableIntStateOf(safeTarget) }
    var gestureGeneration by remember { mutableIntStateOf(0) }
    var releaseSettleJob by remember { mutableStateOf<Job?>(null) }
    var pressOnSelector by remember { mutableStateOf(false) }
    var touchX by remember { mutableFloatStateOf((safeCurrent + 0.5f) * ItemWidthDp) }

    val visualPosition = when {
        dragging -> dragPosition
        releaseSettling -> releaseSettle.value
        releaseHoldActive -> releaseHoldPosition
        else -> safePagePosition
    }
    val latestVisualPosition by rememberUpdatedState(visualPosition)

    // Preserve beta.21's release-hold ownership exactly. This frame loop no
    // longer drives capsule deformation; it only hands visual ownership back
    // to Pager when Pager reaches the held drag position.
    LaunchedEffect(releaseHoldActive, releaseSettling) {
        while (releaseHoldActive && !releaseSettling) {
            withFrameNanos {
                val pager = latestPagePosition
                val destination = releaseTarget.toFloat()
                val minTravel = min(releasePagerStart, destination)
                val maxTravel = max(releasePagerStart, destination)
                val holdIsOnPagerPath = releaseHoldPosition in minTravel..maxTravel
                val reachedHold = when {
                    destination > releasePagerStart -> pager >= releaseHoldPosition
                    destination < releasePagerStart -> pager <= releaseHoldPosition
                    else -> false
                }
                if (holdIsOnPagerPath && reachedHold) releaseHoldActive = false
            }
        }
    }

    val containerColor = if (dark) Color(0xFF141417).copy(alpha = 0.34f)
        else Color.White.copy(alpha = 0.34f)
    val fallbackColor = if (dark) Color(0xE6222226) else Color(0xEAF5F5F7)
    val edgeColor = if (dark) Color.White.copy(alpha = 0.24f)
        else Color.White.copy(alpha = 0.72f)
    val textColor = if (dark) Color.White else Color(0xFF151518)
    val mutedColor = textColor.copy(alpha = 0.58f)
    val itemWidthPx = with(density) { ItemWidthDp.dp.toPx() }
    val paddingPx = with(density) { HorizontalPaddingDp.dp.toPx() }
    val selectorHeightPx = with(density) { SelectorHeightDp.dp.toPx() }
    val selectorPress = if (pressOnSelector) press.value else 0f
    val maxEdgeStretchPx = with(density) { MaxEdgeStretchDp.dp.toPx() }

    // Navigation geometry is now a true two-edge morph. Pager still owns the
    // timeline, but the leading and trailing physical edges consume that same
    // progress with different monotonic curves.
    val initialMotionLeftPx = paddingPx + safePagePosition * itemWidthPx
    var motionTransactionId by remember { mutableIntStateOf(transactionId) }
    var motionTargetIndex by remember { mutableIntStateOf(safeTarget) }
    var motionStartPosition by remember { mutableFloatStateOf(safePagePosition) }
    var motionStartLeftPx by remember { mutableFloatStateOf(initialMotionLeftPx) }
    var motionStartRightPx by remember {
        mutableFloatStateOf(initialMotionLeftPx + itemWidthPx)
    }

    val motionTargetPosition = motionTargetIndex.toFloat()
    val motionTargetLeftPx = paddingPx + motionTargetPosition * itemWidthPx
    val motionTargetRightPx = motionTargetLeftPx + itemWidthPx
    val motionDistance = motionTargetPosition - motionStartPosition
    val motionProgress = if (abs(motionDistance) < 0.0001f) {
        1f
    } else {
        ((safePagePosition - motionStartPosition) / motionDistance).coerceIn(0f, 1f)
    }
    val leadingProgress = leadingEdgeProgress(motionProgress)
    val trailingProgress = trailingEdgeProgress(motionProgress)

    var motionLeftPx = when {
        motionDistance > 0f ->
            lerpFloat(motionStartLeftPx, motionTargetLeftPx, trailingProgress)
        motionDistance < 0f ->
            lerpFloat(motionStartLeftPx, motionTargetLeftPx, leadingProgress)
        else -> motionTargetLeftPx
    }
    var motionRightPx = when {
        motionDistance > 0f ->
            lerpFloat(motionStartRightPx, motionTargetRightPx, leadingProgress)
        motionDistance < 0f ->
            lerpFloat(motionStartRightPx, motionTargetRightPx, trailingProgress)
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

    // Drag/release ownership is left untouched from beta.21. While a gesture
    // owns the visual position, use the same fixed-width geometry beta.21 had;
    // the new edge morph only replaces normal Pager-driven navigation motion.
    val gestureOwnsGeometry = dragging || releaseSettling || releaseHoldActive
    val selectorMotionLeftPx = if (gestureOwnsGeometry) {
        paddingPx + visualPosition * itemWidthPx
    } else {
        motionLeftPx
    }
    val selectorMotionRightPx = if (gestureOwnsGeometry) {
        selectorMotionLeftPx + itemWidthPx
    } else {
        motionRightPx
    }
    val latestSelectorMotionLeftPx by rememberUpdatedState(selectorMotionLeftPx)
    val latestSelectorMotionRightPx by rememberUpdatedState(selectorMotionRightPx)

    // On rapid retarget, capture the capsule exactly as currently rendered.
    // The new transaction starts from those two real edges, so there is no
    // reset-to-normal-width frame before reversing direction.
    LaunchedEffect(transactionId, safeTarget) {
        if (motionTransactionId != transactionId || motionTargetIndex != safeTarget) {
            motionStartPosition = latestPagePosition
            motionStartLeftPx = latestSelectorMotionLeftPx
            motionStartRightPx = latestSelectorMotionRightPx
            motionTargetIndex = safeTarget
            motionTransactionId = transactionId
        }
    }

    val containerEffects: BackdropEffectScope.() -> Unit = remember {
        { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 20.dp.toPx()) }
    }
    val containerSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }
    val selectorEffects: BackdropEffectScope.() -> Unit = remember {
        {
            val p = if (pressOnSelector) press.value else 0f
            lens(
                refractionHeight = 10.dp.toPx() + 8.dp.toPx() * p,
                refractionAmount = 10.dp.toPx() + 10.dp.toPx() * p,
                chromaticAberration = false
            )
        }
    }
    val selectorSurface: DrawScope.() -> Unit = remember(dark) {
        {
            val p = if (pressOnSelector) press.value else 0f
            drawRect(
                if (dark) Color.White.copy(alpha = 0.08f - 0.03f * p)
                else Color.Black.copy(alpha = 0.055f - 0.025f * p)
            )
        }
    }

    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val glassModifier = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = containerEffects,
                exportedBackdrop = containerBackdrop,
                highlight = { Highlight.Default.copy(alpha = 0.25f + 0.35f * press.value) },
                onDrawSurface = containerSurface
            )
        } else {
            Modifier.background(fallbackColor, CircleShape)
        }

        Box(
            Modifier
                .width(BarWidthDp.dp)
                .height(BarHeightDp.dp)
                .then(glassModifier)
                .border(0.55.dp, edgeColor, CircleShape)
                .clip(CircleShape)
                .drawWithContent {
                    drawContent()
                    val p = press.value
                    if (p > 0f) {
                        val center = Offset(touchX.dp.toPx(), size.height / 2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f * p),
                                    Color.White.copy(alpha = 0.045f * p),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.width * 0.56f
                            ),
                            center = center,
                            radius = size.width * 0.56f,
                            blendMode = BlendMode.Plus
                        )
                    }
                }
        ) {
            val pressExpansion = with(density) { (1.4f * selectorPress).dp.toPx() }
            val selectorLeft = selectorMotionLeftPx - pressExpansion
            val selectorRight = selectorMotionRightPx + pressExpansion
            val dynamicHeight = selectorHeightPx + pressExpansion * 1.4f
            val selectorTop = with(density) { BarHeightDp.dp.toPx() } / 2f - dynamicHeight / 2f

            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        if (combinedBackdrop != null) {
                            Modifier.fillMaxSize().drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { CircleShape },
                                downsampleScale = 1f,
                                effects = selectorEffects,
                                highlight = { Highlight.Default.copy(alpha = 0.18f + 0.72f * selectorPress) },
                                shadow = { Shadow(alpha = 0.12f + 0.28f * selectorPress) },
                                innerShadow = {
                                    InnerShadow(
                                        radius = 6.dp + 3.dp * selectorPress,
                                        alpha = 0.18f + 0.42f * selectorPress
                                    )
                                },
                                onDrawSurface = selectorSurface
                            )
                        } else {
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (dark) Color(0x663F3F45) else Color(0x66FFFFFF),
                                    CircleShape
                                )
                                .border(0.55.dp, edgeColor, CircleShape)
                        }
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
                        releaseSettleJob?.cancel()
                        releaseSettleJob = null
                        releaseHoldActive = false
                        releaseSettling = false
                        dragging = false
                        touchX = with(density) { down.position.x.toDp().value }

                        val downIndex = floor(
                            (down.position.x - paddingPx) / itemWidthPx
                        ).toInt().coerceIn(0, ItemCount - 1)
                        val selectorCenter = paddingPx + (latestVisualPosition + 0.5f) * itemWidthPx
                        val startedOnSelector =
                            abs(down.position.x - selectorCenter) <= itemWidthPx * 0.55f
                        pressOnSelector = startedOnSelector
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
                            touchX = with(density) { change.position.x.toDp().value }
                            val totalDx = change.position.x - down.position.x
                            if (
                                startedOnSelector &&
                                !dragging &&
                                abs(totalDx) > viewConfiguration.touchSlop
                            ) {
                                dragging = true
                                dragPosition = latestVisualPosition
                                lastX = change.position.x
                            }
                            if (dragging) {
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                dragPosition = rubberBand(
                                    dragPosition + dx / itemWidthPx,
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
                        dragging = false

                        if (wasDragging && !cancelled) {
                            releaseHoldPosition = heldPosition
                            releasePagerStart = latestPagePosition
                            releaseTarget = target
                            releaseHoldActive = true

                            val destination = target.toFloat()
                            val holdOnPath = heldPosition in
                                min(releasePagerStart, destination)..max(releasePagerStart, destination)
                            if (!holdOnPath || abs(destination - releasePagerStart) < 0.001f) {
                                releaseSettleJob = scope.launch {
                                    releaseSettle.snapTo(heldPosition)
                                    if (gestureGeneration != generation) return@launch
                                    releaseSettling = true
                                    releaseSettle.animateTo(destination, GestureSettleSpring)
                                    if (gestureGeneration == generation) {
                                        releaseSettling = false
                                        releaseHoldActive = false
                                    }
                                }
                            }
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

private fun leadingEdgeProgress(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    val inverse = 1f - t
    return 1f - inverse * inverse
}

private fun trailingEdgeProgress(value: Float): Float {
    val t = ((value - 0.08f) / 0.92f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
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
