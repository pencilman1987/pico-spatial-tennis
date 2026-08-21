package com.example.spatialtennis.spatial.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSwingDetectorTest {
    @Test
    fun `consistent fast path produces one swing`() {
        val detector = MotionSwingDetector()

        assertFalse(detector.addSample(sample(0f, 0)))
        assertFalse(detector.addSample(sample(0.04f, 25)))
        assertTrue(detector.addSample(sample(0.08f, 50)))
    }

    @Test
    fun `slow controller reposition does not swing`() {
        val detector = MotionSwingDetector()

        assertFalse(detector.addSample(sample(0f, 0)))
        assertFalse(detector.addSample(sample(0.02f, 50)))
        assertFalse(detector.addSample(sample(0.04f, 100)))
    }

    @Test
    fun `single tracking jump does not swing`() {
        val detector = MotionSwingDetector()

        assertFalse(detector.addSample(sample(0f, 0)))
        assertFalse(detector.addSample(sample(0.12f, 16)))
        assertFalse(detector.addSample(sample(0.12f, 32)))
    }

    @Test
    fun `opposite direction jitter does not swing`() {
        val detector = MotionSwingDetector()

        assertFalse(detector.addSample(sample(0f, 0)))
        assertFalse(detector.addSample(sample(0.05f, 20)))
        assertFalse(detector.addSample(sample(0f, 40)))
    }

    private fun sample(
        x: Float,
        milliseconds: Long,
    ) = MotionSample(x = x, y = 0f, z = 0f, timestampNanos = milliseconds * 1_000_000L)
}
