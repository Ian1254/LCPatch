package com.lcpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewContractTest {
    @Test
    fun uiStylePreferenceFallsBackToHyperOsForMissingOrUnknownValue() {
        assertEquals(UiStyle.HYPER_OS_4, UiStyle.fromKey(null))
        assertEquals(UiStyle.HYPER_OS_4, UiStyle.fromKey("invalid"))
        assertEquals(UiStyle.HYPER_OS_4, UiStyle.fromKey("hyperos4"))
        assertEquals(UiStyle.COLOR_OS_17, UiStyle.fromKey("coloros17"))
    }

    @Test
    fun runtimeStatusPreservesReadinessAndDiagnosticCounts() {
        val missingFont = RuntimeOverviewUiState.from(
            RuntimeInspection(jsonFiles = 92, matchingFiles = 21, fontBytes = 0)
        )
        assertTrue(missingFont.ready)
        assertFalse(missingFont.fontReady)
        assertEquals(92, missingFont.jsonFiles)
        assertEquals(21, missingFont.matchingFiles)

        val ready = RuntimeOverviewUiState.from(
            RuntimeInspection(jsonFiles = 120, matchingFiles = 90, fontBytes = 64)
        )
        assertTrue(ready.ready)
        assertTrue(ready.fontReady)

        val missingResources = RuntimeOverviewUiState.from(
            RuntimeInspection(jsonFiles = 0, matchingFiles = 0, fontBytes = 64)
        )
        assertFalse(missingResources.ready)
    }
}
