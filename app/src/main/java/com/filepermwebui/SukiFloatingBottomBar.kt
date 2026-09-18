package com.lcpatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
private const val ItemWidthDp = 80f
private const val HorizontalPaddingDp = 4f
private const val BarWidthDp = 248f
private const val BarHeightDp = 62f
private const val SelectorHeightDp = 54f
private const val EdgeOverscrollItems = 0.075f

private val SelectorSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 720f,
    visibilityThreshold = 0.001f
)
private val PressSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 650f,
    visibilityThreshold = 0.001f
)

@Composable
internal fun SukiFloatingBottomBar(
    currentIndex: Int,
    targetIndex: Int,
    transactionId: Int,
    onTargetSelected: (Int) -> Unit,
    backdrop: Backdrop?
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val safeCurrent = currentIndex.coerceIn(0, ItemCount - 1)
    val safeTarget = targetIndex.coerceIn(0, ItemCount - 1)
    val currentTarget by rememberUpdatedState(onTargetSelected)
    val selector = remember { Animatable(safeCurrent.toFloat(), 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }
    // Export only the container glass (drawBackdrop excludes child content).
    // The selector can therefore refract page + container glass without ever
    // sampling NavigationRow glyphs or itself.
    val containerBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, containerBackdrop) }
    var gestureActive by remember { mutableStateOf(false) }
    var locallyCommittedTarget by remember { mutableIntStateOf(-1) }
    var touchX by remember { mutableFloatStateOf((safeCurrent + 0.5f) * ItemWidthDp) }

    // External page changes use the same selector Animatable. A locally committed gesture
    // is already animating to this target, so its transaction is acknowledged without replay.
    LaunchedEffect(transactionId, safeTarget) {
        if (locallyCommittedTarget == safeTarget) {
            locallyCommittedTarget = -1
        } else if (!gestureActive) {
            selector.animateTo(safeTarget.toFloat(), SelectorSpring)
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

    val containerEffects: BackdropEffectScope.() -> Unit = remember {
        {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), 20.dp.toPx())
        }
    }
    val containerSurface: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }
    val selectorEffects: BackdropEffectScope.() -> Unit = remember {
        {
            val p = press.value
            lens(
                refractionHeight = 10.dp.toPx() + 8.dp.toPx() * p,
                refractionAmount = 10.dp.toPx() + 10.dp.toPx() * p,
                chromaticAberration = p > 0.45f
            )
        }
    }
    val selectorSurface: DrawScope.() -> Unit = remember(dark) {
        {
            val p = press.value
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
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f * p),
                                    Color.White.copy(alpha = 0.045f * p),
                                    Color.Transparent
                                ),
                                center = Offset(touchX.dp.toPx(), size.height / 2f),
                                radius = size.width * 0.56f
                            ),
                            center = Offset(touchX.dp.toPx(), size.height / 2f),
                            radius = size.width * 0.56f,
                            blendMode = BlendMode.Plus
                        )
                    }
                }
        ) {
            val velocityStretch = (abs(selector.velocity) / 22f).coerceIn(0f, 0.065f)
            val pressScale = 1f + 0.035f * press.value
            val selectorModifier = Modifier
                .graphicsLayer {
                    translationX = paddingPx + selector.value * itemWidthPx
                    translationY = with(density) { ((BarHeightDp - SelectorHeightDp) / 2f).dp.toPx() }
                    scaleX = (pressScale + velocityStretch).coerceAtMost(1.105f)
                    scaleY = (pressScale - velocityStretch * 0.38f).coerceAtLeast(0.97f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (selector.velocity >= 0f) 0.38f else 0.62f,
                        pivotFractionY = 0.5f
                    )
                }
                .width(ItemWidthDp.dp)
                .height(SelectorHeightDp.dp)

            Box(
                if (combinedBackdrop != null) {
                    selectorModifier.drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { CircleShape },
                        downsampleScale = 1f,
                        effects = selectorEffects,
                        highlight = { Highlight.Default.copy(alpha = 0.18f + 0.72f * press.value) },
                        shadow = { Shadow(alpha = 0.12f + 0.28f * press.value) },
                        innerShadow = {
                            InnerShadow(
                                radius = 6.dp + 3.dp * press.value,
                                alpha = 0.18f + 0.42f * press.value
                            )
                        },
                        onDrawSurface = selectorSurface
                    )
                } else {
                    selectorModifier
                        .background(
                            if (dark) Color(0x663F3F45) else Color(0x66FFFFFF),
                            CircleShape
                        )
                        .border(0.55.dp, edgeColor, CircleShape)
                }
            )

            // Foreground glyphs are drawn after the refractive selector so Lens and
            // chromatic aberration never distort icon or label readability.
            NavigationRow(
                selectedIndex = safeTarget,
                textColor = textColor,
                mutedColor = mutedColor,
                modifier = Modifier.fillMaxSize().padding(horizontal = HorizontalPaddingDp.dp)
            )

            // One gesture owner for press, click-to-travel, drag takeover, release and snap.
            Box(
                Modifier.fillMaxSize().pointerInput(itemWidthPx, paddingPx, viewConfiguration.touchSlop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        gestureActive = true
                        touchX = with(density) { down.position.x.toDp().value }
                        val downIndex = floor(
                            (down.position.x - paddingPx) / itemWidthPx
                        ).toInt().coerceIn(0, ItemCount - 1)
                        val selectorCenter = paddingPx + (selector.value + 0.5f) * itemWidthPx
                        val startedOnSelector =
                            abs(down.position.x - selectorCenter) <= itemWidthPx * 0.55f
                        var dragging = false
                        var dragPosition = selector.value
                        var lastX = down.position.x
                        var cancelled = false
                        var selectorTravelJob: kotlinx.coroutines.Job? = null
                        var dragMutationJob: kotlinx.coroutines.Job? = null

                        scope.launch { press.animateTo(1f, PressSpring) }
                        if (!startedOnSelector) {
                            selectorTravelJob = scope.launch {
                                selector.animateTo(downIndex.toFloat(), SelectorSpring)
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            touchX = with(density) { change.position.x.toDp().value }
                            val totalDx = change.position.x - down.position.x
                            if (!dragging && abs(totalDx) > viewConfiguration.touchSlop) {
                                dragging = true
                                selectorTravelJob?.cancel()
                                dragMutationJob?.cancel()
                                dragPosition = selector.value
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
                                dragMutationJob?.cancel()
                                val position = dragPosition
                                dragMutationJob = scope.launch {
                                    // A new Animatable mutation takes ownership from any
                                    // click-to-travel animation before following the pointer.
                                    selector.snapTo(position)
                                }
                                change.consume()
                            }
                            if (!change.pressed) break
                        }

                        selectorTravelJob?.cancel()
                        dragMutationJob?.cancel()
                        val target = when {
                            cancelled -> safeTarget
                            dragging -> dragPosition.roundToInt()
                            else -> downIndex
                        }.coerceIn(0, ItemCount - 1)
                        gestureActive = false
                        locallyCommittedTarget = target
                        currentTarget(target)
                        scope.launch { selector.animateTo(target.toFloat(), SelectorSpring) }
                        scope.launch { press.animateTo(0f, PressSpring) }
                    }
                }
            )
        }
    }
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
    selectedIndex: Int,
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier
) {
    Row(modifier) {
        NavigationItem("概觀", MiuixIcons.Home, selectedIndex == 0, textColor, mutedColor)
        NavigationItem("日誌", Icons.Default.List, selectedIndex == 1, textColor, mutedColor)
        NavigationItem("設定", MiuixIcons.Settings, selectedIndex == 2, textColor, mutedColor)
    }
}

@Composable
private fun RowScope.NavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    textColor: Color,
    mutedColor: Color
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) textColor else mutedColor
        )
        Text(
            text = label,
            color = if (selected) textColor else mutedColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
