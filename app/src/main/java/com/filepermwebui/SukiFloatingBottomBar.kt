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
import kotlinx.coroutines.flow.collect
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

private const val ItemCount = 3
private const val BarWidthDp = 240f
private const val BarHeightDp = 52f
private const val HorizontalPaddingDp = 7f
private const val ItemWidthDp = (BarWidthDp - 2f * HorizontalPaddingDp) / ItemCount
private const val SelectorHeightDp = 45f
private const val SelectorWidthExtraDp = 7f
private val PressSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 650f)

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
    val latestTarget by rememberUpdatedState(safeTarget)
    val press = remember { Animatable(0f, 0.001f) }

    val physics = remember { BottomBarPhysics(safePagePosition) }
    var frameRevision by remember { mutableIntStateOf(0) }
    var gestureActive by remember { mutableStateOf(false) }
    var pressOnSelector by remember { mutableStateOf(false) }
    var pressJob by remember { mutableStateOf<Job?>(null) }
    val containerColor = if (dark) Color(0xFF171719) else Color(0xFFF1F1F3)
    val borderColor = if (dark) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.10f)
    val selectorColor = if (dark) Color(0xFF4B4B4E) else Color(0xFFDADADD)
    val textColor = if (dark) Color.White else Color(0xFF151518)
    val mutedColor = textColor.copy(alpha = if (dark) 0.94f else 0.86f)
    val selectedColor = textColor
    val itemWidthPx = with(density) { ItemWidthDp.dp.toPx() }
    val paddingPx = with(density) { HorizontalPaddingDp.dp.toPx() }
    val selectorExtraPx = with(density) { SelectorWidthExtraDp.dp.toPx() }
    val selectorInsetPx = selectorExtraPx / 2f
    val selectorMinLeftPx = paddingPx - selectorInsetPx
    val selectorMaxRightPx = paddingPx + ItemCount * itemWidthPx + selectorInsetPx
    val selectorHeightPx = with(density) { SelectorHeightDp.dp.toPx() }
    val selectorPress = if (pressOnSelector) press.value else 0f
    // One continuously evolving pair of edges owns taps, drags and release.
    // Reading the revision publishes the plain controller's frame snapshot.
    @Suppress("UNUSED_VARIABLE") val revision = frameRevision
    val selectorLeft = (selectorMinLeftPx + physics.leftPosition * itemWidthPx)
        .coerceIn(selectorMinLeftPx, selectorMaxRightPx)
    val selectorRight = (selectorMinLeftPx + physics.rightPosition * itemWidthPx + selectorExtraPx)
        .coerceIn(selectorLeft + 1f, selectorMaxRightPx)
    val visualPosition = physics.position.coerceIn(0f, (ItemCount - 1).toFloat())
    val dynamicHeight = selectorHeightPx
    val selectorTop = with(density) { BarHeightDp.dp.toPx() } / 2f - dynamicHeight / 2f
    val latestVisibleLeftPx by rememberUpdatedState(selectorLeft)
    val latestVisibleRightPx by rememberUpdatedState(selectorRight)
    val latestVisibleTopPx by rememberUpdatedState(selectorTop)
    val latestVisibleBottomPx by rememberUpdatedState(selectorTop + dynamicHeight)

    LaunchedEffect(transactionId, safeTarget) {
        if (!gestureActive) {
            physics.select(safeTarget)
            frameRevision++
        }
    }
    LaunchedEffect(physics) {
        androidx.compose.runtime.snapshotFlow { frameRevision }
            .collect {
                var previous = withFrameNanos { it }
                while (physics.active || physics.dragging) {
                    val now = withFrameNanos { it }
                    physics.advance((now - previous) / 1_000_000_000f)
                    previous = now
                    frameRevision++
                }
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
                        val startedOnSelector =
                            down.position.x in latestVisibleLeftPx..latestVisibleRightPx &&
                            down.position.y in latestVisibleTopPx..latestVisibleBottomPx
                        val downIndex = floor((down.position.x - paddingPx) / itemWidthPx)
                            .toInt().coerceIn(0, ItemCount - 1)
                        gestureActive = true
                        pressOnSelector = startedOnSelector
                        if (startedOnSelector) physics.beginDrag()
                        frameRevision++
                        pressJob?.cancel()
                        pressJob = scope.launch { press.animateTo(1f, PressSpring) }
                        var cancelled = true
                        var previousX = down.position.x
                        var previousTime = down.uptimeMillis
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                if (startedOnSelector) {
                                    physics.dragBy(
                                        (change.position.x - previousX) / itemWidthPx,
                                        (change.uptimeMillis - previousTime) / 1000f
                                    )
                                    frameRevision++
                                    change.consume()
                                }
                                previousX = change.position.x
                                previousTime = change.uptimeMillis
                                if (!change.pressed) {
                                    cancelled = false
                                    break
                                }
                            }
                        } finally {
                            val target = when {
                                cancelled -> latestTarget
                                startedOnSelector -> physics.dragPosition.roundToInt()
                                else -> downIndex
                            }.coerceIn(0, ItemCount - 1)
                            physics.select(target)
                            gestureActive = false
                            frameRevision++
                            // Page and capsule receive the same committed target
                            // on release; drag never navigates prematurely.
                            if (!cancelled) currentTarget(target)
                            pressJob?.cancel()
                            pressJob = scope.launch {
                                press.animateTo(0f, PressSpring)
                                pressOnSelector = false
                            }
                        }
                    }
                }
            )
        }
    }
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
