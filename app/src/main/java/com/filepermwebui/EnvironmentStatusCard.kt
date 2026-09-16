package com.lcpatch

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EnvironmentStatusOverviewCard(
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
        healthy -> "LCPatch ${BuildConfig.VERSION_NAME}"
        hasError -> "請授予 Limbus Company 作用域"
        else -> "請確認模組與作用域狀態"
    }
    val statusSummary = if (healthy) {
        "Limbus Company · $gameVersion"
    } else {
        "模組狀態 · $scopeStatus"
    }

    var overlayMounted by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed && !overlayMounted) 0.982f else 1f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 760f),
        label = "environment-card-press"
    )

    fun openOverlay() {
        if (overlayMounted) return
        closing = false
        overlayMounted = true
        overlayVisible = true
    }

    fun closeOverlay() {
        if (!overlayMounted || closing) return
        closing = true
        overlayVisible = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = if (overlayMounted && !closing) 0f else 1f
            }
            .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = ::openOverlay
            ),
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

    if (overlayMounted) {
        EnvironmentStatusOverlay(
            visible = overlayVisible,
            healthy = healthy,
            cardColor = cardColor,
            accent = accent,
            sourceBounds = sourceBounds,
            statusTitle = statusTitle,
            statusSubtitle = statusSubtitle,
            statusSummary = statusSummary,
            scopeStatus = scopeStatus,
            rootStatus = rootStatus,
            gameInstalled = gameInstalled,
            gameVersion = gameVersion,
            onCheckScope = onCheckScope,
            onRequestRoot = onRequestRoot,
            onDismiss = ::closeOverlay,
            onExitFinished = {
                overlayMounted = false
                overlayVisible = false
                closing = false
            }
        )
    }
}

@Composable
private fun EnvironmentStatusOverlay(
    visible: Boolean,
    healthy: Boolean,
    cardColor: Color,
    accent: Color,
    sourceBounds: Rect,
    statusTitle: String,
    statusSubtitle: String,
    statusSummary: String,
    scopeStatus: String,
    rootStatus: String,
    gameInstalled: Boolean,
    gameVersion: String,
    onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit,
    onDismiss: () -> Unit,
    onExitFinished: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val panelInteraction = remember { MutableInteractionSource() }
    val latestExitFinished by androidx.compose.runtime.rememberUpdatedState(onExitFinished)

    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f)
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.91f, stiffness = 430f)
            )
            withFrameNanos { }
            withFrameNanos { }
            latestExitFinished()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            val hasMeasuredSource = sourceBounds.width > 0f && sourceBounds.height > 0f
            val sourceLeft = if (hasMeasuredSource) with(density) { sourceBounds.left.toDp() } else 12.dp
            val sourceTop = if (hasMeasuredSource) with(density) { sourceBounds.top.toDp() } else 76.dp
            val sourceWidth = if (hasMeasuredSource) with(density) { sourceBounds.width.toDp() } else maxWidth - 24.dp
            val sourceHeight = if (hasMeasuredSource) with(density) { sourceBounds.height.toDp() } else 142.dp

            val fraction = progress.value.coerceIn(0f, 1f)
            val panelLeft = lerpDp(sourceLeft, 0.dp, fraction)
            val panelTop = lerpDp(sourceTop, 0.dp, fraction)
            val panelWidth = lerpDp(sourceWidth, maxWidth, fraction)
            val panelHeight = lerpDp(sourceHeight, maxHeight, fraction)
            val corner = lerpDp(26.dp, 0.dp, fraction)
            val panelAlpha = if (visible) 1f else (fraction / 0.14f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .offset(x = panelLeft, y = panelTop)
                    .width(panelWidth)
                    .height(panelHeight)
                    .graphicsLayer { alpha = panelAlpha }
                    .clip(RoundedCornerShape(corner))
                    .background(cardColor)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = { }
                    )
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val expandedTitleTop = statusInset + 22.dp
                    val titleStart = lerpDp(16.dp, 24.dp, fraction)
                    val titleTop = lerpDp(16.dp, expandedTitleTop, fraction)
                    val iconSize = lerpDp(68.dp, 46.dp, fraction)
                    val collapsedIconX = sourceWidth - 68.dp - 14.dp
                    val collapsedIconY = 14.dp
                    val expandedIconX = maxWidth - 24.dp - 46.dp
                    val expandedIconY = expandedTitleTop
                    val iconX = lerpDp(collapsedIconX, expandedIconX, fraction)
                    val iconY = lerpDp(collapsedIconY, expandedIconY, fraction)
                    val summaryAlpha = (1f - fraction / 0.30f).coerceIn(0f, 1f)
                    val detailsAlpha = ((fraction - 0.25f) / 0.46f).coerceIn(0f, 1f)
                    val actionsAlpha = ((fraction - 0.45f) / 0.36f).coerceIn(0f, 1f)
                    val detailSurface = if (dark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.055f)
                    val actionSurface = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.065f)

                    Column(modifier = Modifier.offset(x = titleStart, y = titleTop)) {
                        Text(
                            statusTitle,
                            fontSize = (22f + 7f * fraction).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            statusSubtitle,
                            fontSize = (15f + fraction).sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    Icon(
                        painter = painterResource(if (healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline),
                        contentDescription = null,
                        modifier = Modifier.offset(x = iconX, y = iconY).size(iconSize),
                        tint = accent
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
                                translationY = with(density) { ((1f - detailsAlpha) * 18f).dp.toPx() }
                            }
                    ) {
                        Text(
                            "環境與權限",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dark) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.74f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(detailSurface, RoundedCornerShape(22.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            EnvironmentDetail("模組作用域", scopeStatus)
                            EnvironmentDetail("Root 權限", rootStatus)
                            EnvironmentDetail(
                                "Limbus Company",
                                if (gameInstalled) "已安裝 · $gameVersion" else "未安裝"
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = actionsAlpha
                                    translationY = with(density) { ((1f - actionsAlpha) * 14f).dp.toPx() }
                                }
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                onClick = onCheckScope
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
                                text = if (rootStatus == "正在請求") "正在檢查 Root…" else "檢查 Root 權限",
                                enabled = rootStatus != "正在請求",
                                onClick = onRequestRoot
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(actionSurface),
                                text = "完成",
                                onClick = onDismiss
                            )
                        }
                    }
                }
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
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
