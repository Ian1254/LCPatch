package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ItemCount = 3
private const val ItemWidthDp = 80f
private const val HorizontalPaddingDp = 4f
private const val BarWidthDp = 248f
private const val BarHeightDp = 62f
private const val IndicatorInsetDp = 4f

@Composable
internal fun SukiFloatingBottomBar(
    currentIndex: Int,
    targetIndex: Int,
    transactionId: Int,
    onTargetSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val currentTarget by rememberUpdatedState(onTargetSelected)
    val safeCurrent = currentIndex.coerceIn(0, ItemCount - 1)
    val safeTarget = targetIndex.coerceIn(0, ItemCount - 1)
    val itemWidthPx = ItemWidthDp * density.density
    fun leftFor(index: Int) = HorizontalPaddingDp + index * ItemWidthDp
    fun rightFor(index: Int) = leftFor(index) + ItemWidthDp

    val leftEdge = remember { Animatable(leftFor(safeCurrent)) }
    val rightEdge = remember { Animatable(rightFor(safeCurrent)) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var previewStretch by remember { mutableFloatStateOf(0f) }
    val pressAmount by animateFloatAsState(
        if (pressedIndex == safeTarget) 1f else 0f,
        spring(dampingRatio = 0.76f, stiffness = 850f),
        label = "navigation-press"
    )

    // A transaction changes the two physical edges once. No selected-state observer replays it.
    LaunchedEffect(transactionId, safeTarget) {
        val destinationLeft = leftFor(safeTarget)
        val destinationRight = rightFor(safeTarget)
        val movingRight = destinationLeft > leftEdge.value
        val leadingSpec = spring<Float>(0.82f, 800f)
        val trailingSpec = spring<Float>(0.9f, Spring.StiffnessMediumLow)
        coroutineScope {
            launch {
                if (movingRight) rightEdge.animateTo(destinationRight, leadingSpec)
                else leftEdge.animateTo(destinationLeft, leadingSpec)
            }
            launch {
                if (movingRight) leftEdge.animateTo(destinationLeft, trailingSpec)
                else rightEdge.animateTo(destinationRight, trailingSpec)
            }
        }
    }

    val pressInset = pressAmount * 3f
    val previewAmount = abs(previewStretch).coerceAtMost(ItemWidthDp * 0.42f)
    val rawLeft = leftEdge.value + pressInset - if (previewStretch < 0f) previewAmount else 0f
    val rawRight = rightEdge.value - pressInset + if (previewStretch > 0f) previewAmount else 0f
    val overlayLeft = rawLeft.coerceIn(HorizontalPaddingDp, BarWidthDp - HorizontalPaddingDp)
    val overlayRight = rawRight.coerceIn(overlayLeft + 1f, BarWidthDp - HorizontalPaddingDp)

    val containerColor = if (dark) Color(0xD91E1E21) else Color(0xEAF4F4F5)
    val indicatorColor = if (dark) Color(0xFF3B3B3F) else Color(0xFFE0E0E3)
    val outlineColor = if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.07f)
    val barModifier = Modifier.width(BarWidthDp.dp).height(BarHeightDp.dp).then(
        if (backdrop != null) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = { blur(34f, 34f) },
            onDrawSurface = { drawRect(containerColor.copy(alpha = 0.80f)) }
        ) else Modifier.background(containerColor, CircleShape)
    ).border(1.dp, outlineColor, CircleShape).clip(CircleShape)

    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(barModifier) {
            Box(
                Modifier.graphicsLayer {
                    translationX = with(density) { overlayLeft.dp.toPx() }
                    translationY = with(density) { IndicatorInsetDp.dp.toPx() }
                }.width((overlayRight - overlayLeft).dp)
                    .height((BarHeightDp - IndicatorInsetDp * 2f).dp)
                    .background(indicatorColor, CircleShape)
            )
            Row(Modifier.fillMaxSize().padding(horizontal = HorizontalPaddingDp.dp)) {
                NavigationItem("概觀", MiuixIcons.Home, safeTarget == 0, pressedIndex == 0, dark)
                NavigationItem("日誌", Icons.Default.List, safeTarget == 1, pressedIndex == 1, dark)
                NavigationItem("設定", MiuixIcons.Settings, safeTarget == 2, pressedIndex == 2, dark)
            }
            Box(Modifier.fillMaxSize().pointerInput(viewConfiguration.touchSlop, density.density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downIndex = ((down.position.x - HorizontalPaddingDp * density.density) / itemWidthPx)
                        .toInt().coerceIn(0, ItemCount - 1)
                    pressedIndex = downIndex
                    previewStretch = 0f
                    val downX = down.position.x
                    val downY = down.position.y
                    var horizontalGesture = false
                    var cancelled = false
                    var releaseX = downX
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) { cancelled = true; break }
                        releaseX = change.position.x
                        val dx = change.position.x - downX
                        val dy = change.position.y - downY
                        if (!horizontalGesture && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) horizontalGesture = true
                        if (!horizontalGesture && abs(dy) > viewConfiguration.touchSlop) cancelled = true
                        if (horizontalGesture) { change.consume(); previewStretch = dx / density.density }
                        if (!change.pressed) break
                    }
                    val releaseIndex = ((releaseX - HorizontalPaddingDp * density.density) / itemWidthPx)
                        .toInt().coerceIn(0, ItemCount - 1)
                    pressedIndex = -1
                    previewStretch = 0f
                    if (!cancelled) currentTarget(if (horizontalGesture) releaseIndex else downIndex)
                }
            })
        }
    }
}

@Composable
private fun RowScope.NavigationItem(label: String, icon: ImageVector, selected: Boolean, pressed: Boolean, dark: Boolean) {
    val selectedAmount by animateFloatAsState(if (selected) 1f else 0f, spring(0.9f, 650f), label = "item-selected")
    val pressAmount by animateFloatAsState(if (pressed) 1f else 0f, spring(0.76f, 900f), label = "item-press")
    val selectedColor = if (dark) Color.White else Color(0xFF171719)
    val unselectedColor = if (dark) Color(0xFF8A8A90) else Color(0xFF737378)
    val color = lerp(unselectedColor, selectedColor, selectedAmount)
    val scale = 0.95f + selectedAmount * 0.05f - pressAmount * 0.04f
    Column(
        Modifier.weight(1f).fillMaxHeight().semantics { role = Role.Tab; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, label, Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale }, tint = color)
        Text(label, color = color, fontSize = 11.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
    }
}
