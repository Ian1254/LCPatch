package com.lcpatch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val EnvironmentMorphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal data class EnvironmentOverlayRequest(
    val sourceBounds: Rect,
    val healthy: Boolean,
    val cardColor: Color,
    val accent: Color,
    val statusTitle: String,
    val statusSubtitle: String,
    val statusSummary: String,
    val scopeStatus: String,
    val rootStatus: String,
    val gameInstalled: Boolean,
    val gameVersion: String,
    val onCheckScope: () -> Unit,
    val onRequestRoot: () -> Unit
)

/**
 * Owns the source/overlay handoff. Mounting and visibility are separate states:
 * a mounted overlay cannot hide the source until its root has real coordinates.
 */
@Stable
internal class EnvironmentStatusOverlayState internal constructor() {
    internal var request by mutableStateOf<EnvironmentOverlayRequest?>(null)
        private set
    internal var sourceHidden by mutableStateOf(false)
        private set
    internal var closing by mutableStateOf(false)
        private set
    internal var liveSourceBounds by mutableStateOf(Rect.Zero)
        private set

    private var requestId = 0
    internal val currentRequestId: Int get() = requestId

    private fun show(request: EnvironmentOverlayRequest) {
        if (this.request != null || request.sourceBounds.width <= 0f || request.sourceBounds.height <= 0f) return
        requestId++
        closing = false
        sourceHidden = false
        liveSourceBounds = request.sourceBounds
        this.request = request
    }

    internal fun dismiss() {
        if (request != null) closing = true
    }

    internal fun reopen() {
        if (request != null && closing) closing = false
    }

    internal fun takeVisualOwnership() {
        sourceHidden = true
    }

    internal fun returnVisualOwnership() {
        sourceHidden = false
    }

    internal fun finishDismiss() {
        sourceHidden = false
        closing = false
        request = null
    }

    internal fun updateSourceBounds(bounds: Rect) {
        if (bounds.width > 0f && bounds.height > 0f) liveSourceBounds = bounds
    }

    internal fun open(
        sourceBounds: Rect,
        healthy: Boolean,
        cardColor: Color,
        accent: Color,
        statusTitle: String,
        statusSubtitle: String,
        statusSummary: String,
        scopeStatus: String,
        rootStatus: String,
        gameInstalled: Boolean,
        gameVersion: String,
        onCheckScope: () -> Unit,
        onRequestRoot: () -> Unit
    ) {
        show(
            EnvironmentOverlayRequest(
                sourceBounds = sourceBounds,
                healthy = healthy,
                cardColor = cardColor,
                accent = accent,
                statusTitle = statusTitle,
                statusSubtitle = statusSubtitle,
                statusSummary = statusSummary,
                scopeStatus = scopeStatus,
                rootStatus = rootStatus,
                gameInstalled = gameInstalled,
                gameVersion = gameVersion,
                onCheckScope = onCheckScope,
                onRequestRoot = onRequestRoot
            )
        )
    }
}

@Composable
internal fun rememberEnvironmentStatusOverlayState(): EnvironmentStatusOverlayState =
    remember { EnvironmentStatusOverlayState() }

