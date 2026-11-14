package com.lcpatch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.layout.onSizeChanged
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
    HyperOS4("hyperos4", "hyper");
    companion object {
        fun fromId(value: String?) = entries.firstOrNull { it.id == value || it.label == value } ?: HyperOS4
    }
}
internal val LocalSystemUiStyle = staticCompositionLocalOf { SystemUiStyle.HyperOS4 }
internal val LocalSystemUiDark = staticCompositionLocalOf { false }
private val LocalCardInset = staticCompositionLocalOf { 0.dp }
private val LocalGrouped = staticCompositionLocalOf { false }

internal data class SystemSurfacePalette(val menu: Color,val sheet: Color,val dock: Color,val selected: Color,val actionInk: Color,val outline: Color,val secondaryButton: Color)
@Composable internal fun systemSurfacePalette(): SystemSurfacePalette {
    val dark=LocalSystemUiDark.current
    val menu=Color(if(dark) 0xFF38383C else 0xFFEDF2FA)
    return SystemSurfacePalette(menu,Color(if(dark) 0xFF2D2D31 else 0xFFFFFFFF),Color(if(dark) 0xFF151518 else 0xFFF1F3F7),
        MiuixTheme.colorScheme.primary.copy(alpha=if(dark) .26f else .17f).compositeOver(menu),
        Color(if(dark) 0xFF9CC2FF else 0xFF1B5FC7),
        (if(dark) Color.White else Color.Black).copy(alpha=if(dark) .18f else .14f),
        Color(if(dark) 0xFF45454B else 0xFFE8EBF1))
}

@Composable internal fun SystemUiTheme(style: SystemUiStyle, themeMode: String, content: @Composable () -> Unit) {
    val dark = when(themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val controller = remember(themeMode) { ThemeController(colorSchemeMode = when(themeMode) {
        "dark" -> ColorSchemeMode.Dark; "light" -> ColorSchemeMode.Light; else -> ColorSchemeMode.System
    }) }
    CompositionLocalProvider(LocalSystemUiStyle provides SystemUiStyle.HyperOS4, LocalSystemUiDark provides dark) {
        MiuixTheme(controller=controller) {
            CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) { SystemWindowSurface(content) }
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
    colors: CardColors = CardDefaults.defaultColors(color=MiuixTheme.colorScheme.surfaceContainer),
    cornerRadius: Dp = Dp.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    val radius=if(cornerRadius==Dp.Unspecified) CardDefaults.CornerRadius else cornerRadius
    CompositionLocalProvider(LocalCardInset provides insideMargin.calculateLeftPadding(LayoutDirection.Ltr), LocalGrouped provides true,
        LocalContentColor provides colors.contentColor) {
        MiuixCard(modifier.fillMaxWidth().clip(RoundedCornerShape(radius)), cornerRadius=radius,
            insideMargin=insideMargin,colors=colors,content=content)
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
    PreferenceRow(title,summary?:value,null,modifier,enabled,
        trailing={ Icon(SystemGlyph.Chevron.vector(),null,Modifier.size(18.dp),tint=MiuixTheme.colorScheme.onSurfaceVariantSummary) },
        action=Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick))
}

@Composable internal fun SystemToggle(checked: Boolean, enabled: Boolean = true) {
    MiuixSwitch(checked=checked,onCheckedChange=null,enabled=enabled,modifier=Modifier.clearAndSetSemantics {})
}

@Composable internal fun SystemSwitchPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    PreferenceRow(title,summary,null,modifier,enabled,trailing={ SystemToggle(checked,enabled) },
        action=Modifier.toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange))
}

@Composable internal fun SystemButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    MiuixButton(onClick,modifier.heightIn(min=48.dp),enabled,colors=ButtonDefaults.buttonColorsPrimary(),content=content)
}

@Composable internal fun SystemSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette=systemSurfacePalette()
    val shape=RoundedCornerShape(16.dp)
    Box(modifier.heightIn(min=48.dp).clip(shape).background(palette.secondaryButton).border(.8.dp,palette.outline,shape)
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),Alignment.Center) {
        Text(text,fontSize=16.sp,lineHeight=24.sp,color=MiuixTheme.colorScheme.onSurface.copy(alpha=if(enabled) 1f else .38f))
    }
}

