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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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

internal data class SystemSurfacePalette(val menu: Color,val sheet: Color,val dock: Color,val selected: Color,val actionInk: Color,val secondaryButton: Color)
@Composable internal fun systemSurfacePalette(): SystemSurfacePalette {
    val dark=LocalSystemUiDark.current
    val menu=Color(if(dark) 0xFF2C2C2C else 0xFFFFFFFF)
    return SystemSurfacePalette(menu,Color(if(dark) 0xFF242424 else 0xFFFFFFFF),Color(if(dark) 0xFF151518 else 0xFFF1F3F7),
        MiuixTheme.colorScheme.primary.copy(alpha=if(dark) .26f else .17f).compositeOver(menu),
        Color(if(dark) 0xFF9CC2FF else 0xFF1B5FC7),
        Color(if(dark) 0xFF434343 else 0xFFF0F0F0))
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

/** Released v1.2.2 secondary header components and layout. */
@Composable internal fun SystemInnerTopBar(title: String,onBack: ()->Unit,showBack: Boolean=true,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null) {
    top.yukonga.miuix.kmp.basic.SmallTopAppBar(title=title,color=Color.Transparent,
        scrollBehavior=scrollBehavior?:top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(),
        navigationIcon={
            if(showBack) top.yukonga.miuix.kmp.basic.IconButton(onClick=onBack) {
                Icon(MiuixIcons.Back,contentDescription="返回")
            }
        })
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
        }
        if (value != null) {
            Text(value, Modifier.padding(start = 12.dp).widthIn(max = 124.dp), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp)); trailing()
    }
}

@Composable internal fun SystemArrowPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, value: String? = null, onClick: () -> Unit) {
    PreferenceRow(title,summary,value,modifier,enabled,
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
    MiuixButton(onClick,modifier.heightIn(min=50.dp),enabled,cornerRadius=16.dp,colors=ButtonDefaults.buttonColorsPrimary(),content=content)
}

@Composable internal fun SystemSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette=systemSurfacePalette()
    val shape=RoundedCornerShape(16.dp)
    Box(modifier.heightIn(min=50.dp).clip(shape).background(palette.secondaryButton)
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
    val latestDismiss by rememberUpdatedState(onDismiss)
    val animate=android.animation.ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(Unit) { shown=true }
    val close: ()->Unit = {
        if(!closing) { closing=true; scope.launch { shown=false; if(animate) delay(200); latestDismiss() } }
    }
    val scene=LocalSystemWindowBackdrop.current.takeIf { LocalSystemMaterialEnabled.current }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=if(LocalSystemUiDark.current) .50f else .32f))
            .clickable(indication=null,interactionSource=remember { MutableInteractionSource() },onClick=close)) {
            AnimatedVisibility(shown,Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(),
                enter=fadeIn(tween(if(animate) 200 else 0))+slideInVertically(tween(if(animate) 250 else 0)) { it/3 },
                exit=fadeOut(tween(if(animate) 180 else 0))+slideOutVertically(tween(if(animate) 200 else 0)) { it/3 }) {
                CompositionLocalProvider(LocalSystemPanelBackdrop provides scene) {
                    SystemSheetSurface(title,Modifier.padding(12.dp),alert,content=content)
                }
            }
        }
    }
}

/** Dialog actions have their own inset and never paint a full-width footer. */
@Composable internal fun SystemDialogDone(onClick: () -> Unit, text: String = "完成") {
    Box(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp)) {
        SystemButton(onClick,Modifier.fillMaxWidth()) { Text(text,fontSize=16.sp) }
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

@Composable internal fun SystemSheetSurface(title: String, modifier: Modifier = Modifier, alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val palette=systemSurfacePalette()
    val available=LocalConfiguration.current.screenHeightDp.dp*.88f
    val shape=RoundedCornerShape(36.dp)
    val scene=LocalSystemPanelBackdrop.current.takeIf { LocalSystemMaterialEnabled.current && android.os.Build.VERSION.SDK_INT>=31 }
    val glass=if(scene!=null) Modifier.drawBackdrop(scene,shape={shape},effects={ blur(24.dp.toPx()) },highlight=null,shadow=null) else Modifier
    Column(modifier.widthIn(max=480.dp).fillMaxWidth().heightIn(max=available)
        .shadow(20.dp,shape).clip(shape).then(glass)
        .background(palette.sheet.copy(alpha=if(scene!=null) .92f else 1f))
        .clickable(indication=null,interactionSource=remember { MutableInteractionSource() }) { }) {
        Text(title,Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=28.dp,bottom=16.dp),
            fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=12.dp),content=content)
    }
}

/** Released v1.2.2 uses Miuix's overlay dropdown, including its value placement. */
@Composable internal fun SystemDropdownPreference(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null) {
    MiuixDropdownPreference(title=title,items=items,selectedIndex=selectedIndex,modifier=modifier,
        enabled=enabled,summary=summary,onSelectedIndexChange=onSelectedIndexChange)
}

