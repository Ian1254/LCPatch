package com.lcpatch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

internal enum class SystemUiStyle(val id: String, val label: String) {
    HyperOS4("hyperos4", "柔光玻璃"), ColorOS17("coloros17", "Liquid Acrylic");
    companion object { fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: HyperOS4 }
}
internal val LocalSystemUiStyle = staticCompositionLocalOf { SystemUiStyle.HyperOS4 }
internal val LocalSystemUiDark = staticCompositionLocalOf { false }

/** Version-specific app chrome, shared by every page and transient surface. */
@Composable internal fun SystemUiTheme(style: SystemUiStyle, themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = remember(style, dark) {
        val hyper = style == SystemUiStyle.HyperOS4
        val accent = if (hyper) Color(if (dark) 0xFF82B3FF else 0xFF1769E8) else Color(if (dark) 0xFF75B8FF else 0xFF006CDB)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(primary = accent, onPrimary = if (dark) Color(0xFF101827) else Color.White,
            primaryContainer = accent.copy(alpha = if (dark) 0.22f else 0.11f),
            onPrimaryContainer = accent,
            background = Color(if (dark) 0xFF08090B else if (hyper) 0xFFF2F4F8 else 0xFFF5F6F8),
            surface = Color(if (dark) 0xFF08090B else if (hyper) 0xFFF2F4F8 else 0xFFF5F6F8),
            surfaceContainer = Color(if (dark) 0xFF1B1D21 else 0xFFFFFFFF),
            onSurface = Color(if (dark) 0xFFF4F5F8 else 0xFF15171B),
            onSurfaceVariantSummary = Color(if (dark) 0xFFB2B7C0 else 0xFF666B75),
            dividerLine = Color(if (dark) 0xFF30333A else 0xFFE9EBEF))
    }
    CompositionLocalProvider(LocalSystemUiStyle provides style, LocalSystemUiDark provides dark) {
        MiuixTheme(colors = colors, content = content)
    }
}

@Composable internal fun SystemCard(modifier: Modifier = Modifier, insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(), cornerRadius: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit) {
    val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val radius = if (cornerRadius != Dp.Unspecified) cornerRadius else if (hyper) 28.dp else 22.dp
    val shape = RoundedCornerShape(radius)
    MiuixCard(modifier = modifier.then(if (hyper) Modifier.border(0.7.dp,
        Color.White.copy(alpha = if (LocalSystemUiDark.current) 0.09f else 0.7f), shape) else Modifier),
        cornerRadius = radius, insideMargin = insideMargin, colors = colors, content = content)
}

@Composable internal fun SystemButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val accent = MiuixTheme.colorScheme.primary
    val shape = RoundedCornerShape(if (hyper) 20.dp else 100.dp)
    val fill = if (enabled) accent else accent.copy(alpha = 0.3f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.85f, stiffness = if (hyper) 650f else 450f), label = "system-button-press")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(if (hyper) Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0.87f)))
        else Brush.verticalGradient(listOf(fill, fill)))
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 52.dp)
        .padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides MiuixTheme.colorScheme.onPrimary) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable private fun PreferenceLabel(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
        if (!summary.isNullOrBlank()) Text(summary, fontSize = 13.sp, lineHeight = 19.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemArrowPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.alpha(if (enabled) 1f else 0.45f).fillMaxWidth().heightIn(min = 68.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        PreferenceLabel(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("›", fontSize = 25.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemSwitchPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val position by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.8f), label = "switch-position")
    val track by animateColorAsState(if (checked) MiuixTheme.colorScheme.primary else
        if (LocalSystemUiDark.current) Color(0xFF44464D) else Color(0xFFDADDE3), label = "switch-color")
    Row(modifier.alpha(if (enabled) 1f else 0.45f).fillMaxWidth().heightIn(min = 72.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        PreferenceLabel(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(if (hyper) 52.dp else 48.dp, 30.dp).clip(CircleShape)
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))) {
            Box(Modifier.padding(3.dp).offset(x = (position * if (hyper) 22 else 18).dp)
                .size(24.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable internal fun SystemDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    summary: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    SystemArrowPreference(title, summary ?: items.getOrNull(selectedIndex), modifier, enabled) { expanded = true }
    if (expanded) {
        val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { expanded = false },
                contentAlignment = if (hyper) Alignment.Center else Alignment.BottomCenter) {
                SystemCard(Modifier.padding(20.dp).widthIn(max = 480.dp).fillMaxWidth().clickable { },
                    insideMargin = PaddingValues(vertical = 16.dp)) {
                    Text(title, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        items.forEachIndexed { index, value ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(index == selectedIndex, role = Role.RadioButton) {
                                    onSelectedIndexChange(index); expanded = false
                                }.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(value, modifier = Modifier.weight(1f), fontSize = 16.sp,
                                    color = if (index == selectedIndex) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                                if (index == selectedIndex) Text("✓", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    SystemTextButton("取消", { expanded = false }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable internal fun SystemTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
        .background(MiuixTheme.colorScheme.primary.copy(alpha = if (LocalSystemUiStyle.current == SystemUiStyle.HyperOS4) 0.08f else 0.05f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), Alignment.Center) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable internal fun SystemBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>,
    floating: Boolean, backdrop: Backdrop? = null, hazeState: HazeState? = null, onSelected: (Int) -> Unit) {
    val hyper = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    val shape = RoundedCornerShape(if (hyper) 32.dp else 28.dp)
    val hazeStyle = remember { HazeBlurStyle { blurRadius(18.dp) } }
    val glass = when {
        hazeState != null -> Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
        backdrop != null -> Modifier.drawBackdrop(backdrop = backdrop, shape = { shape },
            effects = { blur(18.dp.toPx()) }, highlight = null, shadow = null)
        else -> Modifier
    }
    Row(Modifier.navigationBarsPadding().padding(horizontal = if (floating) 24.dp else 12.dp, vertical = 10.dp)
        .widthIn(max = 520.dp).fillMaxWidth().clip(shape)
        .then(glass)
        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (backdrop != null || hazeState != null) 0.72f else 0.96f))
        .border(0.7.dp, if (hyper) Color.White.copy(alpha = 0.18f) else MiuixTheme.colorScheme.dividerLine, shape)
        .padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEachIndexed { item, label ->
            val selected = item == index
            val fill by animateColorAsState(if (selected) (if (hyper) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.primary).copy(alpha = 0.08f)
                else Color.Transparent, label = "navigation-fill")
            val ink = if (selected) { if (hyper) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.primary }
                else MiuixTheme.colorScheme.onSurfaceVariantSummary
            Column(Modifier.weight(1f).clip(RoundedCornerShape(if (hyper) 27.dp else 20.dp)).background(fill)
                .selectable(selected, role = Role.Tab) { onSelected(item) }
                .padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icons[item], label, Modifier.size(23.dp), tint = ink)
                Text(label, color = ink, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
