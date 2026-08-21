package com.example.spatialtennis.spatial.scene

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterFacingTest {
    @Test
    fun `near and far athletes face opposite directions toward the net`() {
        val nearCourtYaw = yawToFaceNet(sceneZ = 4.75f)
        val farCourtYaw = yawToFaceNet(sceneZ = -4.75f)

        assertEquals(180f, nearCourtYaw)
        assertEquals(0f, farCourtYaw)
        assertEquals(180f, kotlin.math.abs(nearCourtYaw - farCourtYaw))
    }
}