@Composable internal fun SystemBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, floating: Boolean,
    backdrop: Backdrop? = null, hazeState: HazeState? = null, targetIndex: Int = index, onSelected: (Int) -> Unit) {
    if(!floating) { SystemStandardBottomNavigation(targetIndex,labels,icons,onSelected,backdrop,hazeState); return }
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
    val rim = if(dark) Brush.linearGradient(listOf(Color.White.copy(alpha=.4f),Color.White.copy(alpha=.1f),Color.White.copy(alpha=.2f)))
        else Brush.linearGradient(listOf(Color.Black.copy(alpha=.18f),Color.Black.copy(alpha=.08f),Color.Black.copy(alpha=.14f)))
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = if (!floating) inset else if (hyper) maxOf(24.dp,inset+8.dp) else inset+8.dp
    val sources = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val hovered = sources.map { it.collectIsHoveredAsState().value || it.collectIsFocusedAsState().value }
    Box(Modifier.fillMaxWidth().padding(start = if (floating) 24.dp else 0.dp, end = if (floating) 24.dp else 0.dp,
        top = if (floating && hyper) 20.dp else 0.dp, bottom = bottom), Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = if (floating) preferredWidth else 600.dp).fillMaxWidth()
            .shadow(if (floating) { if(dark) 10.dp else 14.dp } else 0.dp, shape, ambientColor = Color.Black.copy(alpha = if(dark) .18f else .24f), spotColor = Color.Black.copy(alpha = if(dark) .18f else .24f))
            .clip(shape).then(glass).background(tint)
            .then(if (floating) Modifier.border(if(dark) .8.dp else 1.dp,rim,shape) else Modifier)
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

internal enum class SystemGlyph { Home, Records, Settings, Language, Download, Folder, Shield, Appearance, Font, Sync, Info, Chevron, Check, Play, Back, Close }

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
                SystemGlyph.Close -> { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
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

@Composable internal fun SystemLogOverflow(busy: Boolean,share: ()->Unit,save: ()->Unit,clear: ()->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val ink=MiuixTheme.colorScheme.onSurface
    Box {
        Box(Modifier.size(48.dp).clip(CircleShape).alpha(if(busy) .4f else 1f)
            .semantics { contentDescription=if(busy) "正在收集診斷" else "更多日誌操作";stateDescription=if(expanded) "已展開" else "已收合" }
            .clickable(enabled=!busy,role=Role.Button) { expanded=true },Alignment.Center) {
            Box(Modifier.size(24.dp).drawBehind {
                repeat(3) { i -> drawCircle(ink,1.6.dp.toPx(),Offset(size.width/2, (5f+i*7f)*density)) }
            })
        }
        top.yukonga.miuix.kmp.overlay.OverlayListPopup(show=expanded,
            alignment=top.yukonga.miuix.kmp.basic.PopupPositionProvider.Align.TopEnd,
            onDismissRequest={expanded=false},renderInRootScaffold=true) {
            SystemLogMenuItems(busy) {index ->
                expanded=false
                when(index) {0->share();1->save();else->clear()}
            }
        }
    }
}

@Composable internal fun SystemLogMenuItems(busy: Boolean,onSelected: (Int)->Unit) {
    top.yukonga.miuix.kmp.basic.ListPopupColumn {
        listOf("分享診斷文件","儲存診斷文件","清除日誌").forEachIndexed {i,label ->
            top.yukonga.miuix.kmp.basic.DropdownImpl(text=label,optionSize=3,isSelected=false,index=i,
                dropdownColors=top.yukonga.miuix.kmp.basic.DropdownDefaults.dropdownColors(),
                enabled=!busy,dialogMode=false,onSelectedIndexChange=onSelected)
        }
    }
}

@Composable internal fun SystemLo…10871 tokens truncated…言文件",0,0))
        validateJsonDirectory(File(unpacked, "Localize/cn"))
        progress(ApplyProgress("正在建立文字索引",0,0))
        val indexed = writeTextAssetIndex(File(unpacked, "Localize/cn"))
        LogRepository.append(context, "INFO", "translation.textasset.index", "$indexed 個內建文字資源名稱已建立索引")
        progress(ApplyProgress("正在寫入遊戲目錄", 0, 0))
        val nativeAction = runtimeSwapCommand(File(unpacked, "Localize/cn").absolutePath)
        val command = "am force-stop $GAME_PACKAGE && $nativeAction"
        val result = executeRoot(command)
        require(result.success) { result.output.ifBlank { "root 套用失敗" } }
        val inspection = inspectRuntime()
        require(inspection.ready) { "遊戲快取驗證失敗：找不到 ${targetLanguage().prefix} 漢化文件" }
        require(inspection.fontReady) { "遊戲快取驗證失敗：中文字型未寫入" }
        progress(ApplyProgress("套用完成", 1, 1))
        prefs.edit().putString("active_name", displayName).putString("active_script", script).putString("active_version",installedVersion).apply()
        LogRepository.append(context, "INFO", "translation.applied", "已套用 $displayName")
        return displayName
    }

    fun activeVersion(): String? = prefs.getString(if(rootMode()) "active_version" else "native_active_version",null)

    private fun runtimeVersionEvidence(directory: String): Pair<String?,String?> {
        val metadata=RootShell.run("cat ${quote("$directory/$VERSION_MARKER")} 2>/dev/null || true",timeoutMs=15_000L,maxOutputChars=8192).output
        val coverage=RootShell.run("cat ${quote("$directory/.lcpatch-coverage.txt")} 2>/dev/null || true",timeoutMs=15_000L,maxOutputChars=8192).output
        return metadata.takeIf {it.isNotBlank()} to coverage.takeIf {it.isNotBlank()}
    }

    fun activeName(): String = prefs.getString(if (rootMode()) "active_name" else "native_active_name", "尚未選擇") ?: "尚未選擇"
    fun activeScript(): String = if (!rootMode()) "日文字形" else prefs.getString("active_script", null)?.takeIf { it in setOf("簡體", "繁體") }
        ?: inferScript(activeName())

    suspend fun downloaded(): List<DownloadedTranslation> = withContext(Dispatchers.IO) {
        if (!rootMode()) return@withContext localPacks.list().map { file ->
            val script = File(file, ".lcpatch-script").takeIf(File::isFile)?.readText()?.trim()
                ?.takeIf { it in setOf("簡體", "繁體") } ?: "日文字形"
            DownloadedTranslation(file.name, file.absolutePath, script, File(file, PUA_MARKER).isFile)
        }
        repairStorage()
        val result = executeRoot("find ${quote(PUBLIC_DOWNLOADS)} -mindepth 1 -maxdepth 1 -type d -print")
        if (!result.success) return@withContext emptyList()
        result.output.lineSequence().filter(String::isNotBlank).mapNotNull { original ->
            val oldName = File(original).name
            if (isLegacyRootSimplified(oldName) && oldName != activeName()) {
                if (runRoot("rm -rf -- ${quote(original)}")) {
                    LogRepository.append(context, "INFO", "translation.legacy_removed", "已移除舊版 Root 簡體漢化")
                    return@mapNotNull null
                }
            }
            val cleanName = safeName(oldName)
            val canonical = "$PUBLIC_DOWNLOADS/$cleanName"
            if (original != canonical) runRoot("mv -f ${quote(original)} ${quote(canonical)}")
            DownloadedTranslation(cleanName, canonical, readScript(canonical, cleanName), rootFileExists("$canonical/$PUA_MARKER"))
        }.toList().distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    suspend fun apply(entry: DownloadedTranslation, progress: suspend (ApplyProgress) -> Unit = {}): String = withContext(Dispatchers.IO) {
        progress(ApplyProgress("正在讀取已下載漢化",0,0))
        if (!rootMode()) {
            val raw = File(staging, "native-source").also { it.deleteRecursively() }
            val prepared = File(staging, "native-prepared").also { it.deleteRecursively(); it.mkdirs() }
            readPack(entry, raw)
            prepareCharacters(raw, progress)
            normalizeForLanguage(raw, prepared, targetLanguage(), progress)
            val directory = File(prepared, nativeExportPath(targetLanguage()))
            if (nativeBackend() == "shizuku") {
                val archive = File(staging, "native-transfer.zip")
                try {
                    zipDirectory(directory, archive.outputStream())
                    progress(ApplyProgress("正在透過 Shizuku 寫入原生語言文件", 0, 0))
                    NativeStorageClient.use(context) { service ->
                        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            val error = service.apply(descriptor, targetLanguage().id)
                            if (error.isNotEmpty() && !service.hasBackup()) {
                                prefs.edit().remove("native_active_name").remove("native_active_language").apply()
                            }
                            check(error.isEmpty()) { error }
                        }
                    }
                } finally { archive.delete() }
            } else gameStorage.apply(directory, targetLanguage(), progress)
            prefs.edit().putString("native_active_name", entry.name).putString("native_active_language", targetLanguage().id)
                .putString("native_active_version",installedPackVersion(File(raw,".lcpatch-upstream.json").takeIf {it.isFile && it.length()<8192}?.readText(),null)).apply()
            LogRepository.append(context, "INFO", "translation.native_applied", "已寫入原生語言目錄：${entry.name}")
            return@withContext entry.name
        }
        val raw = File(staging, "${safeName(entry.name)}.raw").also { it.deleteRecursively() }
        require(runRoot("cp -R ${quote(entry.path)} ${quote(raw.absolutePath)} && chmod -R 0755 ${quote(raw.absolutePath)}")) { "無法讀取已下載漢化目錄" }
        if (!File(raw, PUA_MARKER).isFile) {
            encodeDirectoryToPua(raw, progress)
            File(raw, PUA_MARKER).writeText("1")
            validateJsonDirectory(File(raw, "Localize"))
            require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(raw.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法儲存 PUA 轉換結果" }
        }
        applyPrepared(raw, entry.name, progress)
    }

    suspend fun importCustom(uri: Uri, displayName: String): DownloadedTranslation = withContext(Dispatchers.IO) {
        val label = safeName(displayName.substringBeforeLast('.').ifBlank { "自訂漢化" })
        val cache = File(downloads, "$label.zip")
        context.contentResolver.openInputStream(uri)?.use { input -> cache.outputStream().use(input::copyTo) } ?: error("無法讀取漢化文件")
        require(cache.length() > 4 && cache.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }) { "選擇的文件不是 ZIP" }
        ZipFile(cache).use { require(it.entries().asSequence().any { item -> localizeRelativePath(item.name) != null }) { "漢化包缺少 Localize 資料夾" } }
        val prepared = File(staging, "import-$label").also { it.deleteRecursively(); it.mkdirs() }
        unpackLocalize(cache, prepared)
        File(prepared, ".lcpatch-script").writeText(inferScript(label))
        prepareCharacters(prepared) {}
        val publicPath = savePack(prepared, label)
        cache.delete()
        prefs.edit().putString("download.${cache.name}", label).apply()
        LogRepository.append(context, "INFO", "translation.imported", "已匯入並解壓 $label")
        DownloadedTranslation(label, publicPath, if (rootMode()) inferScript(label) else "日文字形", rootMode())
    }

    fun targetLanguage(): OverrideLanguage {
        val id = prefs.getString("override_language", "jp")
        return LANGUAGES.firstOrNull { it.id == id && (rootMode() || it.id != "en") } ?: LANGUAGES.first()
    }

    fun setTargetLanguage(value: OverrideLanguage) {
        require(rootMode() || value.id in setOf("jp", "kr")) { "無 root 模式只支援日語與韓語字形漢化" }
        prefs.edit().putString("override_language", value.id).apply()
    }

    /** Convert the installed bytes, not a possibly newer downloaded package with the same name. */
    internal suspend fun convertActive(conversion: TextConversion,progress: suspend (ApplyProgress)->Unit): ConversionResult = withContext(Dispatchers.IO) {
        check(rootMode()) { "無 root 漢化需保留來源字形，不能進行繁簡轉換" }
        val name=activeName()
        check(name!="尚未選擇") {"目前尚未套用漢化"}
        val enabled=translationEnabled()
        val directory=if(enabled) NATIVE_COMPAT else NATIVE_DISABLED
        val work=File(staging,"convert-active").also {it.deleteRecursively()}
        val destination=File(work,"Localize/cn")
        val uid=android.os.Process.myUid()
        progress(ApplyProgress("正在讀取目前套用的漢化",0,0))
        require(runRoot("${validLanguageDirectoryTest(directory)} && mkdir -p ${quote(destination.parentFile!!.absolutePath)} && cp -R ${quote(directory)} ${quote(destination.absolutePath)} && chown -R $uid:$uid ${quote(work.absolutePath)} && chmod -R 0755 ${quote(work.absolutePath)} && { restorecon -RF ${quote(work.absolutePath)} 2>/dev/null || true; }")) {"無法讀取目前套用的漢化"}
        val evidence=runtimeVersionEvidence(directory)
        val metadata=evidence.first?.takeIf {runCatching {JSONObject(it).optInt("schema")==1}.getOrDefault(false)}
            ?:officialReleaseUrl(evidence.second)?.let {JSONObject().put("schema",1).put("url",it).toString()}
        metadata?.let {File(work,".lcpatch-upstream.json").writeText(it)}
        val result=convertTextFiles(work,conversion,progress)
        require(result.scannedFiles>0) {"目前漢化沒有 JSON 文件"}
        val script=if(conversion.id=="traditional") "繁體" else "簡體"
        File(work,".lcpatch-script").writeText(script)
        File(work,PUA_MARKER).writeText("1")
        validateJsonDirectory(File(work,"Localize"))
        savePack(work,name)
        applyPrepared(work,name,progress)
        // applyPrepared replaces the disabled directory directly when disabled;
        // calling setTranslationEnabled(false) again would delete that directory.
        LogRepository.append(context,"INFO","translation.converted","目前套用的 $name ${conversion.label}完成：${result.scannedFiles} 個 JSON")
        result
    }

    internal suspend fun convertDownloaded(entry: DownloadedTranslation, conversion: TextConversion, progress: suspend (ApplyProgress) -> Unit = {}): ConversionResult = withContext(Dispatchers.IO) {
        check(rootMode()) { "無 root 漢化需保留來源字形，不能進行繁簡轉換" }
        val work = File(staging, "convert-${safeName(entry.name)}").also { it.deleteRecursively() }
        readPack(entry, work)
        val result = convertTextFiles(work, conversion, progress)
        require(result.scannedFiles > 0) { "漢化目錄內沒有 JSON 文件" }
        File(work, ".lcpatch-script").writeText(if (conversion.id == "traditional") "繁體" else "簡體")
        File(work, PUA_MARKER).writeText("1")
        validateJsonDirectory(File(work, "Localize"))
        if (rootMode()) {
            require(runRoot("rm -rf ${quote(entry.path)} && cp -R ${quote(work.absolutePath)} ${quote(entry.path)} && chmod -R 0755 ${quote(entry.path)}")) { "無法寫回轉換後的漢化目錄" }
        } else localPacks.replace(work, safeName(entry.name))
        LogRepository.append(context, "INFO", "translation.converted", "${entry.name} ${conversion.label}完成：已掃描 ${result.scannedFiles} 個 JSON，內容有變更 ${result.changedFiles} 個")
        result
    }

    fun fontName(): String = if (hasCustomFont()) {
        prefs.getString("font_name", null) ?: "自訂字型"
    } else "內建 PUA 中文字型"
    fun hasCustomFont(): Boolean = customFontFile() != null

    fun translationEnabled(): Boolean = if (rootMode()) prefs.getBoolean("translation_enabled", true)
        else if (nativeBackend() == "saf") gameStorage.hasBackup() else prefs.contains("native_active_name")

    fun migrateRuntime() {
        if (!rootMode()) return
        val fallback = File(context.filesDir, "runtime-pua-font.ttf")
        val font = customFontFile() ?: fallback.also { copyBundledPuaFont(it) }
        val cleanup = "rm -rf '/sdcard/limbus mods/zh_cn' ${quote("$PUBLIC_ROOT/目前套用")} ${quote("$PUBLIC_ROOT/.active-next")} ${quote("$PUBLIC_ROOT/.active-old")} ${quote(NATIVE_NEXT)} ${quote(NATIVE_OLD)}"
        val repairFont = "if ${validLanguageDirectoryTest(NATIVE_COMPAT)}; then cp -f ${quote(font.absolutePath)} ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} && chmod 0644 ${quote("$NATIVE_COMPAT/ChineseFont.ttf")}; restorecon -F ${quote("$NATIVE_COMPAT/ChineseFont.ttf")} 2>/dev/null || true; fi"
        runRoot("$cleanup; $repairFont")
        repairRuntimeIndex()
    }

    private fun repairRuntimeIndex(): Boolean {
        if (!rootMode()) return true
        return runCatching {
            val directory = if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED
            val repair = repairRuntimeTextAssetIndex(directory, File(staging, "runtime-index")) { command ->
                RootShell.run(command, timeoutMs = 15_000L, maxOutputChars = TEXT_ASSET_INDEX_LIMIT, keepTail = false)
            }
            if (repair?.changed == true) LogRepository.append(context, "INFO", "translation.textasset.index.repaired",
                "已為既有漢化補建內建文字索引，共 ${repair.entries} 筆；重新啟動遊戲後生效")
            true
        }.getOrElse { error ->
            LogRepository.append(context, "WARN", "translation.textasset.index.repair_failed", error.message ?: "索引修復失敗")
            false
        }
    }

    fun prepareGameLaunch(): Boolean {
        if (!rootMode()) return true
        // Native configuration reads the index once; stop the previous process before repairing it.
        if (!runRoot("am force-stop $GAME_PACKAGE")) return false
        if (!translationEnabled()) return true
        if (!repairRuntimeIndex()) return false
        return runRoot(validLanguageDirectoryTest(NATIVE_COMPAT))
    }

    fun inspectRuntime(): RuntimeInspection {
        if (!rootMode()) return RuntimeInspection()
        val prefix = targetLanguage().prefix.uppercase()
        val directory = if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED
        val command = "if [ -d ${quote(directory)} ]; then total=${'$'}(find ${quote(directory)} -type f -iname '*.json' | wc -l); matching=${'$'}(find ${quote(directory)} -type f -iname ${quote("${prefix}_*.json")} | wc -l); font=${'$'}(stat -c %s ${quote("$directory/ChineseFont.ttf")} 2>/dev/null || echo 0); echo \"${'$'}total|${'$'}matching|${'$'}font\"; else echo '0|0|0'; fi"
        val (success, output) = runRootOutput(command)
        if (!success) return RuntimeInspection()
        val values = output.lineSequence().lastOrNull { it.count { char -> char == '|' } == 2 }
            ?.trim()?.split('|') ?: return RuntimeInspection()
        return RuntimeInspection(
            jsonFiles = values.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
            matchingFiles = values.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
            fontBytes = values.getOrNull(2)?.trim()?.toLongOrNull() ?: 0,
            activeVersion() ?: runtimeVersionEvidence(directory).let {installedPackVersion(it.first,it.second)}
        )
    }

    suspend fun setTranslationEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        if (!rootMode()) {
            require(!enabled) { "請從已下載漢化選擇套用" }
            if (nativeBackend() == "shizuku") NativeStorageClient.use(context) { service ->
                val error = service.restore()
                check(error.isEmpty()) { error }
            } else gameStorage.restore()
            prefs.edit().remove("native_active_name").remove("native_active_language").apply()
            return@withContext
        }
        val command = if (enabled) {
            "am force-stop $GAME_PACKAGE && if ${validLanguageDirectoryTest(NATIVE_DISABLED)}; then rm -rf ${quote(NATIVE_COMPAT)} && mv ${quote(NATIVE_DISABLED)} ${quote(NATIVE_COMPAT)}; else ${validLanguageDirectoryTest(NATIVE_COMPAT)}; fi"
        } else {
            "am force-stop $GAME_PACKAGE && rm -rf ${quote(NATIVE_DISABLED)} && if [ -d ${quote(NATIVE_COMPAT)} ]; then mv ${quote(NATIVE_COMPAT)} ${quote(NATIVE_DISABLED)}; fi"
        }
        require(runRoot(command)) { if (enabled) "尚未套用任何漢化" else "無法停用漢化" }
        prefs.edit().putBoolean("translation_enabled", enabled).apply()
        LogRepository.append(context, "INFO", "translation.${if (enabled) "enabled" else "disabled"}", "漢化已${if (enabled) "啟用" else "停用"}，重新啟動遊戲後生效")
    }

    fun setCustomFont(uri: Uri, displayName: String) {
        val extension = displayName.substringAfterLast('.', "ttf").lowercase().let { if (it in setOf("ttf", "otf")) it else "ttf" }
        val target = File(context.filesDir, "custom-font.$extension")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) } ?: error("無法讀取字型文件")
        require(target.length() in 12..64L * 1024 * 1024) { "字型文件大小不正確" }
        File(context.filesDir, if (extension == "ttf") "custom-font.otf" else "custom-font.ttf").delete()
        val publicPath = "$PUBLIC_FONTS/${safeName(displayName.substringBeforeLast('.'))}.$extension"
        if (rootMode()) require(runRoot("mkdir -p ${quote(PUBLIC_FONTS)} && cp -f ${quote(target.absolutePath)} ${quote(publicPath)} && chmod 0666 ${quote(publicPath)}")) { "無法儲存到 LCPatch 字型目錄" }
        prefs.edit().putString("font_name", displayName).putString("font_path", target.absolutePath).putString("font_public_path", publicPath).apply()
    }

    fun clearCustomFont() {
        File(context.filesDir, "custom-font.ttf").delete(); File(context.filesDir, "custom-font.otf").delete()
        prefs.edit().remove("font_name").remove("font_path").remove("font_public_path").apply()
    }

    private fun customFontFile(): File? = prefs.getString("font_path", null)?.let(::File)?.takeIf(File::isFile)

    private fun copyBundledPuaFont(target: File) {
        target.parentFile?.mkdirs()
        context.assets.open("ChineseFont-PUA.ttf").use { input ->
            target.outputStream().buffered().use(input::copyTo)
        }
        require(target.length() >= 12) { "內建中文字型無法讀取" }
    }

    private fun runtimeSwapCommand(source: String): String =
        atomicSwapCommand(source, if (translationEnabled()) NATIVE_COMPAT else NATIVE_DISABLED, NATIVE_NEXT, NATIVE_OLD)

    private fun atomicSwapCommand(source: String, destination: String, next: String, old: String): String =
        "mkdir -p ${quote(File(next).parent!!)} && rm -rf ${quote(next)} ${quote(old)} && mkdir -p ${quote(next)} && cp -R ${quote("$source/.")} ${quote(next)} && chmod -R 0755 ${quote(next)} && ${validLanguageDirectoryTest(next)} && if [ -e ${quote(destination)} ]; then mv ${quote(destination)} ${quote(old)}; fi && if mv ${quote(next)} ${quote(destination)}; then chmod -R 0755 ${quote(destination)}; restorecon -RF ${quote(destination)} 2>/dev/null || true; if ${validLanguageDirectoryTest(destination)}; then rm -rf ${quote(old)} ${if (destination == NATIVE_COMPAT) quote(NATIVE_DISABLED) else ""}; else rm -rf ${quote(destination)}; if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi; else if [ -e ${quote(old)} ]; then mv ${quote(old)} ${quote(destination)}; fi; false; fi"

    private fun validLanguageDirectoryTest(path: String): String =
        "{ [ -d ${quote(path)} ] && [ -n \"${'$'}(find ${quote(path)} -type f -iname '*.json' -print -quit)\" ]; }"

    private suspend fun normalizeForLanguage(raw: File, output: File, language: OverrideLanguage, progress: suspend (ApplyProgress)->Unit = {}) {
        val localize = File(raw, "Localize")
        val source = if (!rootMode()) nativeLanguageSource(localize, language) else localize.listFiles()?.firstOrNull { candidate ->
            candidate.isDirectory && candidate.name.lowercase() in setOf("cn", "zh_cn", "zh-cn", "jp", "kr", "en", "hant", "zh-tw")
        } ?: localize
        val destination = File(output, if (rootMode()) "Localize/cn" else nativeExportPath(language)).apply { mkdirs() }
        copyLanguageResources(source,destination,language,rootMode(),progress)
    }

    private suspend fun convertTextFiles(output: File, conversion: TextConversion, progress: suspend (ApplyProgress) -> Unit): ConversionResult {
        if (conversion.id == "original") return ConversionResult(0, 0)
        val files = translationJsonFiles(output)
        val puaConverter = PuaConverter.fromAssets(context)
        
        progress(ApplyProgress("正在${conversion.label}", 0, files.size))
        val converter: (String) -> String = when (conversion.id) {
            "traditional" -> ZhConverterUtil::toTraditional
            "simplified" -> ZhConverterUtil::toSimple
            else -> error("不支援的繁簡轉換模式")
        }
        var changed = 0
        files.forEachIndexed { index, file ->
            val original = file.readText(Charsets.UTF_8)
            val decoded = decodeCjkUnicodeEscapes(decodePuaUnicodeEscapes(original))
            val readableText = puaConverter.convert(decoded, toPua = false)
            val converted = puaConverter.convert(converter(readableText), toPua = true)
            if (converted != original) {
                file.writeText(converted, Charsets.UTF_8)
                changed++
            }
            progress(ApplyProgress("正在${conversion.label}", index + 1, files.size))
        }
        return ConversionResult(files.size, changed)
    }

    private fun translationJsonFiles(output: File): List<File> = output.walkTopDown()
        .filter { file -> file.isFile && file.extension.equals("json", true) }
        .toList()

    private suspend fun encodeDirectoryToPua(output: File, progress: suspend (ApplyProgress) -> Unit) {
        val files = translationJsonFiles(output)
        val converter = PuaConverter.fromAssets(context)
        progress(ApplyProgress("正在自動轉換 PUA", 0, files.size))
        files.forEachIndexed { index, file ->
            val original = file.readText(Charsets.UTF_8)
            val converted = converter.convert(decodeCjkUnicodeEscapes(original), toPua = true)
            if (converted != original) file.writeText(converted, Charsets.UTF_8)
            progress(ApplyProgress("正在自動轉換 PUA", index + 1, files.size))
        }
        validateJsonDirectory(File(output, "Localize"))
    }

    private fun validateJsonDirectory(directory: File) {
        val files = translationJsonFiles(directory)
        require(files.isNotEmpty()) { "漢化目錄內沒有 JSON 文件" }
        files.forEach { file ->
            runCatching { org.json.JSONTokener(file.readText(Charsets.UTF_8)).nextValue() }
                .getOrElse { throw IllegalArgumentException("JSON 格式錯誤：${file.name}", it) }
        }
    }

    private fun rootFileExists(path: String): Boolean = runRoot("[ -f ${quote(path)} ]")

    private fun inferScript(name: String): String = when {
        name.contains("繁體", true) || name.contains("繁体", true) -> "繁體"
        name.contains("簡體", true) || name.contains("简体", true) -> "簡體"
        else -> "未判定"
    }

    private fun readScript(path: String, name: String): String {
        val result = executeRoot("cat ${quote("$path/.lcpatch-script")} 2>/dev/null")
        val value = result.output.trim()
        return if (result.success && value in setOf("簡體", "繁體")) value else inferScript(name)
    }

    private fun repairStorage() {
        val aliases = listOf("/sdcard/LCPatch/汉化", "/sdcard/lcpatch/漢化", "/sdcard/LC Patch/漢化", "/sdcard/limbus mod/汉化")
        val migrate = aliases.joinToString(" && ") { old -> "if [ -d ${quote(old)} ]; then cp -Rn ${quote("$old/.")} ${quote(PUBLIC_DOWNLOADS)}; fi" }
        runRoot("mkdir -p ${quote(PUBLIC_DOWNLOADS)} && $migrate")
        val legacyList = executeRoot("find ${quote(PUBLIC_DOWNLOADS)} -maxdepth 1 -type f -iname '*.zip' -print")
        if (legacyList.success) legacyList.output.lineSequence().filter(String::isNotBlank).forEach { path ->
            val label = safeName(File(path).nameWithoutExtension)
            val cache = File(downloads, "legacy-$label.zip")
            val prepared = File(staging, "legacy-$label").also { it.deleteRecursively(); it.mkdirs() }
            if (runRoot("cp -f ${quote(path)} ${quote(cache.absolutePath)}")) runCatching {
                unpackLocalize(cache, prepared)
                val target = "$PUBLIC_DOWNLOADS/$label"
                if (runRoot("rm -rf ${quote(target)} && cp -R ${quote(prepared.absolutePath)} ${quote(target)} && chmod -R 0755 ${quote(target)} && rm -f ${quote(path)}")) cache.delete()
            }
        }
    }

    private fun unpackLocalize(zipFile: File, destination: File) {
        var count = 0
        var total = 0L
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relativePath = localizeRelativePath(entry.name) ?: continue
                val target = File(destination, relativePath).canonicalFile
                require(target.path.startsWith(destination.canonicalPath + File.separator)) { "漢化包包含不安全路徑" }
                if (entry.isDirectory) target.mkdirs() else {
                    require(++count <= 30_000) { "漢化包檔案數過多" }
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            total += n; require(total <= 512L * 1024 * 1024) { "漢化包解壓後過大" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
        }
        require(File(destination, "Localize").isDirectory) { "漢化包缺少 Localize 資料夾" }
    }

    fun pcCoverageSummary(): String = prefs.getString("pc_coverage_summary", "重新套用漢化後，會比對 PC 翻譯並建立內建文字索引") ?: ""

    private suspend fun supplementPcTranslations(directory: File, script: String, progress: suspend (ApplyProgress) -> Unit) {
        progress(ApplyProgress("正在比對 PC 版翻譯", 0, 0))
        val reference = File(staging, "pc-reference").also { it.deleteRecursively(); it.mkdirs() }
        val archive = File(context.cacheDir, "lcpatch-pc-reference.zip")
        var sourceUrl = ""
        try {
            val entry = fetchOfficialLatest().single()
            sourceUrl = entry.url
            val part = File(context.cacheDir, "lcpatch-pc-reference.part")
            downloadResumable(entry.url, part, "PC 版翻譯", {})
            java.nio.file.Files.move(part.toPath(), archive.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            unpackLocalize(archive, reference)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            File(directory, ".lcpatch-coverage.txt").writeText("PC 比對未完成：${error.message}\n")
            prefs.edit().putString("pc_coverage_summary", "PC 翻譯比對未完成，已保留所選漢化；詳情見日誌").apply()
            LogRepository.append(context, "WARN", "translation.pc.supplement.failed", "PC 比對未完成，保留所選漢化：${error.message}")
            reference.deleteRecursively()
            archive.delete()
            return
        }
        try {
            val converter = PuaConverter.fromAssets(context)
            val report = supplementTranslationDirectory(directory, File(reference, "Localize/cn"), targetLanguage().prefix,
                decode = { converter.convert(decodeCjkUnicodeEscapes(decodePuaUnicodeEscapes(it)), toPua = false) },
                encode = { value ->
                    val converted = when (script) {
                        "繁體" -> ZhConverterUtil.toTraditional(value)
                        else -> value
                    }
                    converter.convert(converted, toPua = true)
                })
            File(directory, ".lcpatch-coverage.txt").writeText("來源：$sourceUrl\n$report\n")
            prefs.edit().putString("pc_coverage_summary", "PC 翻譯：$report").apply()
            LogRepository.append(context, "INFO", "translation.pc.supplement", report.toString())
        } finally {
            reference.deleteRecursively()
            archive.delete()
        }
    }

    private fun runRoot(command: String): Boolean = executeRoot(command).success

    private fun runRootOutput(command: String): Pair<Boolean, String> {
        val result = executeRoot(command)
        return result.success to result.output
    }

    private fun safeName(value: String): String = safePackName(value)
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

internal fun isLegacyRootSimplified(name: String): Boolean =
    Regex("^Root\\s*简体汉化$", RegexOption.IGNORE_CASE).matches(name)

internal fun parseTranslationCatalog(text: String): List<TranslationEntry> {
    var section = ""
    return text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1)
            null
        } else if (line.isBlank() || line.startsWith("#") ||
            !(section == "漢化-XP" || section == "汉化-XP")) null
        else {
            val columns = line.split('|').map(String::trim)
            if (columns.size < 4) null else TranslationEntry(section, columns[0], columns[1], columns[2], columns[3])
        }
    }.toList()
}

