package com.lcpatch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val EnvironmentOverlayExitMs = 210L

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

    var overlayMounted by rememberSaveable { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var sourceBounds by remember { mutableStateOf(Rect.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed && !overlayMounted) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 760f),
        label = "environment-card-press"
    )

    fun openOverlay() {
        if (!overlayMounted) {
            closing = false
            overlayMounted = true
        }
    }

    fun closeOverlay() {
        if (!overlayMounted || closing) return
        closing = true
        overlayVisible = false
        coroutineScope.launch {
            delay(EnvironmentOverlayExitMs)
            overlayMounted = false
            closing = false
        }
    }

    LaunchedEffect(overlayMounted) {
        if (overlayMounted) {
            withFrameNanos { }
            overlayVisible = true
        } else {
            overlayVisible = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
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
            Icon(
                painter = painterResource(if (healthy) R.drawable.ic_check_circle_outline else R.drawable.ic_error_outline),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(18.dp, 18.dp)
                    .size(112.dp),
                tint = accent
            )
            Column(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(
                    if (healthy) "已啟用" else if (hasError) "尚未設定作用域" else "尚未連接",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (healthy) "LCPatch ${BuildConfig.VERSION_NAME}"
                    else if (hasError) "請授予 Limbus Company 作用域"
                    else "請確認模組與作用域狀態",
                    fontSize = 15.sp
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (healthy) "Limbus Company · $gameVersion" else "模組狀態 · $scopeStatus",
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
            scopeStatus = scopeStatus,
            rootStatus = rootStatus,
            gameInstalled = gameInstalled,
            gameVersion = gameVersion,
            onCheckScope = onCheckScope,
            onRequestRoot = onRequestRoot,
            onDismiss = ::closeOverlay
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
    scopeStatus: String,
    rootStatus: String,
    gameInstalled: Boolean,
    gameVersion: String,
    onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit,
    onDismiss: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val progress = remember { Animatable(0f) }
    val panelInteraction = remember { MutableInteractionSource() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) 360 else EnvironmentOverlayExitMs.toInt(),
                easing = FastOutSlowInEasing
            )
        )
    }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = (if (dark) 0.46f else 0.30f) * progress.value
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            val hasMeasuredSource = sourceBounds.width > 0f && sourceBounds.height > 0f
            val sourceLeft = if (hasMeasuredSource) {
                with(density) { sourceBounds.left.toDp() }
            } else 12.dp
            val sourceTop = if (hasMeasuredSource) {
                with(density) { sourceBounds.top.toDp() }
            } else 76.dp
            val sourceWidth = if (hasMeasuredSource) {
                with(density) { sourceBounds.width.toDp() }
            } else maxWidth - 24.dp
            val sourceHeight = if (hasMeasuredSource) {
                with(density) { sourceBounds.height.toDp() }
            } else 142.dp
            val targetLeft = 12.dp
            val targetTop = 76.dp
            val targetWidth = maxWidth - 24.dp
            val targetHeight = minOf(500.dp, maxHeight - 100.dp)
            val fraction = progress.value
            val panelLeft = sourceLeft + (targetLeft - sourceLeft) * fraction
            val panelTop = sourceTop + (targetTop - sourceTop) * fraction
            val panelWidth = sourceWidth + (targetWidth - sourceWidth) * fraction
            val panelHeight = sourceHeight + (targetHeight - sourceHeight) * fraction
            val corner = 26.dp + 4.dp * fraction

            Box(
                modifier = Modifier
                    .offset(x = panelLeft, y = panelTop)
                    .width(panelWidth)
                    .height(panelHeight)
                    .clip(RoundedCornerShape(corner))
                    .background(cardColor)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = { }
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = (1f - fraction * 2f).coerceIn(0f, 1f)
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (healthy) R.drawable.ic_check_circle_outline
                            else R.drawable.ic_error_outline
                        ),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(18.dp, 18.dp).size(112.dp),
                        tint = accent
                    )
                    Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        Text(
                            if (healthy) "已啟用" else "環境需要處理",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("LCPatch ${BuildConfig.VERSION_NAME}", fontSize = 15.sp)
                    }
                    Text(
                        if (healthy) "Limbus Company · $gameVersion" else "模組狀態 · $scopeStatus",
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp).graphicsLayer {
                        alpha = ((fraction - 0.28f) / 0.72f).coerceIn(0f, 1f)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (healthy) "已啟用" else "環境需要處理",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "環境與權限",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            painter = painterResource(
                                if (healthy) R.drawable.ic_check_circle_outline
                                else R.drawable.ic_error_outline
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = accent
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    EnvironmentDetail("模組作用域", scopeStatus)
                    EnvironmentDetail("Root 權限", rootStatus)
                    EnvironmentDetail(
                        "Limbus Company",
                        if (gameInstalled) "已安裝 · $gameVersion" else "未安裝"
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onCheckScope) {
                        Text("重新檢查模組作用域")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (rootStatus == "正在請求") "正在檢查 Root…" else "檢查 Root 權限",
                        enabled = rootStatus != "正在請求",
                        onClick = onRequestRoot
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = "完成",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentDetail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
