package com.example.spatialtennis.spatial.input

import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.ShotCommand
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerPose
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import java.util.concurrent.atomic.AtomicReference

class PicoControllerInputAdapter {
    private val provider = ControllerTrackingProvider()
    private val latestAction = AtomicReference<ControllerActionData?>(null)
    private var previousAction: ControllerActionData? = null
    private val motionSwingDetector = MotionSwingDetector()
    private var activeRacketHand = RacketHand.NONE
    private val listener = ControllerTrackingProvider.ControllerActionListener { action ->
        latestAction.set(action)
    }

    fun start() {
        provider.addControllerActionListener(listener)
        provider.start()
    }

    fun stop() {
        provider.removeControllerActionListener(listener)
        provider.stop()
        latestAction.set(null)
        previousAction = null
        motionSwingDetector.reset()
        activeRacketHand = RacketHand.NONE
    }

    fun snapshot(): PlayerInput {
        val current = latestAction.get() ?: return PlayerInput()
        val previous = previousAction
        val tracking = provider.latestData
        val leftConnected = tracking.left != null
        val rightConnected = tracking.right != null
        val hitAction = if (rightConnected) current.right else current.left
        val previousHitAction = if (rightConnected) previous?.right else previous?.left
        val moveAction = if (leftConnected) current.left else current.right
        val triggerDown = hitAction.triggerPressed && previousHitAction?.triggerPressed != true
        val attackDown = rightConnected && current.right.aButtonPressed && previous?.right?.aButtonPressed != true
        val defenseDown = rightConnected && current.right.bButtonPressed && previous?.right?.bButtonPressed != true
        val pauseDown =
            if (leftConnected) {
                current.left.xButtonPressed && previous?.left?.xButtonPressed != true
            } else {
                current.right.thumbstickPressed && previous?.right?.thumbstickPressed != true
            }
        val menuDown = leftConnected && current.left.yButtonPressed && previous?.left?.yButtonPressed != true
        val motionSwing = detectMotionSwing(latestRacketPose(), System.nanoTime())
        val command =
            when {
                attackDown -> ShotCommand.ATTACK
                defenseDown -> ShotCommand.DEFENSE
                else -> ShotCommand.SAFE
            }
        previousAction = current
        return PlayerInput(
            moveX = deadZone(moveAction.thumbstickValue.x),
            moveZ = deadZone(moveAction.thumbstickValue.y),
            hit = triggerDown || attackDown || defenseDown || motionSwing,
            command = command,
            pausePressed = pauseDown,
            menuPressed = menuDown,
        )
    }

    fun latestRacketPose(): ControllerPose? = provider.latestData.right ?: provider.latestData.left

    fun controllersReady(): Boolean = provider.latestData.left != null && provider.latestData.right != null

    fun statusText(): String =
        controllerStatusText(
            supportState = provider.supportState.toString(),
            leftConnected = provider.latestData.left != null,
            rightConnected = provider.latestData.right != null,
        )

    private fun deadZone(value: Float): Float = if (kotlin.math.abs(value) < 0.12f) 0f else value

    private fun detectMotionSwing(
        currentPose: ControllerPose?,
        nowNanos: Long,
    ): Boolean {
        val hand =
            when (currentPose) {
                null -> RacketHand.NONE
                provider.latestData.right -> RacketHand.RIGHT
                else -> RacketHand.LEFT
            }
        if (hand != activeRacketHand) {
            activeRacketHand = hand
            motionSwingDetector.reset()
        }
        if (currentPose == null) return false
        return motionSwingDetector.addSample(
            MotionSample(
                x = currentPose.position.x,
                y = currentPose.position.y,
                z = currentPose.position.z,
                timestampNanos = nowNanos,
            ),
        )
    }

    private enum class RacketHand {
        NONE,
        LEFT,
        RIGHT,
    }
}

internal fun controllerStatusText(
    supportState: String,
    leftConnected: Boolean,
    rightConnected: Boolean,
): String =
    when {
        supportState == "DEVICE_NOT_SUPPORTED" -> "当前设备不支持手柄追踪"
        supportState != "SUPPORTED" -> "正在检测 PICO 手柄"
        leftConnected && rightConnected -> "双手柄已连接 · 支持挥拍"
        rightConnected -> "仅右手柄 · 右摇杆移动 / 按下暂停"
        leftConnected -> "仅左手柄 · 扳机安全击球"
        else -> "等待 PICO 手柄连接"
    }