@Composable internal fun SystemTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val actionInk=systemSurfacePalette().actionInk
    val source=remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.heightIn(min=48.dp).widthIn(min=48.dp).clip(RoundedCornerShape(8.dp))
        .background(if(pressed) MiuixTheme.colorScheme.primary.copy(alpha=.10f) else Color.Transparent)
        .clickable(interactionSource=source,indication=null,enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(horizontal=16.dp,vertical=12.dp),Alignment.Center) {
        Text(text,fontSize=16.sp,lineHeight=24.sp,color=actionInk.copy(alpha=if(enabled) 1f else .38f))
    }
}

@Composable internal fun SystemModal(title: String, onDismiss: () -> Unit, alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val scope=rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density=LocalDensity.current
    val threshold=with(density) { 56.dp.toPx() }
    val movement by animateFloatAsState(dragOffset,if(dragging) snap() else spring(1f,600f),label="sheet-offset")
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { shown=true }
    val close: ()->Unit = {
        if(!closing) { closing=true; scope.launch { shown=false; delay(200); latestDismiss() } }
    }
    val grip=Modifier.draggable(rememberDraggableState { delta ->
        dragOffset=(dragOffset+delta).coerceIn(-threshold*1.5f,threshold*8f)
    },Orientation.Vertical,enabled=!alert&&!closing,onDragStarted={ dragging=true; dragOffset=movement },onDragStopped={ velocity ->
        dragging=false
        when(sheetDragOutcome(expanded,dragOffset/threshold,velocity)) {
            SheetDragOutcome.Expand -> expanded=true
            SheetDragOutcome.Collapse -> expanded=false
            SheetDragOutcome.Dismiss -> close()
            SheetDragOutcome.Keep -> Unit
        }
        dragOffset=0f
    })
    val scene=LocalSystemWindowBackdrop.current.takeIf { LocalSystemMaterialEnabled.current }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=if(LocalSystemUiDark.current) .42f else .26f))
            .clickable(indication=null,interactionSource=remember { MutableInteractionSource() },onClick=close)) {
            AnimatedVisibility(shown,Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .offset { IntOffset(0,movement.roundToInt()) },
                enter=fadeIn(tween(200))+slideInVertically(tween(250)) { it/3 },
                exit=fadeOut(tween(180))+slideOutVertically(tween(200)) { it/3 }) {
                CompositionLocalProvider(LocalSystemPanelBackdrop provides scene) {
                    SystemSheetSurface(title,Modifier.padding(12.dp),alert,expanded,
                        onToggle={ expanded=!expanded },gripModifier=grip,onClose=close,content=content)
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
    expanded: Boolean = true, onToggle: (() -> Unit)? = null, gripModifier: Modifier = Modifier, onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val palette=systemSurfacePalette()
    val available=LocalConfiguration.current.screenHeightDp.dp*.88f
    val shape=RoundedCornerShape(32.dp)
    val scene=LocalSystemPanelBackdrop.current.takeIf { LocalSystemMaterialEnabled.current }
    val glass=if(scene!=null) Modifier.hyperGlassBackdrop(scene,shape,32.dp) else Modifier
    Column(modifier.widthIn(max=480.dp).fillMaxWidth()
        .animateContentSize(if(android.animation.ValueAnimator.areAnimatorsEnabled()) spring(1f,500f) else snap())
        .heightIn(max=available).shadow(18.dp,shape).clip(shape).then(glass)
        .background(palette.sheet.copy(alpha=if(scene!=null) .94f else 1f)).border(.8.dp,palette.outline,shape)
        .clickable(indication=null,interactionSource=remember { MutableInteractionSource() }) { }) {
        if(!alert) Box(Modifier.fillMaxWidth().height(48.dp).then(gripModifier)
            .then(if(onToggle!=null) Modifier.clickable(role=Role.Button,onClick=onToggle).semantics {
                contentDescription=if(expanded) "收合面板" else "展開面板"
                stateDescription=if(expanded) "已展開" else "已收合"
                if(onClose!=null) customActions=listOf(CustomAccessibilityAction("關閉面板") { onClose(); true })
            } else Modifier),Alignment.Center) {
            Box(Modifier.size(40.dp,4.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha=.45f),CircleShape))
        }
        Text(title,Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=if(alert) 20.dp else 4.dp),
            fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
        if(expanded||alert) Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=12.dp),content=content)
        else Text("點擊橫條或向上滑動以展開",Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),
            fontSize=13.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemSelectionRows(items: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    SystemChoiceMenuSurface(items,selected,onSelected,Modifier.fillMaxWidth())
}

@Composable internal fun SystemChoiceMenuSurface(items: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette=systemSurfacePalette()
    // Only the outside of the whole group is rounded. Adjacent rows meet square.
    Column(modifier.widthIn(min=180.dp,max=320.dp).width(IntrinsicSize.Max).clip(RoundedCornerShape(16.dp))
        .background(palette.menu).border(.8.dp,palette.outline,RoundedCornerShape(16.dp)).selectableGroup()) {
        items.forEachIndexed { i,label ->
            val source=remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Row(Modifier.fillMaxWidth().heightIn(min=52.dp)
                .background(if(pressed) palette.selected.copy(alpha=.8f) else if(i==selected) palette.selected else Color.Transparent)
                .selectable(i==selected,interactionSource=source,indication=null,role=Role.RadioButton) { onSelected(i) }
                .padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f),fontSize=16.sp,lineHeight=24.sp,
                    fontWeight=if(i==selected) FontWeight.Medium else FontWeight.Normal,color=if(i==selected) palette.actionInk else MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.width(20.dp))
                Box(Modifier.size(20.dp),Alignment.Center) {
                    if(i==selected) Icon(SystemGlyph.Check.vector(),null,Modifier.size(20.dp),tint=palette.actionInk)
                }
            }
        }
    }
}

