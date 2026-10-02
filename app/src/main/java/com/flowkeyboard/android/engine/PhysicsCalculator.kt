package com.flowkeyboard.android.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/** All coordinates are pixels; cruise and fling velocities are pixels/second. */
object PhysicsCalculator {
    const val MAX_FLING_VELOCITY = 8_000f
    fun wrap(coordinate: Float, period: Float): Float {
        require(period.isFinite() && period > 0f) { "A cyclic track needs a positive finite length" }
        if (!coordinate.isFinite()) return 0f
        val result = (((coordinate.toDouble() % period) + period) % period).toFloat()
        return if (result >= period) 0f else result
    }

    fun dampVelocity(velocity: Float, elapsedSeconds: Float, friction: Float = 5.5f): Float {
        if (!velocity.isFinite() || !elapsedSeconds.isFinite() || !friction.isFinite()) return 0f
        val clamped = velocity.coerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY)
        val result = clamped * exp(-friction.coerceAtLeast(0f) * elapsedSeconds.coerceAtLeast(0f))
        return if (abs(result) < 0.5f) 0f else result
    }

    fun advance(offset: Float, velocity: Float, elapsedSeconds: Float, period: Float): Float {
        val delta = if (elapsedSeconds.isFinite()) elapsedSeconds.coerceIn(0f, 0.05f) else 0f
        val speed = if (velocity.isFinite()) velocity.coerceIn(-MAX_FLING_VELOCITY, MAX_FLING_VELOCITY) else 0f
        return wrap(offset + speed * delta, period)
    }

    fun cyclicDistance(first: Float, second: Float, period: Float): Float =
        abs(wrap(first - second + period / 2f, period) - period / 2f)

    fun crossesTarget(center: Float, target: Float, keyWidth: Float, period: Float): Boolean =
        keyWidth >= 0f && cyclicDistance(center, target, period) <= keyWidth / 2f

    fun keyAt(coordinate: Float, offset: Float, spacing: Float, count: Int, keyWidth: Float = spacing): Int? {
        if (count <= 0 || !spacing.isFinite() || spacing <= 0f || !coordinate.isFinite()) return null
        val period = spacing * count
        val index = floor(wrap(coordinate - offset, period) / spacing).toInt().coerceIn(0, count - 1)
        val center = offset + (index + 0.5f) * spacing
        return index.takeIf { crossesTarget(center, coordinate, keyWidth, period) }
    }
}