@Composable
internal fun EnvironmentStatusOverviewCard(
    overlayState: EnvironmentStatusOverlayState,
    scopeStatus: String,
    rootStatus: String,
    gameInstalled: Boolean,
    gameVersion: String,
    onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit
) {
    val healthy = scopeStatus == "已啟用"
    val hasError = scopeStatus == "尚未授權遊戲"
    val dark = isSystemInDarkTheme()
    val cardColor = when {
        healthy -> if (dark) Color(0xFF173D27) else Color(0xFFDFFAE4)
        hasError -> if (dark) Color(0xFF472224) else Color(0xFFFFDAD9)
        else -> MiuixTheme.colorScheme.secondaryContainer
    }
    val accent = when {
        healthy -> Color(0xFF43D477)
        hasError -> Color(0xFFFF6B70)
        else -> MiuixTheme.colorScheme.primary.copy(alpha = 0.62f)
    }
    val statusTitle = when {
        healthy -> "已啟用"
        hasError -> "尚未設定作用域"
        else -> "尚未連接"
    }
    val statusSubtitle = when {
        healthy -> "LCPatch " + BuildConfig.VERSION_NAME
        hasError -> "請授予 Limbus Company 作用域"
        else -> "請確認模組與作用域狀態"
    }
    val statusSummary = if (healthy) {
        "Limbus Company · $gameVersion"
    } else {
        "模組狀態 · $scopeStatus"
    }

    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardAlpha by animateFloatAsState(
        targetValue = if (cardPressed && overlayState.request == null) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 760f),
        label = "environment-card-press"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (overlayState.sourceHidden) 0f else cardAlpha
            }
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    sourceBounds = coordinates.boundsInWindow()
                    overlayState.updateSourceBounds(sourceBounds)
                }
            }
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = cardInteraction,
                indication = null
            ) {
                overlayState.open(
                    sourceBounds = sourceBounds,
                    healthy = healthy,
                    cardColor = cardColor,
                    accent = accent,
                    statusTitle = statusTitle,
                    statusSubtitle = statusSubtitle,
                    statusSummary = statusSummary,
                    scopeStatus = scopeStatus,
                    rootStatus = rootStatus,
                    gameInstalled = gameInstalled,
                    gameVersion = gameVersion,
                    onCheckScope = onCheckScope,
                    onRequestRoot = onRequestRoot
                )
            },
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        EnvironmentStatusCollapsedContent(
            healthy = healthy,
            accent = accent,
            statusTitle = statusTitle,
            statusSubtitle = statusSubtitle,
            statusSummary = statusSummary,
            modifier = Modifier.fillMaxWidth().height(142.dp)
        )
    }
}

@Composable
private fun EnvironmentStatusCollapsedContent(
    healthy: Boolean,
    accent: Color,
    statusTitle: String,
    statusSubtitle: String,
    statusSummary: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp)
        ) {
            Text(statusTitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(statusSubtitle, fontSize = 15.sp)
        }

        Icon(
            painter = painterResource(
                if (healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline
            ),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(68.dp),
            tint = accent
        )

        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                statusSummary,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "詳情 ›",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun EnvironmentStatusOverlayHost(
    state: EnvironmentStatusOverlayState,
    modifier: Modifier = Modifier
) {
    val request = state.request ?: return
    val requestId = state.currentRequestId
    val density = LocalDensity.current
    val progress = remember(requestId) { Animatable(0f) }
    val latestRequest by rememberUpdatedState(request)
    var hostBounds by remember(requestId) { mutableStateOf(Rect.Zero) }
    var overlayVisible by remember(requestId) { mutableStateOf(false) }
    val rootReady = hostBounds.width > 0f && hostBounds.height > 0f

    BackHandler(enabled = true, onBack = state::dismiss)

    androidx.compose.runtime.LaunchedEffect(requestId, rootReady, state.closing) {
        if (!rootReady) return@LaunchedEffect
        if (state.closing) {
            val duration = (330f * progress.value.coerceIn(0f, 1f))
                .roundToInt().coerceAtLeast(1)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(duration, easing = EnvironmentMorphEasing)
            )
            // Restore the real source first. Keep the pixel-identical overlay
            // endpoint mounted for a full overlap frame before unmounting it.
            state.returnVisualOwnership()
            withFrameNanos { }
            overlayVisible = false
            withFrameNanos { }
            state.finishDismiss()
        } else {
            if (!overlayVisible) {
                progress.snapTo(0f)
                overlayVisible = true
                // One frame mounts/layouts progress=0; the second guarantees
                // that endpoint has been presented before ownership changes.
                withFrameNanos { }
                withFrameNanos { }
            }
            if (!state.sourceHidden) {
                state.takeVisualOwnership()
                withFrameNanos { }
            }
            val duration = (390f * (1f - progress.value.coerceIn(0f, 1f)))
                .roundToInt().coerceAtLeast(1)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(duration, easing = EnvironmentMorphEasing)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) hostBounds = coordinates.boundsInWindow()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = state::dismiss
            )
    ) {
        if (!overlayVisible) return@BoxWithConstraints

        val sourceBounds = state.liveSourceBounds.takeIf {
            it.width > 0f && it.height > 0f
        } ?: latestRequest.sourceBounds
        val sourceLeftPx = sourceBounds.left - hostBounds.left
        val sourceTopPx = sourceBounds.top - hostBounds.top
        val sourceRightPx = sourceBounds.right - hostBounds.left
        val sourceBottomPx = sourceBounds.bottom - hostBounds.top
        val fraction = progress.value.coerceIn(0f, 1f)
        val corner = lerpDp(26.dp, 0.dp, fraction)
        val panelInteraction = remember { MutableInteractionSource() }
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // The two complete endpoint layers deliberately overlap. progress=0
        // is the exact shared collapsed composable used by the real Card.
        val collapsedAlpha = 1f - smoothIntervalProgress(fraction, 0.22f, 0.62f)
        val expandedAlpha = smoothIntervalProgress(fraction, 0.34f, 0.68f)

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(corner))
                        .background(latestRequest.cardColor)
                        .clickable(
                            interactionSource = panelInteraction,
                            indication = null,
                            onClick = state::reopen
                        )
                ) {
                    EnvironmentStatusCollapsedContent(
                        healthy = latestRequest.healthy,
                        accent = latestRequest.accent,
                        statusTitle = latestRequest.statusTitle,
                        statusSubtitle = latestRequest.statusSubtitle,
                        statusSummary = latestRequest.statusSummary,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = collapsedAlpha }
                    )
                    EnvironmentStatusExpandedContent(
                        request = latestRequest,
                        statusInset = statusInset,
                        navigationInset = navigationInset,
                        contentAlpha = expandedAlpha,
                        actionsEnabled = fraction >= 0.98f,
                        onDismiss = state::dismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = expandedAlpha
                                translationY = with(density) {
                                    ((1f - expandedAlpha) * 12f).dp.toPx()
                                }
                            }
                    )
                }
            }
        ) { measurables, constraints ->
            // Interpolate and round the four physical edges first. Width and
            // height are derived from those rounded edges, so progress 0/1 is
            // pixel-identical to the measured source/host instead of rounding
            // offset and size independently through Dp.
            val left = lerpFloat(sourceLeftPx, 0f, fraction).roundToInt()
            val top = lerpFloat(sourceTopPx, 0f, fraction).roundToInt()
            val right = lerpFloat(sourceRightPx, constraints.maxWidth.toFloat(), fraction)
                .roundToInt().coerceAtLeast(left + 1)
            val bottom = lerpFloat(sourceBottomPx, constraints.maxHeight.toFloat(), fraction)
                .roundToInt().coerceAtLeast(top + 1)
            val placeable = measurables.single().measure(
                Constraints.fixed(right - left, bottom - top)
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(left, top)
            }
        }
    }
}