@Composable internal fun SystemDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null) {
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
        SystemArrowPreference(title=title,summary=summary?:items.getOrNull(selectedIndex),modifier=modifier,enabled=enabled,
            onClick={ expanded=true })
        if (expanded) Popup(popupPositionProvider = position, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            Box(Modifier.shadow(12.dp, RoundedCornerShape(16.dp)).heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                SystemChoiceMenuSurface(items, selectedIndex, { onSelectedIndexChange(it); expanded = false })
            }
        }
    }
}



@Composable internal fun SystemBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, floating: Boolean,
    backdrop: Backdrop? = null, hazeState: HazeState? = null, targetIndex: Int = index, onSelected: (Int) -> Unit) {
    if(!floating) { SystemStandardBottomNavigation(targetIndex,labels,icons,onSelected); return }
    val hyper = true
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
                        else Icon(glyph.vector(),null,Modifier.size(24.dp),tint=ink)
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
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp)) {
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
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(.5.dp).background(MiuixTheme.colorScheme.dividerLine))
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
    Column {
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

/** Full-width dock: no capsule, moving pill or floating spacing. */
@Composable internal fun SystemStandardBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, onSelected: (Int)->Unit) {
    val height=maxOf(64.dp,with(LocalDensity.current) { 18.sp.toDp() }+44.dp)
    val divider=MiuixTheme.colorScheme.dividerLine
    Box(Modifier.fillMaxWidth().background(systemSurfacePalette().dock).drawBehind {
        drawLine(divider,Offset.Zero,Offset(size.width,0f),1.dp.toPx())
    }.navigationBarsPadding(),Alignment.Center) {
        Row(Modifier.widthIn(max=600.dp).fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { i,label ->
                val active=i==index
                val ink=if(active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                Column(Modifier.weight(1f).height(height).selectable(active,role=Role.Tab) { onSelected(i) },
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Box(Modifier.width(24.dp).height(2.dp).background(if(active) ink else Color.Transparent))
                    Spacer(Modifier.height(5.dp))
                    Icon(icons[i],null,Modifier.size(26.dp),tint=ink)
                    Text(label,fontSize=12.sp,lineHeight=18.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,color=if(active) MiuixTheme.colorScheme.onSurface else ink)
                }
            }
        }
    }
}
