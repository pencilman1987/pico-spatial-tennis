package com.example.spatialtennis.spatial.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerStatusTest {
    @Test
    fun `both hands are required for ready status`() {
        assertEquals(
            "双手柄已连接 · 支持挥拍",
            controllerStatusText("SUPPORTED", leftConnected = true, rightConnected = true),
        )
        assertEquals(
            "仅右手柄 · 右摇杆移动 / 按下暂停",
            controllerStatusText("SUPPORTED", leftConnected = false, rightConnected = true),
        )
        assertEquals(
            "仅左手柄 · 扳机安全击球",
            controllerStatusText("SUPPORTED", leftConnected = true, rightConnected = false),
        )
    }
}
