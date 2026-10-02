package com.flowkeyboard.android.engine

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsCalculatorTest {
    @Test fun `cyclic coordinates wrap in either direction`() {
        assertEquals(20f, PhysicsCalculator.wrap(1020f, 100f), .001f)
        assertEquals(80f, PhysicsCalculator.wrap(-1020f, 100f), .001f)
        assertEquals(0f, PhysicsCalculator.wrap(100f, 100f), .001f)
    }
    @Test fun `negative fling wraps through the beginning`() {
        assertEquals(90f, PhysicsCalculator.advance(5f, -300f, .05f, 100f), .001f)
    }
    @Test fun `extreme velocity is clamped and damped`() {
        val damped = PhysicsCalculator.dampVelocity(Float.MAX_VALUE, .1f)
        assertTrue(damped in 0f..PhysicsCalculator.MAX_FLING_VELOCITY)
        assertTrue(damped < PhysicsCalculator.MAX_FLING_VELOCITY)
        assertEquals(-damped, PhysicsCalculator.dampVelocity(-Float.MAX_VALUE, .1f), .001f)
    }
    @Test fun `damping is independent of frame subdivision`() {
        val whole = PhysicsCalculator.dampVelocity(1000f, .2f)
        val split = PhysicsCalculator.dampVelocity(PhysicsCalculator.dampVelocity(1000f, .1f), .1f)
        assertEquals(whole, split, .001f)
    }
    @Test fun `crosshair detects both edges and cyclic seams`() {
        assertTrue(PhysicsCalculator.crossesTarget(50f, 60f, 20f, 100f))
        assertFalse(PhysicsCalculator.crossesTarget(50f, 61f, 20f, 100f))
        assertTrue(PhysicsCalculator.crossesTarget(98f, 2f, 10f, 100f))
    }
    @Test fun `zero speed preserves position`() {
        assertEquals(45f, PhysicsCalculator.advance(45f, 0f, .016f, 100f), .001f)
        assertEquals(0f, PhysicsCalculator.dampVelocity(0f, .016f), .001f)
    }
    @Test fun `tap detection rejects gaps and finds repeated keys`() {
        assertEquals(0, PhysicsCalculator.keyAt(35f, 0f, 70f, 4, 60f))
        assertNull(PhysicsCalculator.keyAt(70f, 0f, 70f, 4, 60f))
        assertEquals(0, PhysicsCalculator.keyAt(315f, 0f, 70f, 4, 60f))
        assertEquals(3, PhysicsCalculator.keyAt(-35f, 0f, 70f, 4, 60f))
    }
    @Test fun `non finite input cannot poison animation`() {
        assertEquals(0f, PhysicsCalculator.wrap(Float.NaN, 100f), 0f)
        assertEquals(0f, PhysicsCalculator.dampVelocity(Float.POSITIVE_INFINITY, .1f), 0f)
        assertEquals(10f, PhysicsCalculator.advance(10f, 50f, Float.NaN, 100f), 0f)
    }
    @Test fun `long frame is bounded when resuming`() {
        assertEquals(5f, PhysicsCalculator.advance(0f, 100f, 10f, 100f), .001f)
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid track length is rejected`() {
        PhysicsCalculator.wrap(5f, 0f)
    }

    @Test fun `wrap supports fractional periods and exact negative multiples`() {
        assertEquals(.125f, PhysicsCalculator.wrap(1.125f, .5f), .001f)
        assertEquals(.375f, PhysicsCalculator.wrap(-.125f, .5f), .001f)
        listOf(0f, 100f, 300f, -100f, -300f).forEach {
            assertEquals(0f, PhysicsCalculator.wrap(it, 100f), 0f)
        }
    }

    @Test fun `wrap rejects every non positive or non finite period`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { period ->
            assertThrows(IllegalArgumentException::class.java) { PhysicsCalculator.wrap(10f, period) }
        }
    }

    @Test fun `non finite coordinates and velocities are safe`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertEquals(0f, PhysicsCalculator.wrap(value, 100f), 0f)
            assertEquals(0f, PhysicsCalculator.dampVelocity(value, .1f), 0f)
            assertEquals(0f, PhysicsCalculator.dampVelocity(100f, value), 0f)
            assertEquals(25f, PhysicsCalculator.advance(25f, value, .05f, 100f), 0f)
        }
    }

    @Test fun `friction applies exponential decay and preserves velocity sign`() {
        val early = PhysicsCalculator.dampVelocity(1000f, .1f)
        val later = PhysicsCalculator.dampVelocity(1000f, .2f)
        assertEquals(1000f * exp(-5.5f * .1f), early, .001f)
        assertTrue(later > 0f && later < early)
        assertEquals(-early, PhysicsCalculator.dampVelocity(-1000f, .1f), .001f)
        assertEquals(1000f * exp(-2f * .1f), PhysicsCalculator.dampVelocity(1000f, .1f, 2f), .001f)
    }

    @Test fun `velocity clamps before damping and stops below cutoff`() {
        val maximum = PhysicsCalculator.MAX_FLING_VELOCITY
        assertEquals(maximum, PhysicsCalculator.dampVelocity(Float.MAX_VALUE, 0f), 0f)
        assertEquals(-maximum, PhysicsCalculator.dampVelocity(-Float.MAX_VALUE, 0f), 0f)
        assertEquals(maximum * exp(-5.5f * .1f), PhysicsCalculator.dampVelocity(100000f, .1f), .001f)
        assertEquals(0f, PhysicsCalculator.dampVelocity(.49f, 0f), 0f)
        assertEquals(0f, PhysicsCalculator.dampVelocity(-.49f, 0f), 0f)
        assertEquals(.5f, PhysicsCalculator.dampVelocity(.5f, 0f), 0f)
        assertEquals(-.5f, PhysicsCalculator.dampVelocity(-.5f, 0f), 0f)
        assertEquals(0f, PhysicsCalculator.dampVelocity(1000f, 10f), 0f)
    }

    @Test fun `negative time or friction cannot accelerate damping`() {
        assertEquals(100f, PhysicsCalculator.dampVelocity(100f, -1f), 0f)
        assertEquals(100f, PhysicsCalculator.dampVelocity(100f, 1f, 0f), 0f)
        assertEquals(100f, PhysicsCalculator.dampVelocity(100f, 1f, -2f), 0f)
    }

    @Test fun `advance applies delta wraps and clamps extreme velocity`() {
        assertEquals(12f, PhysicsCalculator.advance(10f, 100f, .02f, 100f), .001f)
        assertEquals(8f, PhysicsCalculator.advance(10f, -100f, .02f, 100f), .001f)
        assertEquals(3f, PhysicsCalculator.advance(98f, 100f, .05f, 100f), .001f)
        assertEquals(97f, PhysicsCalculator.advance(2f, -100f, .05f, 100f), .001f)
        assertEquals(410f, PhysicsCalculator.advance(10f, 100000f, .05f, 1000f), .001f)
        assertEquals(610f, PhysicsCalculator.advance(10f, -100000f, .05f, 1000f), .001f)
    }

    @Test fun `advance ignores invalid delta and normalizes stationary offset`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { delta ->
            assertEquals(25f, PhysicsCalculator.advance(25f, 100f, delta, 100f), 0f)
        }
        assertEquals(25f, PhysicsCalculator.advance(125f, 0f, 0f, 100f), 0f)
    }

    @Test fun `cyclic distance is symmetric and uses shortest boundary path`() {
        assertEquals(10f, PhysicsCalculator.cyclicDistance(95f, 5f, 100f), .001f)
        assertEquals(10f, PhysicsCalculator.cyclicDistance(5f, 95f, 100f), .001f)
        assertEquals(10f, PhysicsCalculator.cyclicDistance(-5f, 105f, 100f), .001f)
        assertEquals(20f, PhysicsCalculator.cyclicDistance(10f, 30f, 100f), .001f)
        assertEquals(50f, PhysicsCalculator.cyclicDistance(0f, 50f, 100f), .001f)
        assertEquals(0f, PhysicsCalculator.cyclicDistance(10f, 110f, 100f), .001f)
    }

    @Test fun `crosshair handles both edges and zero or negative width`() {
        assertTrue(PhysicsCalculator.crossesTarget(50f, 40f, 20f, 100f))
        assertTrue(PhysicsCalculator.crossesTarget(50f, 50f, 0f, 100f))
        assertFalse(PhysicsCalculator.crossesTarget(50f, 51f, 0f, 100f))
        assertFalse(PhysicsCalculator.crossesTarget(50f, 50f, -1f, 100f))
    }

    @Test fun `key index accounts for offset and exact period boundaries`() {
        assertEquals(0, PhysicsCalculator.keyAt(0f, 0f, 20f, 5))
        assertEquals(1, PhysicsCalculator.keyAt(20f, 0f, 20f, 5))
        assertEquals(0, PhysicsCalculator.keyAt(100f, 0f, 20f, 5))
        assertEquals(4, PhysicsCalculator.keyAt(-1f, 0f, 20f, 5))
        assertEquals(0, PhysicsCalculator.keyAt(20f, 10f, 20f, 5))
        assertEquals(4, PhysicsCalculator.keyAt(9f, 10f, 20f, 5))
        assertEquals(0, PhysicsCalculator.keyAt(0f, -10f, 20f, 5))
        assertEquals(0, PhysicsCalculator.keyAt(20f, 110f, 20f, 5))
    }

    @Test fun `key index includes key edges and rejects invalid tracks`() {
        assertEquals(0, PhysicsCalculator.keyAt(5f, 0f, 20f, 5, 10f))
        assertEquals(0, PhysicsCalculator.keyAt(15f, 0f, 20f, 5, 10f))
        assertNull(PhysicsCalculator.keyAt(10f, 0f, 20f, 0))
        assertNull(PhysicsCalculator.keyAt(10f, 0f, 20f, -1))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { spacing ->
            assertNull(PhysicsCalculator.keyAt(10f, 0f, spacing, 5))
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { coordinate ->
            assertNull(PhysicsCalculator.keyAt(coordinate, 0f, 20f, 5))
        }
    }
}
