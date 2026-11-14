package com.lcpatch

import androidx.compose.animation.animateColorAsState
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
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.roundToInt

internal enum class SystemUiStyle(val id: String, val label: String) {
    HyperOS4("hyperos4", "柔光玻璃"), ColorOS17("coloros17", "Liquid Acrylic");
    companion object { fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: HyperOS4 }
}
internal val LocalSystemUiStyle = staticCompositionLocalOf { SystemUiStyle.HyperOS4 }
internal val LocalSystemUiDark = staticCompositionLocalOf { false }
private val LocalCardInset = staticCompositionLocalOf { 0.dp }
private val LocalGrouped = staticCompositionLocalOf { false }

@Composable internal fun SystemUiTheme(style: SystemUiStyle, themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = remember(style, dark) {
        val soft = style == SystemUiStyle.HyperOS4
        val accent = Color(if (dark) 0xFF76AEFF else if (soft) 0xFF126AE5 else 0xFF006CE5)
        val canvas = Color(if (dark) { if (soft) 0xFF0E0F12 else 0xFF111315 } else if (soft) 0xFFF3F4F7 else 0xFFF5F6F7)
        val card = Color(if (dark) { if (soft) 0xFF202126 else 0xFF232629 } else 0xFFFFFFFF)
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = accent, onPrimary = if (dark) Color(0xFF0D1728) else Color.White,
            primaryVariant = accent, primaryContainer = accent.copy(alpha = 0.11f), onPrimaryContainer = accent,
            secondaryContainer = card, background = canvas, surface = canvas, surfaceContainer = card,
            onSurface = Color(if (dark) 0xFFF2F3F6 else 0xFF17191E),
            onBackground = Color(if (dark) 0xFFF2F3F6 else 0xFF17191E),
            onSurfaceVariantSummary = Color(if (dark) 0xFFADB0B8 else 0xFF70747E),
            dividerLine = Color(if (dark) 0xFF35373D else 0xFFEEEFF2))
    }
    CompositionLocalProvider(LocalSystemUiStyle provides style, LocalSystemUiDark provides dark) {
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}

@Composable internal fun SystemCanvas(content: @Composable BoxScope.() -> Unit) {
    val base = MiuixTheme.colorScheme.surface
    val dark = LocalSystemUiDark.current
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        if (dark) base else Color(if (soft) 0xFFEEF3F9 else 0xFFEEF5F7), base, base
    ))), content = content)
}

/** Content panels stay quiet; glass is reserved for floating controls. */
@Composable internal fun SystemCard(modifier: Modifier = Modifier, insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    cornerRadius: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    val radius = if (cornerRadius != Dp.Unspecified) cornerRadius else if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) 22.dp else 20.dp
    val shape = RoundedCornerShape(radius)
    CompositionLocalProvider(LocalCardInset provides insideMargin.calculateLeftPadding(LayoutDirection.Ltr), LocalGrouped provides true,
        LocalContentColor provides colors.contentColor) {
        Column(modifier.fillMaxWidth().clip(shape).background(colors.color).padding(insideMargin), content = content)
    }
}

@Composable internal fun SystemPageHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
        Text(title, fontSize = 34.sp, lineHeight = 44.sp,
            fontWeight = if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) FontWeight.Bold else FontWeight.SemiBold,
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
    Box(modifier.size(30.dp).background(if (soft) ink else ink.copy(alpha = if (LocalSystemUiDark.current) 0.22f else 0.12f),
        RoundedCornerShape(if (soft) 9.dp else 10.dp)), contentAlignment = Alignment.Center) {
        Icon(glyph.vector(), null, Modifier.size(19.dp), tint = if (soft) Color.White else ink)
    }
}

@Composable private fun PreferenceRow(title: String, summary: String?, value: String?, modifier: Modifier,
    enabled: Boolean, trailing: @Composable () -> Unit, action: Modifier) {
    val inset = (16.dp - LocalCardInset.current).coerceAtLeast(0.dp)
    val glyph = preferenceGlyph(title)
    val divider = MiuixTheme.colorScheme.dividerLine
    val grouped = LocalGrouped.current
    Row(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).then(action)
        .drawBehind {
            if (grouped) {
                val left = inset.toPx() + if (glyph != null) 44.dp.toPx() else 0f
                drawLine(divider, Offset(left, size.height - 0.5f), Offset(size.width - inset.toPx(), size.height - 0.5f), 0.5.dp.toPx())
            }
        }.heightIn(min = 60.dp).padding(horizontal = inset, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) { SystemGlyphTile(glyph); Spacer(Modifier.width(13.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal)
            if (!summary.isNullOrBlank()) Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (value != null) {
            Text(value, Modifier.padding(start = 12.dp).widthIn(max = 124.dp), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp)); trailing()
    }
}

@Composable internal fun SystemArrowPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    PreferenceRow(title, summary?.takeIf { it.length > 18 }, summary?.takeIf { it.length <= 18 }, modifier, enabled,
        trailing = { Icon(SystemGlyph.Chevron.vector(), null, Modifier.size(15.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.65f)) },
        action = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))
}

