package com.lcpatch

import org.junit.Assert.*
import org.junit.Test

class BottomBarPhysicsTest {
    private fun settle(p: BottomBarPhysics) { repeat(360) { p.advance(1f / 120f) } }

    @Test fun bothRimsDiscardExcessTravelAndReverseImmediately() {
        for (rim in listOf(0, 2)) {
            val p = BottomBarPhysics(rim.toFloat())
            p.beginDrag()
            val outward = if (rim == 0) -1f else 1f
            repeat(40) { p.dragBy(outward, 0.016f); p.advance(0.016f) }
            assertEquals(rim.toFloat(), p.dragPosition, 0f)
            assertEquals(rim.toFloat(), p.position, 0.0001f)
            p.dragBy(-outward * 0.1f, 0.016f)
            assertEquals(rim - outward * 0.1f, p.dragPosition, 0.0001f)
            p.select(rim)
            settle(p)
            assertEquals(rim.toFloat(), p.position, 0.0001f)
        }
    }

    @Test fun retargetDoesNotResetRenderedEdges() {
        val p = BottomBarPhysics(0f)
        p.select(2)
        repeat(8) { p.advance(1f / 60f) }
        val left = p.leftPosition
        val right = p.rightPosition
        p.select(0)
        assertEquals(left, p.leftPosition, 0f)
        assertEquals(right, p.rightPosition, 0f)
        settle(p)
        assertEquals(0f, p.position, 0.0001f)
        assertFalse(p.active)
    }

    @Test fun dragTakeoverAndReleasePreserveGeometry() {
        val p = BottomBarPhysics(0f)
        p.select(2)
        repeat(5) { p.advance(1f / 60f) }
        val left = p.leftPosition
        val right = p.rightPosition
        p.beginDrag()
        assertEquals(left, p.leftPosition, 0f)
        assertEquals(right, p.rightPosition, 0f)
        p.select(1)
        assertEquals(left, p.leftPosition, 0f)
        assertEquals(right, p.rightPosition, 0f)
        settle(p)
        assertEquals(1f, p.position, 0.0001f)
    }

    @Test fun edgesStayInsideContainerDuringRapidReversals() {
        val p = BottomBarPhysics(0f)
        repeat(800) { frame ->
            if (frame % 11 == 0) p.select(if (frame % 22 == 0) 2 else 0)
            p.advance(1f / 120f)
            assertTrue(p.leftPosition >= 0f)
            assertTrue(p.rightPosition <= 3f)
            assertTrue(p.rightPosition > p.leftPosition)
            assertTrue(p.rightPosition - p.leftPosition >= 0.849f)
            assertTrue(p.rightPosition - p.leftPosition <= 1.321f)
        }
    }

    @Test fun inwardEdgesCanOvershootAndRecoverAtBothRims() {
        for (hz in listOf(60, 120)) {
            for (rim in listOf(0, 2)) {
                val p = BottomBarPhysics(1f)
                p.select(rim)
                var inwardOvershoot = false
                repeat(hz * 2) {
                    p.advance(1f / hz)
                    inwardOvershoot = inwardOvershoot || if (rim == 0) {
                        p.rightPosition < 0.99f
                    } else {
                        p.leftPosition > 2.01f
                    }
                    assertTrue(p.leftPosition >= 0f)
                    assertTrue(p.rightPosition <= 3f)
                    assertTrue(p.rightPosition - p.leftPosition >= 0.849f)
                    assertTrue(p.rightPosition - p.leftPosition <= 1.321f)
                }
                assertTrue("Inner edge should spring past its resting position at rim $rim ($hz Hz)", inwardOvershoot)
                settle(p)
                assertEquals(rim.toFloat(), p.leftPosition, 0.0001f)
                assertEquals(rim + 1f, p.rightPosition, 0.0001f)
                assertFalse(p.active)
            }
        }
    }

    @Test fun sixtyAndOneTwentyHzHaveEquivalentMotion() {
        val a = BottomBarPhysics(0f)
        val b = BottomBarPhysics(0f)
        a.select(1); b.select(1)
        repeat(90) {
            a.advance(1f / 60f)
            repeat(2) { b.advance(1f / 120f) }
            assertEquals(a.leftPosition, b.leftPosition, 0.015f)
            assertEquals(a.rightPosition, b.rightPosition, 0.015f)
        }
    }
}
