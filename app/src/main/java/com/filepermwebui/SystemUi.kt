package com.lcpatch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.*
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorFilter
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixDropdownPreference
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class SystemUiStyle(val id: String, val label: String) {
    HyperOS4("hyperos4", "hyper"), ColorOS17("coloros17", "color");
    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value || it.label == value } ?: HyperOS4
    }
}
internal val LocalSystemUiStyle = staticCompositionLocalOf { SystemUiStyle.HyperOS4 }
internal val LocalSystemUiDark = staticCompositionLocalOf { false }
private val LocalCardInset = staticCompositionLocalOf { 0.dp }
private val LocalGrouped = staticCompositionLocalOf { false }

@Composable internal fun SystemUiTheme(style: SystemUiStyle, themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    if (style == SystemUiStyle.HyperOS4) {
        // Keep the released app's actual Miuix typography, color roles and widgets.
        val controller = remember(themeMode) {
            ThemeController(colorSchemeMode = when (themeMode) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            })
        }
        CompositionLocalProvider(LocalSystemUiStyle provides style, LocalSystemUiDark provides dark) {
            MiuixTheme(controller = controller) {
                CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) { SystemWindowSurface(content) }
            }
        }
        return
    }
    val colors = remember(style, dark) {
        val soft = style == SystemUiStyle.HyperOS4
        val accent = Color(if (soft) { if (dark) 0xFF4788FF else 0xFF3482FF }
            else { if (dark) 0xFF1A8CFF else 0xFF0080FF })
        val canvas = Color(if (dark) 0xFF000000 else if (soft) 0xFFF3F3F3 else 0xFFF0F1F2)
        val card = Color(if (dark) { if (soft) 0xFF242424 else 0xFF1A1A1A } else 0xFFFFFFFF)
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = accent, onPrimary = Color.White,
            primaryVariant = Color(if (soft) 0xFF2469CE else 0xFF0069D9), primaryContainer = accent.copy(alpha = 0.11f), onPrimaryContainer = accent,
            secondaryContainer = card, background = canvas, surface = canvas, surfaceContainer = card,
            onSurface = Color(if (soft) { if (dark) 0xFFF2F2F2 else 0xFF000000 }
                else { if (dark) 0xE6FFFFFF else 0xE6000000 }),
            onBackground = Color(if (dark) 0xFFF2F2F2 else 0xFF000000),
            onSurfaceVariantSummary = Color(if (dark) 0x8AFFFFFF else 0x8A000000),
            dividerLine = Color(if (dark) 0xFF333333 else 0xFFE8E8E8))
    }
    CompositionLocalProvider(LocalSystemUiStyle provides style, LocalSystemUiDark provides dark) {
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) { SystemWindowSurface(content) }
        }
    }
}

internal val LocalSystemWindowBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalSystemPanelBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalSystemMaterialEnabled = staticCompositionLocalOf { true }

@Composable private fun SystemWindowSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) { content() }
}

@Composable internal fun SystemMaterialScope(enabled: Boolean, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalSystemMaterialEnabled provides enabled) { SystemCanvas(content) }
}

@Composable internal fun systemPageGutter(): Dp {
    val base = if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) 12.dp else 16.dp
    return maxOf(base, (LocalConfiguration.current.screenWidthDp.dp - 600.dp) / 2)
}

@Composable internal fun SystemCanvas(content: @Composable BoxScope.() -> Unit) {
    val base = MiuixTheme.colorScheme.surface
    Box(Modifier.fillMaxSize().background(base), content = content)
}

/** Content panels stay quiet; glass is reserved for floating controls. */
@Composable internal fun SystemCard(modifier: Modifier = Modifier, insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    cornerRadius: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    val radius = if (cornerRadius != Dp.Unspecified) cornerRadius else if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) CardDefaults.CornerRadius else 12.dp
    val shape = RoundedCornerShape(radius)
    CompositionLocalProvider(LocalCardInset provides insideMargin.calculateLeftPadding(LayoutDirection.Ltr), LocalGrouped provides true,
        LocalContentColor provides colors.contentColor) {
        if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
            MiuixCard(modifier = modifier.fillMaxWidth(), cornerRadius = radius, insideMargin = insideMargin, colors = colors, content = content)
        } else Column(modifier.fillMaxWidth().clip(shape).background(colors.color).padding(insideMargin), content = content)
    }
}

