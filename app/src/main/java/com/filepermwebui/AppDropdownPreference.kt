package com.lcpatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * App-owned dropdown with a clipped touch indication on every option.
 * In Miuix 0.9.3 the standard DropdownImpl draws a rectangular item background
 * before selectable; the popup container's rounding cannot fix that highlight.
 *
 * Reuse Miuix's BasicComponent and overlay popup host so navigation, dimming,
 * accessibility, and the menu's enter/exit animation remain consistent.
 */
@Composable
internal fun AppDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val canOpen = enabled && items.isNotEmpty()
    val muted = if (canOpen) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.then(AppPreferenceModifier),
        title = title,
        summary = summary,
        enabled = canOpen,
        role = Role.DropdownList,
        onClick = {
            expanded = !expanded
            if (expanded) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        endActions = {
            items.getOrNull(selectedIndex)?.let { current ->
                Text(
                    text = current,
                    modifier = Modifier
                        .padding(end = AppUi.ContentGap)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    color = muted,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    textAlign = TextAlign.End,
                )
            }
            DropdownArrowEndAction(actionColor = muted)
            OverlayListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                onDismissRequest = { expanded = false },
            ) {
                ListPopupColumn {
                    items.forEachIndexed { index, label ->
                        AppDropdownOption(
                            label = label,
                            selected = index == selectedIndex,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                expanded = false
                                onSelectedIndexChange(index)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AppDropdownOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColor = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .widthIn(min = 200.dp)
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(AppUi.DropdownItemCorner))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = selectedColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
    }
}
