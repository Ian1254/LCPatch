package com.lcpatch

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Applies Box parent-data alignment from a composable helper that is invoked inside a Box.
 *
 * This keeps the notice overlay reusable without making the entire helper a BoxScope receiver.
 */
internal fun Modifier.align(alignment: Alignment): Modifier =
    with(BoxScope) { this@align.align(alignment) }
