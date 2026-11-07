package com.lcpatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * Shared application layout system. Feature pages consume the same spacing and
 * control hierarchy instead of defining one-off dimensions.
 *
 * Geometry and motion belonging to the floating navigation/morph overlay are
 * intentionally owned by their animation components.
 */
internal object AppUi {
    val PageGutter = 16.dp
    val PageGap = 16.dp
    val CardPadding = 20.dp
    val ContentGap = 8.dp
    val SectionGap = 12.dp
    val ActionGap = 12.dp
    val ActionTopGap = 16.dp
    val ActionMinHeight = 52.dp
    val PreferenceCorner = 18.dp
    val ActionCorner = 16.dp
    val DropdownItemCorner = 12.dp
}

internal val AppPreferenceModifier: Modifier =
    Modifier.clip(RoundedCornerShape(AppUi.PreferenceCorner))

@Composable
internal fun AppPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(AppUi.CardPadding),
        content = content,
    )
}

/** Keeps MIUIX preference-group spacing while centralizing its usage. */
@Composable
internal fun AppPreferencePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier, content = content)
}

/** Stacks actions with a reliable visible gap, even under large font scaling. */
@Composable
internal fun AppActionStack(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppUi.ActionGap),
        content = content,
    )
}

@Composable
internal fun AppPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        modifier = modifier.fillMaxWidth().heightIn(min = AppUi.ActionMinHeight)
            .clip(RoundedCornerShape(AppUi.ActionCorner)),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
internal fun AppSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        modifier = modifier.fillMaxWidth().heightIn(min = AppUi.ActionMinHeight)
            .clip(RoundedCornerShape(AppUi.ActionCorner)),
        text = text,
        enabled = enabled,
        onClick = onClick,
    )
}
