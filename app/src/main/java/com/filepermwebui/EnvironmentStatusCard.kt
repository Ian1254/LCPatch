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

    private var requestId = 0
    internal val currentRequestId: Int get() = requestId

    private fun show(request: EnvironmentOverlayRequest) {
        if (this.request != null || request.sourceBounds.width <= 0f || request.sourceBounds.height <= 0f) return
        requestId++
        closing = false
        sourceHidden = false
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
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed && overlayState.request == null) 0.982f else 1f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 760f),
        label = "environment-card-press"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = if (overlayState.sourceHidden) 0f else 1f
            }
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached && !overlayState.sourceHidden) {
                    sourceBounds = coordinates.boundsInWindow()
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
        Box(modifier = Modifier.fillMaxWidth().height(142.dp)) {
            Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp)) {
                Text(statusTitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(statusSubtitle, fontSize = 15.sp)
            }

            Icon(
                painter = painterResource(if (healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline),
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
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(330, easing = EnvironmentMorphEasing)
            )
            state.returnVisualOwnership()
            overlayVisible = false
            withFrameNanos { }
            state.finishDismiss()
        } else {
            overlayVisible = true
            state.takeVisualOwnership()
            withFrameNanos { }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(390, easing = EnvironmentMorphEasing)
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

        val sourceBounds = latestRequest.sourceBounds
        val sourceLeftPx = sourceBounds.left - hostBounds.left
        val sourceTopPx = sourceBounds.top - hostBounds.top
        val sourceRightPx = sourceBounds.right - hostBounds.left
        val sourceBottomPx = sourceBounds.bottom - hostBounds.top
        val sourceWidth = with(density) { sourceBounds.width.toDp() }
        val sourceHeight = with(density) { sourceBounds.height.toDp() }
        val fraction = progress.value.coerceIn(0f, 1f)
        val corner = lerpDp(26.dp, 0.dp, fraction)
        val panelInteraction = remember { MutableInteractionSource() }
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val expandedTitleTop = statusInset + 22.dp
        val titleStart = lerpDp(16.dp, 24.dp, fraction)
        val titleTop = lerpDp(16.dp, expandedTitleTop, fraction)
        val iconSize = lerpDp(68.dp, 46.dp, fraction)
        val iconX = lerpDp(sourceWidth - 82.dp, maxWidth - 70.dp, fraction)
        val iconY = lerpDp(14.dp, expandedTitleTop, fraction)
        // Source-only and destination-only content deliberately overlap. The
        // previous 0.52..0.58 dead zone left a large, almost empty green panel.
        val summaryAlpha = 1f - smoothIntervalProgress(fraction, 0.22f, 0.66f)
        val detailsAlpha = smoothIntervalProgress(fraction, 0.34f, 0.64f)
        val actionsAlpha = smoothIntervalProgress(fraction, 0.60f, 0.86f)
        val detailSurface = MiuixTheme.colorScheme.surface.copy(alpha = 0.12f)
        val actionSurface = MiuixTheme.colorScheme.surface.copy(alpha = 0.14f)

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
                    Column(modifier = Modifier.offset(x = titleStart, y = titleTop)) {
                        Text(
                            latestRequest.statusTitle,
                            fontSize = (22f + 7f * fraction).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            latestRequest.statusSubtitle,
                            fontSize = (15f + fraction).sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    Icon(
                        painter = painterResource(
                            if (latestRequest.healthy) R.drawable.ic_check_circle_outline
                            else R.drawable.ic_error_outline
                        ),
                        contentDescription = null,
                        modifier = Modifier.offset(x = iconX, y = iconY).size(iconSize),
                        tint = latestRequest.accent
                    )

                    Row(
                        modifier = Modifier
                            .offset(x = 16.dp, y = sourceHeight - 39.dp)
                            .width((sourceWidth - 32.dp).coerceAtLeast(0.dp))
                            .graphicsLayer { alpha = summaryAlpha },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            latestRequest.statusSummary,
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = expandedTitleTop + 112.dp,
                                bottom = navigationInset + 18.dp
                            )
                            .graphicsLayer {
                                alpha = detailsAlpha
                                translationY = with(density) {
                                    ((1f - detailsAlpha) * 18f).dp.toPx()
                                }
                            }
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
                            EnvironmentDetail("模組作用域", latestRequest.scopeStatus)
                            EnvironmentDetail("Root 權限", latestRequest.rootStatus)
                            EnvironmentDetail(
                                "Limbus Company",
                                if (latestRequest.gameInstalled) {
                                    "已安裝 · " + latestRequest.gameVersion
                                } else {
                                    "未安裝"
                                }
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = actionsAlpha
                                    translationY = with(density) {
                                        ((1f - actionsAlpha) * 14f).dp.toPx()
                                    }
                                }
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                enabled = actionsAlpha >= 0.98f,
                                onClick = latestRequest.onCheckScope
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
                                text = if (latestRequest.rootStatus == "正在請求") {
                                    "正在檢查 Root…"
                                } else {
                                    "檢查 Root 權限"
                                },
                                enabled = actionsAlpha >= 0.98f &&
                                    latestRequest.rootStatus != "正在請求",
                                onClick = latestRequest.onRequestRoot
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(actionSurface),
                                text = "完成",
                                enabled = actionsAlpha >= 0.98f,
                                onClick = state::dismiss
                            )
                        }
                    }
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
