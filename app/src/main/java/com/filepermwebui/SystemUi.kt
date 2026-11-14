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
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

internal enum class SystemGlyph { Home, Records, Settings, Language, Download, Folder, Shield, Appearance, Font, Sync, Info, Chevron, Check, Play, Back }

private val systemGlyphs = SystemGlyph.entries.associateWith { glyph ->
    ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (glyph) {
                SystemGlyph.Home -> { moveTo(3f,10.5f); lineTo(12f,3f); lineTo(21f,10.5f); moveTo(5.5f,9f); lineTo(5.5f,20f); lineTo(9.5f,20f); lineTo(9.5f,14f); lineTo(14.5f,14f); lineTo(14.5f,20f); lineTo(18.5f,20f); lineTo(18.5f,9f) }
                SystemGlyph.Records -> { moveTo(7f,3.5f); lineTo(18f,3.5f); quadTo(20f,3.5f,20f,5.5f); lineTo(20f,19f); quadTo(20f,21f,18f,21f); lineTo(6f,21f); quadTo(4f,21f,4f,19f); lineTo(4f,6f); moveTo(8f,8f); lineTo(16f,8f); moveTo(8f,12f); lineTo(16f,12f); moveTo(8f,16f); lineTo(13f,16f) }
                SystemGlyph.Settings -> { moveTo(9f,3f); lineTo(15f,3f); lineTo(15.7f,5.3f); lineTo(18f,6.6f); lineTo(20.2f,6f); lineTo(22f,11f); lineTo(20f,12.5f); lineTo(19.7f,15f); lineTo(20.8f,17f); lineTo(16.8f,20.5f); lineTo(14.7f,19.3f); lineTo(12f,20f); lineTo(10.5f,22f); lineTo(5.7f,20f); lineTo(6f,17.5f); lineTo(4.4f,15.4f); lineTo(2f,15f); lineTo(2f,9.5f); lineTo(4.3f,8.5f); lineTo(6f,6.5f); lineTo(6.3f,4f); close(); moveTo(15.5f,12.4f); arcTo(3.5f,3.5f,0f,true,true,8.5f,12.4f); arcTo(3.5f,3.5f,0f,true,true,15.5f,12.4f) }
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
            Icon(SystemGlyph.Records.vector(), null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(9.dp))
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
    SystemCard(insideMargin = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("目前漢化", Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Box(Modifier.size(48.dp, 40.dp).semantics { contentDescription = "啟用漢化" }
                .toggleable(state.enabled, enabled = !state.busy && (state.rootMode || state.enabled), role = Role.Switch, onValueChange = onEnabled),
                Alignment.Center) { SystemToggle(state.enabled, !state.busy) }
        }
        Text(if (selected) state.name else "尚未選擇漢化", Modifier.padding(top = 3.dp),
            fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (selected) "${state.script}  ·  覆蓋${state.language}" else "先下載或匯入語言包，再套用漢化。",
            Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(Modifier.padding(top = 18.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            val healthy = state.enabled && (!state.rootMode || state.ready && state.fontReady)
            val color = if (healthy) Color(if (LocalSystemUiDark.current) 0xFF75D5A8 else 0xFF29815D) else MiuixTheme.colorScheme.onSurfaceVariantSummary
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(if (healthy) "漢化已啟用" else if (!state.enabled) "漢化已停用" else "需要重新套用", Modifier.padding(start = 7.dp).weight(1f),
                fontSize = 12.sp, color = color)
            Text("詳情", Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDetails = true }.padding(horizontal = 10.dp, vertical = 7.dp),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        SystemButton(if (state.gameInstalled) onLaunch else onDownload, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Icon(SystemGlyph.Play.vector(), null, Modifier.size(17.dp), tint = MiuixTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(if (state.gameInstalled) "啟動遊戲" else "取得漢化資源", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("下載漢化", SystemGlyph.Download, onDownload), Triple("已下載漢化", SystemGlyph.Folder, onDownloaded)).forEach { (title, glyph, action) ->
            SystemCard(Modifier.weight(1f).clickable(role = Role.Button, onClick = action), insideMargin = PaddingValues(17.dp)) {
                Icon(glyph.vector(), null, Modifier.size(23.dp), tint = MiuixTheme.colorScheme.primary)
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                    Icon(SystemGlyph.Chevron.vector(), null, Modifier.size(13.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable internal fun SystemEnvironmentPanel(state: OverviewUiState, onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit, onGameFolder: () -> Unit, loadAction: String) {
    var expanded by remember { mutableStateOf(false) }
    val healthy = if (state.rootMode) state.scopeStatus == "已啟用" && state.rootStatus == "已授權" && state.gameInstalled else state.gameInstalled
    SystemCard {
        SystemArrowPreference(if (state.rootMode) "環境與權限" else "文件載入方式",
            if (healthy) "已就緒" else "待設定", onClick = { expanded = true })
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