@Composable internal fun SystemPageHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
        Text(title, fontSize = 32.sp, lineHeight = 42.sp,
            fontWeight = if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) FontWeight.Normal else FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface)
        if (subtitle != null) Text(subtitle, modifier = Modifier.padding(top = 4.dp), fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemSectionLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable internal fun SystemGlyphTile(glyph: SystemGlyph, modifier: Modifier = Modifier) {
    val ink = when (glyph) {
        SystemGlyph.Language, SystemGlyph.Font -> Color(0xFF8064D8)
        SystemGlyph.Appearance -> Color(0xFFC76B94)
        SystemGlyph.Sync, SystemGlyph.Shield -> Color(0xFF2E9C82)
        SystemGlyph.Info -> Color(0xFF7D8796)
        else -> Color(0xFF518BDB)
    }
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    Box(modifier.size(30.dp).background(if (soft) ink else Color.Transparent,
        RoundedCornerShape(if (soft) 9.dp else 10.dp)), contentAlignment = Alignment.Center) {
        Icon(glyph.vector(), null, Modifier.size(if (soft) 19.dp else 24.dp), tint = if (soft) Color.White else MiuixTheme.colorScheme.onSurface)
    }
}

@Composable private fun PreferenceRow(title: String, summary: String?, value: String?, modifier: Modifier,
    enabled: Boolean, trailing: @Composable () -> Unit, action: Modifier) {
    val inset = (16.dp - LocalCardInset.current).coerceAtLeast(0.dp)
    val glyph: SystemGlyph? = null
    val divider = MiuixTheme.colorScheme.dividerLine
    val grouped = LocalGrouped.current
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val stackedValue = LocalDensity.current.fontScale > 1.3f
    Row(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).then(action)
        .drawBehind {
            if (grouped && soft) {
                val left = inset.toPx() + if (glyph != null) 44.dp.toPx() else 0f
                drawLine(divider, Offset(left, size.height - 0.5f), Offset(size.width - inset.toPx(), size.height - 0.5f), 0.5.dp.toPx())
            }
        }.heightIn(min = 56.dp).padding(horizontal = inset, vertical = if (soft) 14.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) { SystemGlyphTile(glyph); Spacer(Modifier.width(13.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = if (soft) FontWeight.Normal else FontWeight.Medium)
            if (!summary.isNullOrBlank()) Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = if (soft) 12.sp else 13.sp, lineHeight = 18.sp)
            if (value != null && stackedValue) Text(value, fontSize=13.sp,lineHeight=18.sp, color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (value != null && !stackedValue) {
            Text(value, Modifier.padding(start = 12.dp).widthIn(max = 124.dp), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp)); trailing()
    }
}

@Composable internal fun SystemArrowPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, value: String? = null, onClick: () -> Unit) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixArrowPreference(title = title, summary = summary ?: value, modifier = modifier, enabled = enabled, onClick = onClick)
        return
    }
    PreferenceRow(title, summary, value, modifier, enabled,
        trailing = { Icon(SystemGlyph.Chevron.vector(), null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
        action = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))
}

@Composable internal fun SystemToggle(checked: Boolean, enabled: Boolean = true) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    if (soft) {
        MiuixSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        return
    }
    val position by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 700f), label = "switch-position")
    val fill by animateColorAsState(if (checked) MiuixTheme.colorScheme.primary else
        Color(if (LocalSystemUiDark.current) 0xFF4C4F57 else 0xFFD9DCE2), label = "switch-fill")
    val width = if (soft) 49.dp else 44.dp
    val height = if (soft) 28.dp else 24.dp
    val thumb = if (soft) 20.dp else 18.dp
    val padding = (height - thumb) / 2
    Box(Modifier.size(width, height).clip(CircleShape).background(fill.copy(alpha = if (enabled) 1f else 0.45f))) {
        Box(Modifier.offset(x = padding + (width - thumb - padding * 2) * position, y = padding)
            .size(thumb).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable internal fun SystemSwitchPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixSwitchPreference(title = title, summary = summary, modifier = modifier, enabled = enabled,
            checked = checked, onCheckedChange = onCheckedChange)
        return
    }
    PreferenceRow(title, summary, null, modifier, enabled, trailing = { SystemToggle(checked, enabled) },
        action = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange))
}

@Composable internal fun SystemButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColorsPrimary(), content = content)
        return
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = MiuixTheme.colorScheme.primary
    val shape = CircleShape
    Box(modifier.heightIn(min = 48.dp)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 2.dp), Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
            .background(fill.copy(alpha = if (enabled) 1f else 0.30f))
            .then(if (pressed) Modifier.background(Color.Black.copy(alpha = 0.2f)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp), Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = if (enabled) 1f else 0.70f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
            }
        }
    }
}

@Composable internal fun SystemSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text, fontSize = 16.sp) }
        return
    }
    Box(modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .background((if (LocalSystemUiDark.current) Color.White else Color.Black).copy(alpha = if (LocalSystemUiDark.current) 0.10f else 0.08f))
        .padding(horizontal = 12.dp, vertical = 10.dp), Alignment.Center) {
        Text(text, color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable internal fun SystemTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixTextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
        return
    }
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), Alignment.Center) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable internal fun SystemModal(title: String, onDismiss: () -> Unit, alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { shown = true }
    val close = {
        if (!closing) {
            closing = true
            scope.launch { shown = false; delay(if (soft) 200L else 250L); latestDismiss() }
        }
    }
    val windowScene = LocalSystemWindowBackdrop.current.takeIf { LocalSystemMaterialEnabled.current }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable(indication = null,
            interactionSource = remember { MutableInteractionSource() }, onClick = close)) {
            AnimatedVisibility(shown, modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(),
                enter = fadeIn(tween(250)) + slideInVertically(tween(250, easing = CubicBezierEasing(.3f,0f,.1f,1f))) { it / 3 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(250, easing = CubicBezierEasing(.3f,0f,1f,1f))) { it / 4 }) {
                CompositionLocalProvider(LocalSystemPanelBackdrop provides windowScene) {
                SystemSheetSurface(title, Modifier.padding(horizontal = if (soft) 12.dp else 16.dp,
                    vertical = if (!soft && alert) 32.dp else 12.dp), alert, content)
                }
            }
        }
    }
}

@Composable internal fun SystemConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SystemModal(title, onDismiss, alert = true) {
        Text(message, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontSize = 14.sp, lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SystemSecondaryButton("取消", onDismiss, Modifier.weight(1f))
            SystemButton(onConfirm, Modifier.weight(1f)) { Text(confirm, fontSize = 16.sp) }
        }
    }
}

/** Original portable approximation of the observed directional edge/inner-band
 * mechanisms. Screen/soft-light respond to the pixels beneath the stroke; this
 * does not sample native uniforms or claim to reproduce an OEM shader preset. */
