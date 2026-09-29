package com.lcpatch

import kotlin.math.*

/** Independent implementation of the edge motion observed in Clock 18.26.0.
 * Folme spring(damping, response): omega = 2 PI / response (unit mass).
 * All geometry is in item-width units; no device-pixel constants are copied.
 */
internal class BottomBarPhysics(initialPosition: Float) {
    internal class Edge(var position: Double, var velocity: Double = 0.0) {
        fun advance(target: Double, damping: Double, response: Double, dt: Double) {
            val omega = 2.0 * PI / response
            val x = position - target
            val decay = exp(-damping * omega * dt)
            if (damping == 1.0) {
                val b = velocity + omega * x
                position = target + (x + b * dt) * decay
                velocity = (velocity - omega * b * dt) * decay
            } else {
                val wd = omega * sqrt(1.0 - damping * damping)
                val b = (velocity + damping * omega * x) / wd
                val wave = x * cos(wd * dt) + b * sin(wd * dt)
                position = target + decay * wave
                velocity = decay * (-damping * omega * wave + wd * (-x * sin(wd * dt) + b * cos(wd * dt)))
            }
        }
    }

    private val left = Edge(initialPosition.toDouble())
    private val right = Edge(initialPosition.toDouble() + 1.0)
    var dragging = false
        private set
    private var pointerPosition = initialPosition.toDouble()
    private var target = initialPosition.toDouble()
    private var direction = 0.0
    private var trail = 0.0
    val leftPosition get() = left.position.toFloat()
    val rightPosition get() = right.position.toFloat()
    val position get() = ((left.position + right.position - 1.0) / 2.0).toFloat()
    val dragPosition get() = pointerPosition.toFloat()
    val active get() = abs(left.position - target) > 0.0001 ||
        abs(right.position - target - 1.0) > 0.0001 ||
        abs(left.velocity) + abs(right.velocity) > 0.001 || abs(trail) > 0.0001

    fun beginDrag() {
        dragging = true
        pointerPosition = position.toDouble().coerceIn(0.0, 2.0)
        target = pointerPosition
        trail = 0.0
    }

    fun dragBy(delta: Float, elapsedSeconds: Float) {
        // Incremental clamp discards excess travel: reversing at either rim
        // responds on the very next event, even after a long outward drag.
        val previous = pointerPosition
        pointerPosition = (pointerPosition + delta).coerceIn(0.0, 2.0)
        target = pointerPosition
        val movement = pointerPosition - previous
        if (movement != 0.0) direction = movement
        val speed = movement / elapsedSeconds.coerceIn(0.001f, 0.05f)
        trail = (speed * 0.02).coerceIn(-0.14, 0.14)
        if (pointerPosition == 0.0 || pointerPosition == 2.0) trail = 0.0
    }

    fun select(index: Int) {
        dragging = false
        target = index.coerceIn(0, 2).toDouble()
        direction = target - position
        trail = 0.0
        // Retarget without discarding either edge's current velocity.
    }

    fun advance(seconds: Float) {
        val dt = seconds.toDouble().coerceIn(0.0, 0.05)
        if (dragging) {
            left.advance(target - max(0.0, trail), 1.0, 0.15, dt)
            right.advance(target + 1.0 - min(0.0, trail), 1.0, 0.15, dt)
            trail *= exp(-dt / 0.024)
        } else {
            left.advance(target, if (direction < 0) 0.7 else 0.75, if (direction < 0) 0.4 else 0.5, dt)
            right.advance(target + 1.0, if (direction > 0) 0.7 else 0.75, if (direction > 0) 0.4 else 0.5, dt)
        }
        constrain()
        if (!dragging && !active) {
            left.position = target
            right.position = target + 1.0
            left.velocity = 0.0
            right.velocity = 0.0
        }
    }

    private fun constrain() {
        fun bound(edge: Edge, min: Double, max: Double) {
            if (edge.position < min) {
                edge.position = min
                if (edge.velocity < 0) edge.velocity = 0.0
            }
            if (edge.position > max) {
                edge.position = max
                if (edge.velocity > 0) edge.velocity = 0.0
            }
        }
        bound(left, 0.0, 2.0)
        bound(right, 1.0, 3.0)
        val width = right.position - left.position
        if (width > 1.32 || width < 0.85) {
            val center = (left.position + right.position) / 2.0
            val half = width.coerceIn(0.85, 1.32) / 2.0
            left.position = center - half
            right.position = center + half
            val velocity = (left.velocity + right.velocity) / 2.0
            left.velocity = velocity
            right.velocity = velocity
            bound(left, 0.0, 2.0)
            bound(right, 1.0, 3.0)
        }
    }
}
