package com.lcpatch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
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
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null, actions: @Composable RowScope.() -> Unit = {}) {
    top.yukonga.miuix.kmp.basic.SmallTopAppBar(title=title,color=Color.Transparent, actions=actions,
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
    enabled: Boolean, trailing: @Composable () -> Unit, action: Modifier, last: Boolean = false) {
    val inset = (16.dp - LocalCardInset.current).coerceAtLeast(0.dp)
    val glyph: SystemGlyph? = null
    val divider = MiuixTheme.colorScheme.dividerLine
    val grouped = LocalGrouped.current
    val soft = LocalSystemUiStyle.current == SystemUiStyle.HyperOS4
    Row(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).then(action)
        .drawBehind {
            if (grouped && soft && !last) {
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
    enabled: Boolean = true, value: String? = null, last: Boolean = false, authorization: Boolean? = null, onClick: () -> Unit) {
    PreferenceRow(title,summary,value,modifier,enabled,
        trailing={
            if(authorization != null) {
                SystemStatusLabel(if(authorization) "已授權" else "未授權", authorization)
                Spacer(Modifier.width(8.dp))
            }
            Icon(SystemGlyph.Chevron.vector(),null,Modifier.size(18.dp),tint=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        },
        action=Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick),last=last)
}

/** Text conveys the state; the matching glyph is decorative for accessibility. */
@Composable internal fun SystemStatusLabel(text: String, positive: Boolean? = null, loading: Boolean = false) {
    val color = when(positive) {
        true -> Color(if(LocalSystemUiDark.current) 0xFF75D5A8 else 0xFF24734F)
        false -> Color(if(LocalSystemUiDark.current) 0xFFF47777 else 0xFFB3261E)
        null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val rotation = if(loading) {
        val transition = rememberInfiniteTransition(label="status spinner")
        val angle by transition.animateFloat(0f,360f,
            infiniteRepeatable(tween(1000,easing=LinearEasing)),label="status rotation")
        angle
    } else 0f
    Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.semantics(mergeDescendants=true) {}) {
        Text(text,fontSize=12.sp,lineHeight=18.sp,color=color,maxLines=1)
        if(loading || positive != null) {
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(16.dp).rotate(rotation).clearAndSetSemantics {}) {
                val stroke=1.5.dp.toPx()
                val inset=stroke/2
                if(loading) drawArc(color,startAngle=-90f,sweepAngle=270f,useCenter=false,
                    topLeft=Offset(inset,inset),size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),
                    style=androidx.compose.ui.graphics.drawscope.Stroke(stroke,cap=StrokeCap.Round))
                else {
                    drawCircle(color,radius=(size.minDimension-stroke)/2,
                        style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    fun point(x: Float,y: Float)=Offset(size.width*x,size.height*y)
                    if(positive == true) {
                        drawLine(color,point(.27f,.51f),point(.43f,.66f),stroke,StrokeCap.Round)
                        drawLine(color,point(.43f,.66f),point(.74f,.34f),stroke,StrokeCap.Round)
                    } else {
                        drawLine(color,point(.34f,.34f),point(.66f,.66f),stroke,StrokeCap.Round)
                        drawLine(color,point(.66f,.34f),point(.34f,.66f),stroke,StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable internal fun SystemToggle(checked: Boolean, enabled: Boolean = true) {
    MiuixSwitch(checked=checked,onCheckedChange=null,enabled=enabled,modifier=Modifier.clearAndSetSemantics {})
}

@Composable internal fun SystemSwitchPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, checked: Boolean, last: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    PreferenceRow(title,summary,null,modifier,enabled,trailing={ SystemToggle(checked,enabled) },
        action=Modifier.toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange),last=last)
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
    onSelectedIndexChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null, last: Boolean = false) {
    val divider=MiuixTheme.colorScheme.dividerLine
    val grouped=LocalGrouped.current
    val inset=(16.dp-LocalCardInset.current).coerceAtLeast(0.dp)
    MiuixDropdownPreference(title=title,items=items,selectedIndex=selectedIndex,modifier=modifier.drawBehind {
            if(grouped && !last) drawLine(divider,Offset(inset.toPx(),size.height-.5f),Offset(size.width-inset.toPx(),size.height-.5f),.5.dp.toPx())
        },
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
    val view=LocalView.current
    val ink=MiuixTheme.colorScheme.onSurface
    Box {
        Box(Modifier.size(48.dp).clip(CircleShape).alpha(if(busy) .4f else 1f)
            .semantics { contentDescription=if(busy) "正在收集診斷" else "更多日誌操作";stateDescription=if(expanded) "已展開" else "已收合" }
            .clickable(enabled=!busy,role=Role.Button) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                expanded=true
            },Alignment.Center) {
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

@Composable internal fun SystemLogGuide(total: Int, errors: Int, warnings: Int) {
    var details by remember { mutableStateOf(false) }
    SystemCard {
        SystemArrowPreference("診斷與記錄說明",last=true,value=if(total==0) "尚無記錄" else "共 $total 筆",onClick={details=true})
    }
    if(details) SystemModal("診斷與記錄說明",{details=false}) {
        Column(Modifier.padding(horizontal=24.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("分享或儲存時會重新收集遊戲相關系統記錄。文件會標示每項資料是否取得、逾時或權限不足；沒有模組事件時也可以匯出。",fontSize=14.sp,lineHeight=22.sp)
            Text("共 $total 筆 · 錯誤 $errors · 警告 $warnings",fontSize=14.sp)
            if(total==0) Text("尚無模組事件不代表遊戲沒有閃退，仍可分享診斷文件取得系統收集結果。",fontSize=14.sp,lineHeight=22.sp)
        }
        SystemDialogDone({details=false})
    }
}

@Composable internal fun SystemModeHelp(last: Boolean=true) {
    var details by remember { mutableStateOf(false) }
    SystemArrowPreference("運作方式說明",last=last,onClick={details=true})
    if(details) SystemModal("運作方式說明",{details=false}) {
        Text("Root 模式透過模組載入；離開 Root 模式前請先停用模組漢化。無 Root 模式直接覆蓋語言文件，須安裝並授權 Stellar。",
            Modifier.padding(horizontal=24.dp,vertical=8.dp),fontSize=14.sp,lineHeight=22.sp)
        SystemDialogDone({details=false})
    }
}

@Composable internal fun SystemSecondaryEntry(title: String, value: String? = null, last: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var details by remember { mutableStateOf(false) }
    SystemArrowPreference(title,value=value,last=last,onClick={details=true})
    if(details) SystemModal(title,{details=false}) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        SystemDialogDone({details=false})
    }
}

internal data class LogDateGroup(val label: String,val events: List<LogEvent>)
internal fun groupLogEventsByDate(events: List<LogEvent>,zone: java.time.ZoneId): List<LogDateGroup> =
    events.groupBy {event ->runCatching {
        java.time.Instant.parse(event.time).atZone(zone).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    }.getOrDefault("日期未知")}.map {LogDateGroup(it.key,it.value)}

@Composable internal fun SystemLogRecord(event: LogEvent,first: Boolean,last: Boolean) {
    val shape=RoundedCornerShape(topStart=if(first) 24.dp else 0.dp,topEnd=if(first) 24.dp else 0.dp,
        bottomStart=if(last) 24.dp else 0.dp,bottomEnd=if(last) 24.dp else 0.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MiuixTheme.colorScheme.surfaceContainer)) {
        SystemLogEntry(event)
        if(!last) Box(Modifier.fillMaxWidth().padding(horizontal=20.dp).height(.5.dp).background(MiuixTheme.colorScheme.dividerLine))
    }
}

/** Compact summaries in the top-level log; technical fields live in a secondary dialog. */
@Composable internal fun SystemLogEntry(event: LogEvent) {
    var details by remember(event) { mutableStateOf(false) }
    val time = remember(event.time) { runCatching {
        java.time.Instant.parse(event.time).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
    }.getOrDefault(event.time) }
    val ink=when(event.level) {
        "ERROR" -> Color(if(LocalSystemUiDark.current) 0xFFED8891 else 0xFFB73845)
        "WARN" -> Color(if(LocalSystemUiDark.current) 0xFFE6B469 else 0xFF8F5A13)
        else -> MiuixTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth().clickable(role=Role.Button) { details=true }.padding(horizontal=20.dp,vertical=16.dp)) {
        Text(event.message,fontSize=14.sp,lineHeight=22.sp,color=ink,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(time,Modifier.padding(top=6.dp),fontSize=12.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    if(details) SystemModal("記錄詳情",{details=false}) {
        Column(Modifier.padding(horizontal=24.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(event.message,fontSize=15.sp,lineHeight=23.sp)
            Text("等級：${event.level}\n事件：${event.code}\n時間：${event.time}"+
                if(event.process.isBlank()) "" else "\n程序：${event.process}",fontSize=13.sp,lineHeight=21.sp,
                color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        SystemDialogDone({details=false})
    }
}

internal data class OverviewUiState(
    val name: String, val script: String, val language: String, val enabled: Boolean,
    val rootMode: Boolean, val gameInstalled: Boolean, val gameVersion: String, val busy: Boolean,
    val scopeStatus: String, val rootStatus: String, val loadStatus: String,
    val ready: Boolean, val fontReady: Boolean, val jsonFiles: Int, val coverage: String, val packVersion: String? = null, val loadReady: Boolean = false
)

@Composable internal fun SystemTranslationPanel(state: OverviewUiState, onEnabled: (Boolean) -> Unit,
    onLaunch: () -> Unit, onDownload: () -> Unit, workProgress: ApplyProgress? = null, selectedPack: DownloadedTranslation? = null, waitingForGame: Boolean = false) {
    val selected = state.name != "尚未選擇"
    val accent = MiuixTheme.colorScheme.primary
    SystemCard {
        if(state.rootMode) SystemSwitchPreference("啟用漢化", checked = state.enabled,
            enabled = !state.busy, onCheckedChange = onEnabled)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(selected) state.name else selectedPack?.name ?: "尚未選擇漢化",Modifier.weight(1f),fontSize=18.sp,lineHeight=26.sp,
                    fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(selected) {
                    Spacer(Modifier.width(12.dp))
                    val healthy=state.enabled && (!state.rootMode || state.ready && state.fontReady)
                    val loading=workProgress != null || waitingForGame
                    SystemStatusLabel(
                        text=if(workProgress != null) "正在套用" else if(waitingForGame) "等待套用" else if(healthy) "已啟用" else if(!state.enabled) "已停用" else "需要重新套用",
                        positive=if(loading) null else healthy,
                        loading=loading)
                }
            }
            Text(if(selectedPack != null && !selected) packVersionLabel(selectedPack.version) else if(selectedPack != null && selectedPack.name != state.name) "${selectedPack.name} · ${packVersionLabel(selectedPack.version)}" else if(selected) packVersionLabel(state.packVersion) else "下載漢化包後即可直接套用。",
                Modifier.padding(top=5.dp,bottom=20.dp),fontSize=13.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if(waitingForGame) Text("遊戲進入標題畫面後，點擊通知套用漢化",fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.padding(bottom=12.dp),color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if(workProgress != null) SystemLanguageProgress(workProgress)
            SystemButton(if (state.gameInstalled) onLaunch else onDownload, Modifier.fillMaxWidth(), enabled = !state.busy) {
                Text(if (state.gameInstalled) "啟動遊戲" else "取得漢化資源", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable internal fun SystemQuickActions(onDownload: () -> Unit, onDownloaded: () -> Unit) {
    SystemCard {
        SystemArrowPreference("下載漢化", onClick = onDownload)
        SystemArrowPreference("已下載漢化", last=true,onClick = onDownloaded)
    }
}

@Composable internal fun SystemEnvironmentPanel(state: OverviewUiState, onCheckScope: () -> Unit,
    onRequestRoot: () -> Unit, onAuthorize: () -> Unit, loadAction: String) {
    var showDetails by remember { mutableStateOf(false) }
    var translationDetails by remember {mutableStateOf(false)}
    val authorized = if (state.rootMode) state.scopeStatus == "已啟用" && state.rootStatus == "已授權" else state.loadReady
    SystemCard {
        SystemArrowPreference(if (state.rootMode) "環境與權限" else "Stellar 與權限",
            authorization = authorized, onClick = {
                showDetails = true
                if(state.rootMode) { onCheckScope();onRequestRoot() }
            })
        SystemArrowPreference("漢化詳情",last=true,onClick={translationDetails=true})
    }
    if (translationDetails) SystemModal("漢化詳情", { translationDetails = false }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("遊戲版本：${state.gameVersion}", fontSize = 14.sp)
            Text("遊戲語言請設為「${state.language}」", fontSize = 14.sp)
            if (state.rootMode) {
                Text("已準備 ${state.jsonFiles} 個文字資源；${if (state.fontReady) "字型已準備" else "字型尚未準備"}", fontSize = 14.sp)
                Text(state.coverage, fontSize = 13.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else Text("${packVersionLabel(state.packVersion)}；${if(state.enabled) "已套用" else "尚未套用"}", fontSize = 14.sp)
        }
        SystemDialogDone({ translationDetails = false })
    }
    if (showDetails) SystemModal(if (state.rootMode) "環境與權限" else "Stellar 與權限", { showDetails = false }) {
        SystemEnvironmentDialogBody(state,onAuthorize,loadAction) { showDetails=false }
    }
}

@Composable internal fun SystemEnvironmentDialogBody(state: OverviewUiState,
    onAuthorize: ()->Unit,loadAction: String,onClose: ()->Unit) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Limbus Company  ${state.gameVersion}", fontSize = 14.sp)
            if(state.rootMode) {
                Text("模組作用域：${state.scopeStatus}",fontSize=14.sp)
                Text("Root 權限：${state.rootStatus}",fontSize=14.sp)
            } else Text(state.loadStatus,fontSize=14.sp)
        }
        if(!state.rootMode) Box(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp)) {
            SystemSecondaryButton(loadAction,onAuthorize,Modifier.fillMaxWidth(),enabled=!state.busy)
        }
        SystemDialogDone(onClose)
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

/** Compact one-row notice; long text opens secondary details rather than growing the banner. */
@Composable internal fun SystemNotice(message: String, error: Boolean, retry: (() -> Unit)?, dismiss: () -> Unit, modifier: Modifier = Modifier,
    onDetailsVisibleChange: (Boolean)->Unit = {}) {
    var details by remember(message) { mutableStateOf(false) }
    val closeDetails: ()->Unit = { details=false;onDetailsVisibleChange(false) }
    DisposableEffect(Unit) { onDispose { onDetailsVisibleChange(false) } }
    val shape=RoundedCornerShape(16.dp)
    Row(modifier.widthIn(max=480.dp).fillMaxWidth().shadow(6.dp,shape).clip(shape)
        .background(MiuixTheme.colorScheme.surfaceContainer).padding(start=14.dp,end=4.dp,top=4.dp,bottom=4.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(message,Modifier.weight(1f).semantics { liveRegion=LiveRegionMode.Polite }
            .clickable(role=Role.Button) { details=true;onDetailsVisibleChange(true) }.padding(vertical=6.dp),
            fontSize=13.sp,lineHeight=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(error && retry!=null) SystemTextButton("重試",retry)
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role=Role.Button,onClick=dismiss),Alignment.Center) {
            Icon(SystemGlyph.Close.vector(),contentDescription="關閉通知",modifier=Modifier.size(18.dp),tint=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    if(details) SystemModal("通知詳情",closeDetails) {
        Text(message,Modifier.padding(horizontal=24.dp,vertical=8.dp),fontSize=14.sp,lineHeight=22.sp)
        if(error && retry!=null) Box(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp)) {
            SystemSecondaryButton("重試",{closeDetails();retry()},Modifier.fillMaxWidth())
        }
        SystemDialogDone(closeDetails)
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
                    summary = if (blurEnabled) "頂欄、懸浮底欄、選單與面板使用模糊效果" else "使用清晰的純色背景",
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabled
                )
                SystemSwitchPreference(
                    modifier = Modifier,
                    title = "相容模糊", last=true,
                    summary = if (hazeRendererEnabled) "使用較柔和的模糊；效果不合適時可以關閉"
                        else "使用標準玻璃效果",
                    enabled = blurEnabled,
                    checked = hazeRendererEnabled,
                    onCheckedChange = onHazeRendererEnabled
                )

    }
}

/** Released Miuix container and resting item treatment, with no pressed tint or ripple. */
@Composable internal fun SystemStandardBottomNavigation(index: Int, labels: List<String>, icons: List<ImageVector>, onSelected: (Int)->Unit,
    backdrop: Backdrop? = null,hazeState: HazeState? = null) {
    top.yukonga.miuix.kmp.basic.NavigationBar(color=MiuixTheme.colorScheme.surfaceContainer) {
        labels.forEachIndexed { i,label ->
            val selected=i==index
            val source=remember { MutableInteractionSource() }
            val base=MiuixTheme.colorScheme.onSurfaceContainer
            val ink=base.copy(alpha=if(selected) 1f else .4f)
            Column(Modifier.weight(1f).heightIn(min=64.dp)
                .selectable(selected,interactionSource=source,indication=null,role=Role.Tab) {onSelected(i)}
                .padding(top=8.dp,bottom=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(icons[i],null,Modifier.size(26.dp),tint=ink)
                Spacer(Modifier.height(4.dp))
                Text(label,fontSize=12.sp,lineHeight=18.sp,color=ink,fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable internal fun SystemLanguageProgress(progress: ApplyProgress?) {
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp)) {
        top.yukonga.miuix.kmp.basic.LinearProgressIndicator(progress=progress?.fraction,modifier=Modifier.fillMaxWidth())
        Text(progress?.let { it.stage+if(it.total>0) " · ${it.current}/${it.total}" else "" }?:"正在準備…",
            Modifier.padding(top=6.dp).semantics { liveRegion=LiveRegionMode.Polite },fontSize=12.sp,lineHeight=18.sp,
            color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemDownloadRefresh(refreshing: Boolean,onRefresh: ()->Unit,topInset: Dp,content: @Composable ()->Unit) {
    top.yukonga.miuix.kmp.basic.PullToRefresh(isRefreshing=refreshing,onRefresh=onRefresh,
        modifier=Modifier.fillMaxSize().semantics {
            customActions=listOf(CustomAccessibilityAction("重新整理漢化資源") {onRefresh();true})
        },contentPadding=PaddingValues(top=topInset),
        refreshTexts=listOf("下拉重新整理","放開以重新整理","正在重新整理…","已更新"),content=content)
}

@Composable internal fun SystemDownloadEntry(entry: TranslationEntry,action: @Composable ()->Unit) {
    SystemCard(insideMargin=PaddingValues(18.dp)) {
        Text(entry.name,style=MiuixTheme.textStyles.title2)
        Spacer(Modifier.height(4.dp))
        Text(entry.author,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(6.dp))
        Text(entry.description,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Composable internal fun SystemActiveConversion(script: String,enabled: Boolean,onConversion: (TextConversion)->Unit) {
    SystemDropdownPreference(title="繁簡轉換",enabled=enabled,
        items=TranslationRepository.CONVERSIONS.map {it.label.removePrefix("轉為")},
        selectedIndex=if(script=="簡體") 1 else 0,
        onSelectedIndexChange={index ->TranslationRepository.CONVERSIONS.getOrNull(index)?.let(onConversion)})
}

@Composable internal fun SystemDeletePackIcon(enabled: Boolean,onClick: ()->Unit) {
    Box(Modifier.size(48.dp).alpha(if(enabled) 1f else .4f).clip(CircleShape).clickable(enabled=enabled,role=Role.Button,onClick=onClick).semantics {contentDescription="刪除漢化包"},contentAlignment=Alignment.Center) {
        top.yukonga.miuix.kmp.basic.Icon(Icons.Default.Delete,"刪除漢化包",Modifier.size(24.dp),
            tint=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun SystemPackDeleteActions(selecting: Boolean,busy: Boolean,hasPacks: Boolean,hasSelection: Boolean,allSelected: Boolean,
    enter: ()->Unit,selectAll: ()->Unit,confirm: ()->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        if(selecting) SystemTextButton(if(allSelected) "取消全選" else "全選",selectAll,enabled=!busy && hasPacks)
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled=!busy && if(selecting) hasSelection else hasPacks,role=Role.Button,
            onClick=if(selecting) confirm else enter).semantics {contentDescription=if(selecting) "刪除已選漢化包" else "批量刪除漢化包"},Alignment.Center) {
            Icon(Icons.Default.Delete,null,Modifier.size(24.dp),tint=MiuixTheme.colorScheme.onSurface.copy(alpha=if(busy || !hasPacks || selecting && !hasSelection) .38f else 1f))
        }
    }
}

@Composable internal fun SystemDownloadedPackCard(name: String,version: String?,active: Boolean,busy: Boolean,enabled: Boolean,
    progress: ApplyProgress?,onApply: ()->Unit,batchMode: Boolean=false) {
    val blue=MiuixTheme.colorScheme.primary
    val outline=MiuixTheme.colorScheme.onSurfaceVariantSummary
    SystemCard(insideMargin=PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name,fontSize=18.sp,lineHeight=26.sp)
                Text(packVersionLabel(version),Modifier.padding(top=5.dp),fontSize=13.sp,lineHeight=20.sp,
                    color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.width(12.dp))
            val label=if(batchMode) "選取 $name" else if(active) "重新套用 $name" else "套用 $name"
            if(!batchMode) SystemButton(onApply,enabled=enabled) {
                Icon(if(active) Icons.Default.Refresh else Icons.Default.Check,null,Modifier.size(18.dp),tint=Color.White)
                Spacer(Modifier.width(6.dp))
                Text(if(busy) "套用中" else if(active) "重新套用" else "套用",fontSize=14.sp,color=Color.White)
            } else Box(Modifier.size(48.dp).alpha(if(enabled) 1f else .38f).clip(CircleShape)
                .semantics {contentDescription=label;stateDescription=if(busy) "套用中" else if(active) if(batchMode) "已選取" else "已套用" else if(batchMode) "未選取" else "未套用"}
                .toggleable(active,enabled,role=Role.Checkbox,onValueChange={onApply()}),Alignment.Center) {
                Canvas(Modifier.size(28.dp)) {
                    if(active) drawCircle(blue)
                    else drawCircle(outline,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                if(active) Icon(Icons.Default.Check,null,Modifier.size(18.dp),tint=Color.White)
            }
        }
        if(busy) SystemLanguageProgress(progress)
    }
}


@Composable internal fun SystemUpdateCard(version: String,channel: String,status: String,healthy: Boolean,
    enabled: Boolean,onChannel: (String)->Unit,content: @Composable ColumnScope.()->Unit={}) {
    var open by remember {mutableStateOf(false)}
    val view=LocalView.current
    SystemCard(insideMargin=PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher),null,
                Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LCPatch",fontSize=21.sp,lineHeight=28.sp)
                Text(version,fontSize=12.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Box {
                Row(Modifier.heightIn(min=48.dp).offset(y=(-10).dp).clip(RoundedCornerShape(8.dp))
                    .alpha(if(enabled) 1f else .4f).clickable(enabled=enabled,role=Role.Button) {view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);open=true}
                    .semantics {contentDescription="更新渠道";stateDescription=if(channel=="beta") "測試版" else "正式版"},
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(if(channel=="beta") "測試版" else "正式版",fontSize=14.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.width(6.dp))
                    val ink=MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Box(Modifier.size(16.dp).drawBehind {
                        val path=Path().apply {moveTo(size.width*.25f,size.height*.32f);lineTo(size.width*.5f,size.height*.1f);lineTo(size.width*.75f,size.height*.32f)
                            moveTo(size.width*.25f,size.height*.68f);lineTo(size.width*.5f,size.height*.9f);lineTo(size.width*.75f,size.height*.68f)}
                        drawPath(path,ink,style=androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(),cap=StrokeCap.Round))
                    })
                }
                top.yukonga.miuix.kmp.overlay.OverlayListPopup(show=open,onDismissRequest={open=false},renderInRootScaffold=true) {
                    top.yukonga.miuix.kmp.basic.ListPopupColumn {
                        listOf("正式版","測試版").forEachIndexed {index,label ->
                            top.yukonga.miuix.kmp.basic.DropdownImpl(text=label,optionSize=2,isSelected=(channel=="beta")== (index==1),index=index,
                                dropdownColors=top.yukonga.miuix.kmp.basic.DropdownDefaults.dropdownColors(),enabled=enabled,dialogMode=false,
                                onSelectedIndexChange={open=false;onChannel(if(it==1) "beta" else "stable")})
                        }
                    }
                }
            }
        }
        Text(status,Modifier.padding(top=22.dp),fontSize=14.sp,lineHeight=22.sp,
            color=if(healthy) Color(if(LocalSystemUiDark.current) 0xFF81B5A4 else 0xFF287D61) else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        content()
    }
}