private fun Modifier.acrylicSurfaceEdges(shape: Shape, dark: Boolean, sheet: Boolean = false): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val diagonal = Offset(size.width, size.height)
    val nearBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.27f else 0.68f),
        0.38f to Color.White.copy(alpha = if (dark) 0.10f else 0.24f),
        0.72f to Color.Transparent,
        1f to Color.White.copy(alpha = if (dark) 0.06f else 0.13f),
        start = Offset.Zero, end = diagonal
    )
    val farBrush = Brush.linearGradient(
        0f to Color.Transparent,
        0.45f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (dark) 0.22f else 0.12f),
        start = Offset.Zero, end = diagonal
    )
    val innerBrush = Brush.linearGradient(
        0f to Color.Transparent,
        0.24f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (dark) 0.10f else 0.055f),
        start = Offset.Zero, end = Offset(size.width * 0.32f, size.height)
    )
    val lowerBandBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.52f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (dark) 0.075f else 0.035f)
    )
    fun edgePaint(brush: Brush, width: Float, mode: BlendMode) = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = width
        blendMode = mode
        brush.applyTo(size, this, 1f)
    }
    // All strokes are clipped by the surface outline. A wide, faint band and a
    // narrow near/far rim remain independent, instead of a uniform bright border.
    val innerPaint = edgePaint(innerBrush, (if (sheet) 6.dp else 4.dp).toPx(), BlendMode.Softlight)
    val lowerPaint = edgePaint(lowerBandBrush, (if (sheet) 5.dp else 3.dp).toPx(), BlendMode.Multiply)
    val nearPaint = edgePaint(nearBrush, 0.8.dp.toPx(), BlendMode.Screen)
    val farPaint = edgePaint(farBrush, 0.7.dp.toPx(), BlendMode.Multiply)
    onDrawWithContent {
        drawContent()
        drawContext.canvas.drawOutline(outline, innerPaint)
        drawContext.canvas.drawOutline(outline, lowerPaint)
        drawContext.canvas.drawOutline(outline, nearPaint)
        drawContext.canvas.drawOutline(outline, farPaint)
    }
}

