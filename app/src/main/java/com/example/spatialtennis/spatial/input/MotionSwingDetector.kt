package com.example.spatialtennis.spatial.input

import kotlin.math.sqrt

/**
 * Detects a deliberate controller swing from a short, directionally consistent motion path.
 * Using velocity and accumulated travel makes the result independent from render frame rate,
 * while the two-segment requirement rejects one-frame tracking jumps.
 */
internal class MotionSwingDetector(
    private val minimumSpeedMetersPerSecond: Float = 1.15f,
    private val minimumTravelMeters: Float = 0.075f,
    private val minimumDirectionDot: Float = 0.35f,
    private val maximumSampleGapNanos: Long = 120_000_000L,
    private val cooldownNanos: Long = 320_000_000L,
) {
    private var previousSample: MotionSample? = null
    private var candidateDirection: MotionVector? = null
    private var candidateTravel = 0f
    private var lastSwingNanos = Long.MIN_VALUE

    fun addSample(sample: MotionSample): Boolean {
        val previous = previousSample
        previousSample = sample
        if (previous == null) return false

        val elapsedNanos = sample.timestampNanos - previous.timestampNanos
        if (elapsedNanos <= 0L || elapsedNanos > maximumSampleGapNanos) {
            resetCandidate()
            return false
        }

        val displacement =
            MotionVector(
                x = sample.x - previous.x,
                y = sample.y - previous.y,
                z = sample.z - previous.z,
            )
        val distance = displacement.length()
        val speed = distance / (elapsedNanos / 1_000_000_000f)
        if (speed < minimumSpeedMetersPerSecond || distance <= 0f) {
            resetCandidate()
            return false
        }

        val direction = displacement.normalized(distance)
        val previousDirection = candidateDirection
        if (previousDirection == null) {
            candidateDirection = direction
            candidateTravel = distance
            return false
        }

        if (previousDirection.dot(direction) < minimumDirectionDot) {
            candidateDirection = direction
            candidateTravel = distance
            return false
        }

        candidateDirection = (previousDirection + direction).normalized()
        candidateTravel += distance
        val outsideCooldown =
            lastSwingNanos == Long.MIN_VALUE || sample.timestampNanos - lastSwingNanos >= cooldownNanos
        if (candidateTravel < minimumTravelMeters || !outsideCooldown) return false

        lastSwingNanos = sample.timestampNanos
        resetCandidate()
        return true
    }

    fun reset() {
        previousSample = null
        resetCandidate()
    }

    private fun resetCandidate() {
        candidateDirection = null
        candidateTravel = 0f
    }
}

internal data class MotionSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
)

private data class MotionVector(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(length: Float = length()): MotionVector =
        if (length <= 0f) this else MotionVector(x / length, y / length, z / length)

    fun dot(other: MotionVector): Float = x * other.x + y * other.y + z * other.z

    operator fun plus(other: MotionVector): MotionVector =
        MotionVector(x + other.x, y + other.y, z + other.z)
}