internal fun localizeRelativePath(value: String): String? {
    val path = value.replace('\\', '/').trimStart('/')
    val marker = "/Localize/"
    val officialMarker = "/Lang/LLC_zh-CN/"
    return when {
        path == "Localize" || path == "Localize/" -> "Localize/"
        path.startsWith("Localize/") -> path
        marker in path -> "Localize/" + path.substringAfter(marker)
        officialMarker in path -> path.substringAfter(officialMarker).takeUnless { it.startsWith("Info/") }?.let { "Localize/cn/"+it }
        else -> null
    }
}

internal fun correctedLanguageFileName(value: String, prefix: String): String {
    if (!value.endsWith(".json", ignoreCase = true)) return value
    val normalizedPrefix = "${prefix.uppercase()}_"
    if(value == "kr_settings-ui-donttranslate.json") return normalizedPrefix+value
    return if (Regex("^(JP|KR|EN)_", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
        value.replace(Regex("^(JP|KR|EN)_", RegexOption.IGNORE_CASE), normalizedPrefix)
    } else {
        normalizedPrefix + value
    }
}

internal fun decodeCjkUnicodeEscapes(value: String): String =
    Regex("(?<!\\\\)\\\\u([0-9a-fA-F]{4})").replace(value) { match ->
        val character = match.groupValues[1].toInt(16).toChar()
        if (character.code in 0x3400..0x9FFF || character.code in 0xF900..0xFAFF) character.toString() else match.value
    }

internal fun decodePuaUnicodeEscapes(value: String): String =
    Regex("(?<!\\\\)\\\\u([0-9a-fA-F]{4})").replace(value) { match ->
        val character = match.groupValues[1].toInt(16).toChar()
        if (character.code in 0xE000..0xF8FF) character.toString() else match.value
    }

internal fun isOfficialLlcUrl(url: String): Boolean = url.startsWith("https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/")

/** Countable work reports real batches; shell/network phases report unknown totals. */
internal suspend fun copyLanguageResources(source: File,destination: File,language: OverrideLanguage,
    includeNonJson: Boolean,progress: suspend (ApplyProgress)->Unit) {
    val files=source.walkTopDown().filter {it.isFile && (includeNonJson || it.extension.equals("json",true))}.toList()
    val stage="正在整理${language.label}覆蓋文件"
    progress(ApplyProgress(stage,0,files.size))
    files.forEachIndexed { index,input ->
        val relative=input.relativeTo(source)
        val name=correctedLanguageFileName(relative.name,language.prefix)
        val parent=relative.parent?.let {File(destination,it)}?:destination
        input.copyTo(File(parent.apply {mkdirs()},name),overwrite=true)
        if(index==files.lastIndex || (index+1)%16==0) progress(ApplyProgress(stage,index+1,files.size))
    }
}


internal fun officialReleaseUrl(value: String?): String? = value?.takeIf {
    it.lineSequence().any {line ->line.trim()=="零協會完整資源與劇情角色名稱已自動整理"}
}?.let {
    Regex("""https://github\.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/download/[^\s]+""")
        .find(it)?.value
}

internal fun installedPackVersion(metadata: String?,coverage: String?): String? {
    fun valid(value: String?): String? = value?.takeIf {
        it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")) && it.lowercase() !in setOf("latest","null","unknown")
    }
    val json=metadata?.takeIf {it.length<8192}?.let {runCatching {JSONObject(it)}.getOrNull()}?.takeIf {it.optInt("schema")==1}
    valid(json?.optString("version"))?.let {return it}
    val url=json?.optString("url")?.takeIf(::isOfficialLlcUrl) ?: officialReleaseUrl(coverage)
    val tag=url?.substringAfter("/releases/download/","")?.substringBefore('/')
    return valid(tag?.let {runCatching {java.net.URLDecoder.decode(it,"UTF-8")}.getOrNull()})
}