@Composable internal fun SystemSheetSurface(title: String, modifier: Modifier = Modifier, alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val dark = LocalSystemUiDark.current
    val radius = if (soft) 36.dp else if (alert) 24.dp else 28.dp
    val shape = RoundedCornerShape(radius)
    val scene = LocalSystemPanelBackdrop.current.takeIf { LocalSystemMaterialEnabled.current }
    val glass = if (soft && scene != null) Modifier.hyperGlassBackdrop(scene, shape, radius) else Modifier
    val surface = if (soft) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (scene != null) 0.84f else 1f)
        else Color(if (dark) 0xFF1E1E1E else 0xFFFFFFFF)
    Column(modifier.widthIn(max = 480.dp).fillMaxWidth()
        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f).clip(shape).then(glass)
        .background(surface).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
        .verticalScroll(rememberScrollState()).padding(top = if (alert) 14.dp else 10.dp, bottom = 10.dp)) {
        if (!alert) Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(32.dp, 4.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape))
        Text(title, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            textAlign = if (!soft && alert) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
            fontSize = if (soft) 20.sp else 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable internal fun SystemSelectionRows(items: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        items.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(i == selected, role = Role.RadioButton) { onSelected(i) }
                .padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(24.dp), Alignment.Center) {
                    val color = if (i == selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = .38f)
                    Box(Modifier.size(if (i == selected) 20.dp else 18.4.dp).then(if (i == selected) Modifier.background(color, CircleShape)
                        else Modifier.border(1.3.dp, color, CircleShape)), Alignment.Center) {
                        if (i == selected) Box(Modifier.size(12.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable internal fun SystemChoiceMenuSurface(items: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalSystemUiDark.current
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.widthIn(min = 180.dp, max = 300.dp).width(IntrinsicSize.Max).clip(shape)
        .background(Color(if (dark) 0xFF262626 else 0xFFFFFFFF)).padding(vertical = 8.dp).selectableGroup()) {
        items.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(i == selected, role = Role.RadioButton) { onSelected(i) }
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(18.dp))
                Box(Modifier.size(20.dp), Alignment.Center) {
                    if (i == selected) Icon(SystemGlyph.Check.vector(), null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable internal fun SystemDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null) {
    if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) {
        MiuixDropdownPreference(title = title, items = items, selectedIndex = selectedIndex, summary = summary,
            modifier = modifier, enabled = enabled, onSelectedIndexChange = onSelectedIndexChange)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val position = remember(margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val x = (if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right-popupContentSize.width else anchorBounds.left)
                .coerceIn(margin, maxOf(margin, windowSize.width-popupContentSize.width-margin))
            val below = anchorBounds.bottom+margin
            val y = (if (below+popupContentSize.height <= windowSize.height-margin) below else anchorBounds.top-popupContentSize.height-margin)
                .coerceIn(margin, maxOf(margin, windowSize.height-popupContentSize.height-margin))
            return IntOffset(x,y)
        }
    } }
    Box {
        PreferenceRow(title, summary, items.getOrNull(selectedIndex), modifier, enabled,
            trailing = { Icon(SystemGlyph.Chevron.vector(), null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
            action = Modifier.clickable(enabled = enabled, role = Role.Button) { expanded = true })
        if (expanded) Popup(popupPositionProvider = position, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            Box(Modifier.shadow(12.dp, RoundedCornerShape(16.dp)).heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                SystemChoiceMenuSurface(items, selectedIndex, { onSelectedIndexChange(it); expanded = false })
            }
        }
    }
}

@Composable internal fun SystemStylePicker(selected: SystemUiStyle, onSelected: (SystemUiStyle) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("介面風格", fontSize = 16.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SystemUiStyle.entries.forEach { style ->
                val active = style == selected
                val soft = style == SystemUiStyle.HyperOS4
                val shape = RoundedCornerShape(18.dp)
                Column(Modifier.weight(1f).clip(shape)
                    .background(MiuixTheme.colorScheme.surface)
                    .border(if (active) 1.5.dp else 0.6.dp, if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine, shape)
                    .selectable(active, role = Role.RadioButton) { onSelected(style) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(11.dp))
                        .background(Color(if (soft) 0xFFF6F6F6 else 0xFFF0F1F2))) {
                        Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(29.dp).height(4.dp).background(Color(0xFF5C6C81).copy(alpha = 0.55f), CircleShape))
                            repeat(2) {
                                Row(Modifier.fillMaxWidth().height(16.dp).background(Color.White.copy(alpha = if (soft) 0.70f else 0.88f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(5.dp).background(Color(0xFF8BA6BD), CircleShape))
                                    Box(Modifier.padding(start = 5.dp).width(23.dp).height(3.dp).background(Color(0xFF9BA9B8), CircleShape))
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(58.dp, 12.dp)
                            .background(Color.White.copy(alpha = 0.88f), CircleShape).border(0.5.dp, Color.White, CircleShape))
                    }
                    Row(Modifier.padding(top = 11.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(style.label, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1)
                        if (active) Icon(SystemGlyph.Check.vector(), null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Native dimensions come from the two supplied clock packages; the portable
 * backdrop renderer approximates their OEM-only material/force-dark services. */
@Composable internal fun SystemBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, floating: Boolean,
    backdrop: Backdrop? = null, hazeState: HazeState? = null, targetIndex: Int = index, onSelected: (Int) -> Unit) {
    val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val dark = LocalSystemUiDark.current
    val density = LocalDensity.current
    val large = density.fontScale > 1.4f
    val labelHeight = with(density) { (if (hyper) 14.sp else 12.sp).toDp() }
    val barHeight = if (hyper) maxOf(54.dp, 40.dp+labelHeight*(if (large) 2 else 1)) else if (large) 72.dp else 56.dp
    val vertical = if (hyper) 3.dp else 4.dp
    val itemHeight = barHeight-vertical*2
    val horizontal = if (hyper) 3.dp else 12.dp
    val gap = if (hyper) (-10).dp else 8.dp
    val preferredWidth = if (hyper) (labels.size*88-(labels.size-1)*10+6).dp else (labels.size*70+24+(labels.size-1)*8).dp
    val shape = if (floating) CircleShape else RoundedCornerShape(0.dp)
    val hazeStyle = remember(hyper) { HazeBlurStyle { blurRadius(if (hyper) 20.dp else 30.dp) } }
    val glass = when {
        hazeState != null -> Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
        backdrop != null -> Modifier.drawBackdrop(backdrop, shape = { shape }, effects = {
            if (hyper) {
                blur(20.dp.toPx())
                // Source Pured_Thin_Glass, alpha-aware PLUS_DARKER on the opaque page capture.
                val subtract = if (dark) -26f else -5f
                colorFilter(ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    1f,0f,0f,0f,subtract, 0f,1f,0f,0f,subtract, 0f,0f,1f,0f,subtract, 0f,0f,0f,1f,0f))))
                if (dark) {
                    colorFilter(ColorFilter.tint(Color(0x66565656), BlendMode.Luminosity))
                    colorFilter(ColorFilter.tint(Color(0x993F3F3F), BlendMode.Overlay))
                } else {
                    colorFilter(ColorFilter.tint(Color(0x99FFFFFF), BlendMode.Softlight))
                    colorFilter(ColorFilter.tint(Color(0x66FFFFFF), BlendMode.Hardlight))
                }
            } else {
                blur(25f, edgeTreatment = TileMode.Mirror)
                colorFilter(ColorFilter.tint(Color(if (dark) 0x80666666 else 0xB3666666), if (dark) BlendMode.Overlay else BlendMode.ColorDodge))
                colorFilter(ColorFilter.tint(Color(if (dark) 0xB3262626 else 0x99F5F5F5), BlendMode.Luminosity))
            }
        }, highlight = null, shadow = null)
        else -> Modifier
    }
    val tint = if (backdrop != null && hazeState == null) Color.Transparent else if (hazeState != null)
        (if (dark) Color(0xFF272727) else Color.White).copy(alpha = .56f)
        else Color(if (dark) 0xFF1E1E1E else 0xFFFAFAFA)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .4f else .5f),
        Color.White.copy(alpha = .1f), Color.White.copy(alpha = if (dark) .2f else .3f)))
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = if (!floating) inset else if (hyper) maxOf(24.dp,inset+8.dp) else inset+8.dp
    val sources = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val hovered = sources.map { it.collectIsHoveredAsState().value || it.collectIsFocusedAsState().value }
    Box(Modifier.fillMaxWidth().padding(start = if (floating) 24.dp else 0.dp, end = if (floating) 24.dp else 0.dp,
        top = if (floating && hyper) 20.dp else 0.dp, bottom = bottom), Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = if (floating) preferredWidth else 600.dp).fillMaxWidth()
            .shadow(if (floating) 10.dp else 0.dp, shape, ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .18f))
            .clip(shape).then(glass).background(tint)
            .then(if (!floating) Modifier else if (hyper) Modifier.border(.8.dp,rim,shape) else Modifier.acrylicSurfaceEdges(shape,dark))
            .padding(horizontal = horizontal, vertical = vertical)) {
            val cell = (maxWidth-gap*(labels.size-1))/labels.size
            val stride = cell+gap
            val stridePx = with(density) { stride.toPx() }
            val physics = remember { BottomBarPhysics(targetIndex.toFloat()) }
            var revision by remember { mutableIntStateOf(0) }
            var dragging by remember { mutableStateOf(false) }
            val latestTarget by rememberUpdatedState(targetIndex)
            val latestSelected by rememberUpdatedState(onSelected)
            LaunchedEffect(targetIndex) {
                if (!android.animation.ValueAnimator.areAnimatorsEnabled()) physics.snapTo(targetIndex) else physics.select(targetIndex)
                revision++
            }
            LaunchedEffect(physics) {
                snapshotFlow { revision }.collect {
                    var previous = withFrameNanos { it }
                    while (physics.active || physics.dragging) {
                        val now = withFrameNanos { it }
                        if (!physics.dragging && !android.animation.ValueAnimator.areAnimatorsEnabled()) physics.snapTo(latestTarget)
                        else physics.advance((now-previous)/1_000_000_000f)
                        previous = now; revision++
                    }
                }
            }
            @Suppress("UNUSED_VARIABLE") val frame = revision
            val left = physics.leftPosition*stride
            val width = (physics.rightPosition-physics.leftPosition)*stride+(cell-stride)
            val stateIndex = if (dragging) physics.dragPosition.roundToInt().coerceIn(0,labels.lastIndex) else targetIndex.coerceIn(0,labels.lastIndex)
            val isPressed = pressed[stateIndex] || dragging
            val press by animateFloatAsState(if (isPressed) 1f else 0f, spring(.7f,250f), label = "nav-press")
            val alpha by animateFloatAsState(if (isPressed) { if (dark) .26f else .16f }
                else if (hovered[stateIndex]) { if (dark) .20f else .10f } else if (dark) .12f else .06f,
                tween(150), label = "nav-state-alpha")
            if (hyper) Box(Modifier.offset(x=left).width(width).height(itemHeight).graphicsLayer {
                scaleX = (width.toPx()-6.dp.toPx()*press).coerceAtLeast(width.toPx()*.5f)/width.toPx()
                scaleY = (itemHeight.toPx()-6.dp.toPx()*press)/itemHeight.toPx()
            }.background((if (dark) Color.White else Color.Black).copy(alpha=alpha), RoundedCornerShape(itemHeight/2)))
            if (!hyper) labels.indices.forEach { i ->
                if (pressed[i] || hovered[i]) Box(Modifier.offset(x=stride*i-8.dp).width(cell+16.dp).height(itemHeight)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha=.08f),CircleShape))
            }
            val latestLeft by rememberUpdatedState(with(density) { left.toPx() })
            val latestRight by rememberUpdatedState(with(density) { (left+width).toPx() })
            var previousTime by remember { mutableStateOf(0L) }
            Row(Modifier.fillMaxWidth().then(if (hyper) Modifier.pointerInput(stridePx) {
                detectHorizontalDragGestures(onDragStart = { point ->
                    dragging = point.x in latestLeft..latestRight
                    if (dragging) { physics.beginDrag(); previousTime = android.os.SystemClock.uptimeMillis(); revision++ }
                }, onDragCancel = { dragging=false; physics.select(latestTarget); revision++ }, onDragEnd = {
                    if (dragging) { val selected=physics.dragPosition.roundToInt().coerceIn(0,labels.lastIndex)
                        physics.select(selected); latestSelected(selected) }
                    dragging=false; revision++
                }) { change, delta ->
                    if (dragging) { change.consume(); physics.dragBy(delta/stridePx,(change.uptimeMillis-previousTime)/1000f)
                        previousTime=change.uptimeMillis; revision++ }
                }
            } else Modifier), horizontalArrangement=Arrangement.spacedBy(gap)) {
                labels.forEachIndexed { i,label ->
                    val selected=i==index
                    val ink by animateColorAsState((if(dark) Color.White else Color.Black).copy(alpha=if(selected) .9f else if(hyper) .6f else .72f),tween(180),label="tab-ink")
                    val glyph=listOf(SystemGlyph.Home,SystemGlyph.Records,SystemGlyph.Settings).getOrNull(i)
                    Column(Modifier.width(cell).height(itemHeight).clip(CircleShape)
                        .selectable(selected,interactionSource=sources[i],indication=null,role=Role.Tab) { onSelected(i) },
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(if(hyper) 0.dp else 2.dp,Alignment.CenterVertically)) {
                        if (hyper || glyph==null) Icon(icons[i],null,Modifier.size(if(hyper) 28.dp else 24.dp),tint=ink)
                        else Crossfade(selected,Modifier.size(24.dp),animationSpec=tween(180),label="tab-icon") { active ->
                            Icon(if(active) selectedNavigationGlyphs.getValue(glyph) else glyph.vector(),null,Modifier.size(24.dp),tint=ink)
                        }
                        Text(label,color=ink,fontSize=if(hyper) 11.sp else 10.sp,lineHeight=if(hyper) 14.sp else 12.sp,
                            maxLines=if(large) 2 else 1,fontWeight=FontWeight.Medium)
                    }
                }
            }
        }
    }
}

internal enum class SystemGlyph { Home, Records, Settings, Language, Download, Folder, Shield, Appearance, Font, Sync, Info, Chevron, Check, Play, Back }

private val systemGlyphs = SystemGlyph.entries.associateWith { glyph ->
    ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (glyph) {
                SystemGlyph.Home -> { moveTo(3f,10.5f); lineTo(12f,3f); lineTo(21f,10.5f); moveTo(5.5f,9f); lineTo(5.5f,20f); lineTo(9.5f,20f); lineTo(9.5f,14f); lineTo(14.5f,14f); lineTo(14.5f,20f); lineTo(18.5f,20f); lineTo(18.5f,9f) }
                SystemGlyph.Records -> { moveTo(7f,3.5f); lineTo(18f,3.5f); quadTo(20f,3.5f,20f,5.5f); lineTo(20f,19f); quadTo(20f,21f,18f,21f); lineTo(6f,21f); quadTo(4f,21f,4f,19f); lineTo(4f,6f); moveTo(8f,8f); lineTo(16f,8f); moveTo(8f,12f); lineTo(16f,12f); moveTo(8f,16f); lineTo(13f,16f) }
                SystemGlyph.Settings -> { moveTo(12f,2.5f); lineTo(20.2f,7.25f); lineTo(20.2f,16.75f); lineTo(12f,21.5f); lineTo(3.8f,16.75f); lineTo(3.8f,7.25f); close(); moveTo(15.7f,12f); arcTo(3.7f,3.7f,0f,true,true,8.3f,12f); arcTo(3.7f,3.7f,0f,true,true,15.7f,12f) }
                SystemGlyph.Language -> { moveTo(3f,5f); lineTo(14f,5f); moveTo(8.5f,3f); lineTo(8.5f,5f); moveTo(12f,5f); quadTo(11f,13f,3f,17f); moveTo(5f,8f); quadTo(7f,13f,12f,15f); moveTo(12f,21f); lineTo(17f,10f); lineTo(22f,21f); moveTo(14f,17f); lineTo(20f,17f) }
                SystemGlyph.Download -> { moveTo(12f,3f); lineTo(12f,15f); moveTo(7.5f,10.5f); lineTo(12f,15f); lineTo(16.5f,10.5f); moveTo(4f,16f); lineTo(4f,20f); lineTo(20f,20f); lineTo(20f,16f) }
                SystemGlyph.Folder -> { moveTo(3f,7f); lineTo(3f,19f); quadTo(3f,21f,5f,21f); lineTo(19f,21f); quadTo(21f,21f,21f,19f); lineTo(21f,8f); quadTo(21f,6f,19f,6f); lineTo(12f,6f); lineTo(10f,3.5f); lineTo(5f,3.5f); quadTo(3f,3.5f,3f,5.5f); lineTo(3f,9f); lineTo(21f,9f) }
                SystemGlyph.Shield -> { moveTo(12f,2.5f); lineTo(20f,6f); lineTo(19.2f,14f); quadTo(18f,19f,12f,22f); quadTo(6f,19f,4.8f,14f); lineTo(4f,6f); close(); moveTo(8f,12f); lineTo(11f,15f); lineTo(16f,9.5f) }
                SystemGlyph.Appearance -> { moveTo(4f,4f); lineTo(20f,4f); lineTo(20f,20f); lineTo(4f,20f); close(); moveTo(12f,4f); lineTo(12f,20f); moveTo(16f,8f); lineTo(16f,16f) }
                SystemGlyph.Font -> { moveTo(4f,5f); lineTo(20f,5f); moveTo(12f,5f); lineTo(12f,20f); moveTo(8f,20f); lineTo(16f,20f); moveTo(4f,5f); lineTo(4f,8f); moveTo(20f,5f); lineTo(20f,8f) }
                SystemGlyph.Sync -> { moveTo(4f,9f); quadTo(6f,3f,12f,3f); quadTo(17f,3f,20f,7f); moveTo(20f,3f); lineTo(20f,8f); lineTo(15f,8f); moveTo(20f,15f); quadTo(18f,21f,12f,21f); quadTo(7f,21f,4f,17f); moveTo(4f,21f); lineTo(4f,16f); lineTo(9f,16f) }
                SystemGlyph.Info -> { moveTo(21f,12f); arcTo(9f,9f,0f,true,true,3f,12f); arcTo(9f,9f,0f,true,true,21f,12f); moveTo(12f,11f); lineTo(12f,17f); moveTo(12f,7f); lineTo(12f,7.1f) }
                SystemGlyph.Chevron -> { moveTo(9f,6f); lineTo(15f,12f); lineTo(9f,18f) }
                SystemGlyph.Check -> { moveTo(5f,12f); lineTo(10f,17f); lineTo(19f,7f) }
                SystemGlyph.Play -> { moveTo(9f,5f); lineTo(19f,12f); lineTo(9f,19f); close() }
                SystemGlyph.Back -> { moveTo(14.5f,5f); lineTo(7.5f,12f); lineTo(14.5f,19f) }
            }
        }
    }.build()
}
internal fun SystemGlyph.vector(): ImageVector = systemGlyphs.getValue(this)