@Composable
private fun EnvironmentStatusExpandedContent(
    request: EnvironmentOverlayRequest,
    statusInset: Dp,
    navigationInset: Dp,
    contentAlpha: Float,
    actionsEnabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleTop = statusInset + 22.dp
    val detailSurface = MiuixTheme.colorScheme.surface.copy(alpha = 0.12f)
    val actionSurface = MiuixTheme.colorScheme.surface.copy(alpha = 0.14f)
    val actionsAlpha = smoothIntervalProgress(contentAlpha, 0.42f, 0.88f)

    Box(modifier = modifier) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = titleTop)) {
            Text(request.statusTitle, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                request.statusSubtitle,
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        Icon(
            painter = painterResource(
                if (request.healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline
            ),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = titleTop, end = 24.dp).size(46.dp),
            tint = request.accent
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = titleTop + 112.dp,
                    bottom = navigationInset + 18.dp
                )
        ) {
            Text(
                "環境與權限",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(detailSurface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                EnvironmentDetail("模組作用域", request.scopeStatus)
                EnvironmentDetail("Root 權限", request.rootStatus)
                EnvironmentDetail(
                    "Limbus Company",
                    if (request.gameInstalled) "已安裝 · " + request.gameVersion else "未安裝"
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = actionsAlpha }
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = actionsEnabled,
                    onClick = request.onCheckScope
                ) {
                    Text("重新檢查模組作用域")
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(actionSurface),
                    text = if (request.rootStatus == "正在請求") {
                        "正在檢查 Root…"
                    } else {
                        "檢查 Root 權限"
                    },
                    enabled = actionsEnabled && request.rootStatus != "正在請求",
                    onClick = request.onRequestRoot
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(actionSurface),
                    text = "完成",
                    enabled = actionsEnabled,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun EnvironmentDetail(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun intervalProgress(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun smoothIntervalProgress(value: Float, start: Float, end: Float): Float {
    val t = intervalProgress(value, start, end)
    return t * t * (3f - 2f * t)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction
