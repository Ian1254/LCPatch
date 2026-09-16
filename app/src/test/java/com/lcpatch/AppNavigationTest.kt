package com.lcpatch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun detailNavigationPushesAndPopsHistory() {
        val detail = NavigationState(page = SETTINGS).navigateTo(ABOUT)

        assertEquals(ABOUT, detail.page)
        assertArrayEquals(intArrayOf(SETTINGS), detail.stack)
        assertEquals(1, detail.direction)

        val back = detail.navigateBack()
        assertEquals(SETTINGS, back.page)
        assertArrayEquals(intArrayOf(), back.stack)
        assertEquals(-1, back.direction)
    }

    @Test
    fun topLevelNavigationClearsDetailHistory() {
        val state = NavigationState(
            page = ABOUT,
            stack = intArrayOf(SETTINGS),
            direction = 1
        ).navigateTo(LOGS)

        assertEquals(LOGS, state.page)
        assertArrayEquals(intArrayOf(), state.stack)
        assertEquals(-1, state.direction)
    }

    @Test
    fun topLevelSelectionCommitsBeforeDetailNavigation() {
        val settings = NavigationState(page = OVERVIEW).navigateTo(SETTINGS)
        val display = settings.navigateTo(DISPLAY)

        assertEquals(SETTINGS, settings.page)
        assertArrayEquals(intArrayOf(), settings.stack)
        assertEquals(DISPLAY, display.page)
        assertArrayEquals(intArrayOf(SETTINGS), display.stack)
    }

    @Test
    fun topLevelDirectionFollowsVisualOrder() {
        assertEquals(1, NavigationState(page = OVERVIEW).navigateTo(LOGS).direction)
        assertEquals(1, NavigationState(page = LOGS).navigateTo(SETTINGS).direction)
        assertEquals(-1, NavigationState(page = SETTINGS).navigateTo(LOGS).direction)
        assertEquals(-1, NavigationState(page = LOGS).navigateTo(OVERVIEW).direction)
    }

    @Test
    fun fallbackBackNavigationUsesMenuHierarchy() {
        assertEquals(SETTINGS, NavigationState(ABOUT).navigateBack().page)
        assertEquals(SETTINGS, NavigationState(DISPLAY).navigateBack().page)
        assertEquals(OVERVIEW, NavigationState(DOWNLOAD).navigateBack().page)
        assertEquals(OVERVIEW, NavigationState(DOWNLOADED).navigateBack().page)
        assertEquals(OVERVIEW, NavigationState(SETTINGS).navigateBack().page)
        assertEquals(OVERVIEW, NavigationState(LOGS).navigateBack().page)
    }

    @Test
    fun parentMetadataMatchesBackFallbacks() {
        assertEquals(OVERVIEW, parentPage(LOGS))
        assertEquals(OVERVIEW, parentPage(SETTINGS))
        assertEquals(SETTINGS, parentPage(ABOUT))
        assertEquals(SETTINGS, parentPage(DISPLAY))
    }

    @Test
    fun pageMetadataIsCentralized() {
        assertEquals(listOf(OVERVIEW, LOGS, SETTINGS), TOP_LEVEL_PAGES)
        assertEquals("", pageTitle(OVERVIEW))
        assertEquals("", pageTitle(LOGS))
        assertEquals("", pageTitle(SETTINGS))
        assertEquals("應用程式更新", pageTitle(UPDATE))
        assertEquals("環境與權限", pageTitle(ONBOARDING))
    }
}