internal fun preferenceGlyph(title: String): SystemGlyph? = when {
    "運作模式" in title || "作用域" in title || "環境" in title -> SystemGlyph.Shield
    "語言" in title -> SystemGlyph.Language
    "匯入" in title || "下載" in title -> SystemGlyph.Download
    "字型" in title -> SystemGlyph.Font
    "PC" in title || "更新" in title -> SystemGlyph.Sync
    "介面" in title || "風格" in title || "深淺" in title -> SystemGlyph.Appearance
    "位置" in title || "載入方式" in title -> SystemGlyph.Folder
    title == "關於" -> SystemGlyph.Info
    else -> null
}

@Composable internal fun SystemLogActions(busy: Boolean, share: () -> Unit, save: () -> Unit, clear: () -> Unit) {
    SystemCard(insideMargin = PaddingValues(16.dp)) {
        SystemButton(share, Modifier.fillMaxWidth(), !busy) {
            Text(if (busy) "正在收集診斷…" else "分享診斷文件", fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemTextButton("儲存", save, Modifier.weight(1f), !busy)
            SystemTextButton("清除", clear, Modifier.weight(1f), !busy)
        }
    }
}

@Composable internal fun SystemLogEntry(event: LogEvent) {
    val ink = when (event.level) { "ERROR" -> Color(if (LocalSystemUiDark.current) 0xFFED8891 else 0xFFB73845)
        "WARN" -> Color(if (LocalSystemUiDark.current) 0xFFE6B469 else 0xFF8F5A13); else -> MiuixTheme.colorScheme.primary }
    SystemCard(insideMargin = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(event.level, Modifier.clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 4.dp),
                color = ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(event.code, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(event.message, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(12.dp))
        val time = remember(event.time) { runCatching {
            java.time.Instant.parse(event.time).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
        }.getOrDefault(event.time) }
        Text(time + if (event.process.isBlank()) "" else "  ·  ${event.process}", fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

internal data class OverviewUiState(
    val name: String, val script: String, val language: String, val enabled: Boolean,
    val rootMode: Boolean, val gameInstalled: Boolean, val gameVersion: String, val busy: Boolean,
    val scopeStatus: String, val rootStatus: String, val loadStatus: String,
    val ready: Boolean, val fontReady: Boolean, val jsonFiles: Int, val coverage: String
)

@Composable internal fun SystemTranslationPanel(state: OverviewUiState, onEnabled: (Boolean) -> Unit,
    onLaunch: () -> Unit, onDownload: () -> Unit) {
    val selected = state.name != "尚未選擇"
    val accent = MiuixTheme.colorScheme.primary
    var showDetails by remember { mutableStateOf(false) }
    SystemCard {
        SystemSwitchPreference("啟用漢化", checked = state.enabled,
            enabled = !state.busy && (state.rootMode || state.enabled), onCheckedChange = onEnabled)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(if (selected) state.name else "尚未選擇漢化", fontSize = 18.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (selected) "${state.script} · 覆蓋${state.language}" else "先下載或匯入語言包，再套用漢化。",
                Modifier.padding(top = 5.dp), fontSize = 13.sp, lineHeight = 20.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val healthy = state.enabled && (!state.rootMode || state.ready && state.fontReady)
                Text(if (healthy) "漢化已啟用" else if (!state.enabled) "漢化已停用" else "需要重新套用",
                    Modifier.weight(1f), fontSize = 12.sp,
                    color = if (healthy) Color(if (LocalSystemUiDark.current) 0xFF75D5A8 else 0xFF29815D)
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("詳情", Modifier.clip(CircleShape).clickable(role = Role.Button) { showDetails = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary)
            }
            SystemButton(if (state.gameInstalled) onLaunch else onDownload, Modifier.fillMaxWidth(), enabled = !state.busy) {
                Text(if (state.gameInstalled) "啟動遊戲" else "取得漢化資源", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (showDetails) SystemModal("漢化詳情", { showDetails = false }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("遊戲版本：${state.gameVersion}", fontSize = 14.sp)
            Text("遊戲語言請設為「${state.language}」", fontSize = 14.sp)
            if (state.rootMode) {
                Text("已準備 ${state.jsonFiles} 個文字資源；${if (state.fontReady) "字型已準備" else "字型尚未準備"}", fontSize = 14.sp)
                Text(state.coverage, fontSize = 13.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else Text(state.loadStatus, fontSize = 14.sp)
        }
        SystemTextButton("完成", { showDetails = false }, Modifier.fillMaxWidth())
    }
}

@Composable internal fun SystemQuickActions(onDownload: () -> Unit, onDownloaded: () -> Unit) {
    SystemCard {
        SystemArrowPreference("下載漢化", onClick = onDownload)
        SystemArrowPreference("已下載漢化", onClick = onDownloaded)
    }
}

@Composable internal fun SystemEnvironmentPanel(state: OverviewUiState, onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit, onGameFolder: () -> Unit, loadAction: String) {
    var expanded by remember { mutableStateOf(false) }
    val healthy = if (state.rootMode) state.scopeStatus == "已啟用" && state.rootStatus == "已授權" && state.gameInstalled else state.gameInstalled
    SystemCard {
        SystemArrowPreference(if (state.rootMode) "環境與權限" else "文件載入方式",
            value = if (healthy) "已就緒" else "待設定", onClick = { expanded = true })
    }
    if (expanded) SystemModal(if (state.rootMode) "環境與權限" else "文件載入", { expanded = false }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Limbus Company  ${state.gameVersion}", fontSize = 14.sp)
            if (state.rootMode) {
                Text("模組作用域：${state.scopeStatus}", fontSize = 14.sp)
                Text("Root 權限：${state.rootStatus}", fontSize = 14.sp)
            } else Text(state.loadStatus, fontSize = 14.sp)
        }
        if (state.rootMode) {
            SystemTextButton("檢查模組作用域", onCheckScope, Modifier.fillMaxWidth())
            SystemTextButton("檢查 Root 權限", onRequestRoot, Modifier.fillMaxWidth())
        } else SystemTextButton(loadAction, onGameFolder, Modifier.fillMaxWidth())
        SystemTextButton("完成", { expanded = false }, Modifier.fillMaxWidth())
    }
}

// Original LCPatch glyphs: filled active variants follow the supplied COUI tab states.
private val selectedNavigationGlyphs = listOf(SystemGlyph.Home, SystemGlyph.Records, SystemGlyph.Settings).associateWith { glyph ->
    ImageVector.Builder("Selected${glyph.name}", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            when (glyph) {
                SystemGlyph.Home -> { moveTo(2f,11f); lineTo(12f,2.5f); lineTo(22f,11f); lineTo(19.5f,11f); lineTo(19.5f,21.5f); lineTo(14.5f,21.5f); lineTo(14.5f,14f); lineTo(9.5f,14f); lineTo(9.5f,21.5f); lineTo(4.5f,21.5f); lineTo(4.5f,11f); close() }
                SystemGlyph.Records -> {
                    moveTo(5f,2.5f); lineTo(19f,2.5f); quadTo(21f,2.5f,21f,4.5f); lineTo(21f,19.5f); quadTo(21f,21.5f,19f,21.5f); lineTo(5f,21.5f); quadTo(3f,21.5f,3f,19.5f); lineTo(3f,4.5f); quadTo(3f,2.5f,5f,2.5f); close()
                    for (y in listOf(7f,11f,15f)) { moveTo(7f,y); lineTo(17f,y); lineTo(17f,y+1.7f); lineTo(7f,y+1.7f); close() }
                }
                SystemGlyph.Settings -> {
                    moveTo(12f,2.5f); lineTo(20.2f,7.25f); lineTo(20.2f,16.75f); lineTo(12f,21.5f); lineTo(3.8f,16.75f); lineTo(3.8f,7.25f); close()
                    moveTo(15.7f,12f); arcTo(3.7f,3.7f,0f,true,true,8.3f,12f); arcTo(3.7f,3.7f,0f,true,true,15.7f,12f); close()
                }
                else -> Unit
            }
        }
    }.build()
}

/** Shared action states preserve readable text at large system font sizes. */
@Composable internal fun SystemProgressActionButton(text: String, progress: Float? = null, busy: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val fraction = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) 16.dp else 50.dp)
    val ink = MiuixTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
        .background(if (busy) primary.copy(alpha=.16f).compositeOver(MiuixTheme.colorScheme.surfaceContainer)
            else if (enabled) primary else ink.copy(alpha=.08f))
        .semantics {
            role = Role.Button
            if (busy) {
                stateDescription = "處理中"
                progressBarRangeInfo = if (fraction == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(fraction, 0f..1f)
            }
        }.clickable(enabled = enabled && !busy, onClick = onClick), Alignment.Center) {
        if (busy && fraction != null && fraction > 0f) Box(Modifier.matchParentSize().drawBehind {
            drawRect(primary.copy(alpha=.24f), size=androidx.compose.ui.geometry.Size(size.width*fraction,size.height))
        })
        Text(text, Modifier.padding(horizontal=16.dp,vertical=12.dp), color=if (busy) ink else if (enabled) Color.White else ink.copy(alpha=.38f),
            fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable internal fun SystemNotice(message: String, error: Boolean, retry: (() -> Unit)?, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    SystemCard(modifier.widthIn(max=480.dp).fillMaxWidth(), insideMargin=PaddingValues(16.dp)) {
        Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState())) {
            Text(message, Modifier.semantics { liveRegion=LiveRegionMode.Polite }, fontSize=14.sp,lineHeight=22.sp)
            if (error) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)) {
                if (retry != null) SystemTextButton("重試",retry)
                SystemTextButton("關閉",dismiss)
            }
        }
    }
}

/** Ordinary second-level COUI toolbar uses 18sp medium, distinct from the 24sp collapsed main title. */
@Composable internal fun SystemColorTopBar(title: String, navigationIcon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),Alignment.Center) { navigationIcon() }
        Text(title,Modifier.weight(1f).padding(start=8.dp,end=16.dp,top=12.dp,bottom=12.dp),fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable internal fun SystemDisplaySettingsPanel(
        themeMode: String,
        systemUiStyle: SystemUiStyle,
        onSystemUiStyle: (SystemUiStyle) -> Unit,
        navigationStyle: String,
        onNavigationStyle: (String) -> Unit,
        blurEnabled: Boolean,
        onBlurEnabled: (Boolean) -> Unit,
        hazeRendererEnabled: Boolean,
        onHazeRendererEnabled: (Boolean) -> Unit,
        onThemeMode: (String) -> Unit
    ) {
    SystemCard {
                SystemStylePicker(systemUiStyle, onSystemUiStyle)
                SystemDropdownPreference(
                    modifier = Modifier,
                    title = "深淺模式",
                    items = listOf("跟隨系統", "淺色", "深色"),
                    selectedIndex = when (themeMode) { "light" -> 1; "dark" -> 2; else -> 0 },
                    onSelectedIndexChange = { onThemeMode(when (it) { 1 -> "light"; 2 -> "dark"; else -> "system" }) }
                )
                SystemDropdownPreference(
                    modifier = Modifier,
                    title = "底欄樣式",
                    items = listOf("標準底欄", "懸浮底欄"),
                    selectedIndex = if (navigationStyle == "floating") 1 else 0,
                    onSelectedIndexChange = { onNavigationStyle(if (it == 1) "floating" else "standard") }
                )
                SystemSwitchPreference(
                    modifier = Modifier,
                    title = "背景模糊",
                    summary = if (blurEnabled) "頂欄、底欄與面板使用模糊效果" else "使用清晰的純色背景",
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabled
                )
                SystemSwitchPreference(
                    modifier = Modifier,
                    title = "相容模糊",
                    summary = if (hazeRendererEnabled) "使用較柔和的模糊；效果不合適時可以關閉"
                        else "使用標準玻璃效果",
                    enabled = blurEnabled,
                    checked = hazeRendererEnabled,
                    onCheckedChange = onHazeRendererEnabled
                )

    }
}