@Composable internal fun SystemToggle(checked: Boolean, enabled: Boolean = true) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val position by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 700f), label = "switch-position")
    val fill by animateColorAsState(if (checked) MiuixTheme.colorScheme.primary else
        Color(if (LocalSystemUiDark.current) 0xFF4C4F57 else 0xFFD9DCE2), label = "switch-fill")
    Box(Modifier.size(if (soft) 47.dp else 45.dp, 28.dp).clip(CircleShape).background(fill.copy(alpha = if (enabled) 1f else 0.45f))) {
        Box(Modifier.offset(x = 3.dp + (position * if (soft) 19 else 17).dp, y = 3.dp)
            .size(22.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable internal fun SystemSwitchPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    PreferenceRow(title, summary, null, modifier, enabled, trailing = { SystemToggle(checked, enabled) },
        action = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange))
}

@Composable internal fun SystemButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, spring(0.9f, 800f), label = "button-press")
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val shape = RoundedCornerShape(if (soft) 16.dp else 50.dp)
    val fill = MiuixTheme.colorScheme.primary
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
        .background(if (enabled) fill else fill.copy(alpha = 0.22f))
        .then(if (!soft) Modifier.border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), shape) else Modifier)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 50.dp).padding(horizontal = 20.dp, vertical = 13.dp), Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.55f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

@Composable internal fun SystemTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), Alignment.Center) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable internal fun SystemModal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().clickable(onClick = onDismiss))
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(if (soft) 30.dp else 28.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer).clickable { }.padding(top = 10.dp, bottom = 10.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(32.dp, 4.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape))
                Text(title, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}

@Composable internal fun SystemDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    PreferenceRow(title, summary, items.getOrNull(selectedIndex), modifier, enabled,
        trailing = { Icon(SystemGlyph.Chevron.vector(), null, Modifier.size(15.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.65f)) },
        action = Modifier.clickable(enabled = enabled, role = Role.Button) { expanded = true })
    if (expanded) SystemModal(title, { expanded = false }) {
        Column(Modifier.weight(1f, fill = false).heightIn(max = 400.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
            items.forEachIndexed { i, value ->
                Row(Modifier.fillMaxWidth().selectable(i == selectedIndex, role = Role.RadioButton) {
                    onSelectedIndexChange(i); expanded = false
                }.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp,
                        color = if (i == selectedIndex) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    if (i == selectedIndex) Icon(SystemGlyph.Check.vector(), null, Modifier.size(22.dp), tint = MiuixTheme.colorScheme.primary)
                }
            }
        }
        SystemTextButton("取消", { expanded = false }, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    }
}

@Composable internal fun SystemStylePicker(selected: SystemUiStyle, onSelected: (SystemUiStyle) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("材質風格", fontSize = 16.sp, lineHeight = 24.sp)
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
                        .background(Brush.linearGradient(listOf(Color(if (soft) 0xFFD2E5F2 else 0xFFDCEBEA), Color(0xFFECF0F8))))) {
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

@Composable internal fun SystemBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, floating: Boolean,
    backdrop: Backdrop? = null, hazeState: HazeState? = null, targetIndex: Int = index, onSelected: (Int) -> Unit) {
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val dark = LocalSystemUiDark.current
    val shape = RoundedCornerShape(if (floating) 32.dp else 0.dp)
    val hazeStyle = remember { HazeBlurStyle { blurRadius(20.dp) } }
    val glass = when {
        hazeState != null -> Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
        backdrop != null -> Modifier.drawBackdrop(backdrop, shape = { shape }, effects = { blur(18.dp.toPx()); vibrancy() }, highlight = null, shadow = null)
        else -> Modifier
    }
    val tint = if (dark) Color(0xFF292C33) else Color(0xFFF9FAFC)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.85f),
        Color.White.copy(alpha = if (dark) 0.05f else 0.20f), Color.White.copy(alpha = if (dark) 0.12f else 0.65f)))
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = if (floating) 24.dp else 0.dp, vertical = if (floating) 8.dp else 0.dp), Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = if (floating) 304.dp else 600.dp).fillMaxWidth()
            .shadow(if (floating) 14.dp else 0.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape).then(glass).background(tint.copy(alpha = if (dark) 0.86f else if (soft) 0.78f else 0.86f))
            .then(if (floating) Modifier.border(if (soft) 1.dp else 0.65.dp, rim, shape) else Modifier).padding(5.dp)) {
            val cell = maxWidth / labels.size
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            var dragPosition by remember { mutableStateOf<Float?>(null) }
            val latestSelected by rememberUpdatedState(onSelected)
            val latestTarget by rememberUpdatedState(targetIndex)
            val position by animateFloatAsState(dragPosition ?: targetIndex.toFloat(), spring(0.88f, 600f), label = "navigation-selection")
            Box(Modifier.offset(x = cell * position).width(cell).height(54.dp).clip(CircleShape)
                .background(if (dark) Color.White.copy(alpha = if (soft) 0.10f else 0.07f)
                    else Color.Black.copy(alpha = if (soft) 0.055f else 0.035f)))
            Row(Modifier.fillMaxWidth().pointerInput(cellPx, labels.size) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPosition = latestTarget.toFloat() },
                    onDragCancel = { dragPosition = null },
                    onDragEnd = {
                        val target = (dragPosition ?: latestTarget.toFloat()).roundToInt().coerceIn(0, labels.lastIndex)
                        latestSelected(target); dragPosition = null
                    }
                ) { change, amount ->
                    change.consume()
                    dragPosition = ((dragPosition ?: latestTarget.toFloat()) + amount / cellPx).coerceIn(0f, labels.lastIndex.toFloat())
                }
            }) {
                labels.forEachIndexed { i, label ->
                    val selected = i == index
                    val ink = if (selected) { if (soft) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.primary }
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    val glyph = listOf(SystemGlyph.Home, SystemGlyph.Records, SystemGlyph.Settings).getOrNull(i)
                    Column(Modifier.weight(1f).heightIn(min = 54.dp).clip(CircleShape)
                        .selectable(selected, role = Role.Tab) { onSelected(i) }.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(glyph?.vector() ?: icons[i], null, Modifier.size(21.dp), tint = ink)
                        Text(label, color = ink, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
