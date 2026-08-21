package com.example.spatialtennis.domain.usecase

import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.BallFlight
import com.example.spatialtennis.domain.model.BallState
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.HitQuality
import com.example.spatialtennis.domain.model.PlayerId
import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.ShotCommand
import com.example.spatialtennis.domain.model.Vec3
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TickTennisGameUseCaseTest {
    private val useCase = TickTennisGameUseCase(Random(7))

    @Test
    fun initialStateStartsInMenuWithRequestedOpponent() {
        val state = useCase.initialState(AiArchetype.TRICKSTER)

        assertEquals(GamePhase.MENU, state.phase)
        assertEquals(AiArchetype.TRICKSTER, state.aiArchetype)
        assertEquals(0, state.scorePlayer)
    }

    @Test
    fun thumbstickInputMovesPlayerWithinCourt() {
        val state = useCase.initialState(startInMenu = false)
        val moved = useCase(state, 0.05f, PlayerInput(moveX = 1f, moveZ = 1f))

        assertTrue(moved.player.position.x > state.player.position.x)
        assertTrue(moved.player.position.z > state.player.position.z)
    }

    @Test
    fun triggerInputLaunchesPlayerServe() {
        val state = useCase.initialState(startInMenu = false)
        val served = useCase(state, 0.016f, PlayerInput(hit = true, command = ShotCommand.ATTACK))

        assertEquals(GamePhase.RALLY, served.phase)
        assertNotNull(served.ball.flight)
        assertEquals(ShotCommand.ATTACK, served.ball.flight?.command)
    }

    @Test
    fun pauseDuringServeResumesServeInsteadOfSkippingToRally() {
        val serving = useCase.initialState(startInMenu = false)

        val paused = useCase(serving, 0.016f, PlayerInput(pausePressed = true))
        val resumed = useCase(paused, 0.016f, PlayerInput(pausePressed = true))

        assertEquals(GamePhase.PAUSED, paused.phase)
        assertEquals(GamePhase.SERVE, resumed.phase)
    }

    @Test
    fun fullEnergyTurnsNextSuccessfulHitIntoSkillShot() {
        val serving = useCase.initialState(startInMenu = false).copy(skillEnergy = 100f)

        val served = useCase(serving, 0.016f, PlayerInput(hit = true, command = ShotCommand.ATTACK))

        assertTrue(served.ball.flight?.isSkillShot == true)
        assertEquals(0f, served.skillEnergy)
        assertEquals("必杀击球！", served.message)
    }

    @Test
    fun earlySwingIsBufferedUntilBallReachesPlayer() {
        val base = useCase.initialState(startInMenu = false)
        val flight =
            BallFlight(
                from = Vec3(0f, 0.76f, 4.75f),
                bounce = Vec3(0f, 0.16f, -4.65f),
                end = Vec3(0f, 0.16f, -6.1f),
                elapsed = 0.5f,
                durationToBounce = 0.86f,
                durationAfterBounce = 0.38f,
                owner = PlayerId.AI,
                command = ShotCommand.SAFE,
            )
        var state =
            base.copy(
                phase = GamePhase.RALLY,
                ball = BallState(flight.from, flight),
            )

        state = useCase(state, 0.05f, PlayerInput(hit = true, command = ShotCommand.DEFENSE))
        repeat(5) {
            if (state.ball.flight?.owner == PlayerId.AI) state = useCase(state, 0.05f, PlayerInput())
        }

        assertEquals(PlayerId.PLAYER, state.ball.flight?.owner)
        assertEquals(ShotCommand.DEFENSE, state.ball.flight?.command)
    }

    @Test
    fun centeredReturnAwardsPerfectFeedbackAndBonusEnergy() {
        val base = useCase.initialState(startInMenu = false)
        val flight =
            BallFlight(
                from = Vec3(0f, 0.76f, 4.75f),
                bounce = Vec3(0f, 0.16f, -4.65f),
                end = Vec3(0f, 0.16f, -6.1f),
                elapsed = 0.82f,
                durationToBounce = 0.86f,
                durationAfterBounce = 0.38f,
                owner = PlayerId.AI,
                command = ShotCommand.SAFE,
            )
        val returning =
            base.copy(
                phase = GamePhase.RALLY,
                ball = BallState(Vec3(0f, 0.72f, base.player.position.z), flight),
            )

        val hit = useCase(returning, 0.016f, PlayerInput(hit = true, command = ShotCommand.SAFE))

        assertEquals(PlayerId.PLAYER, hit.ball.flight?.owner)
        assertEquals(HitQuality.PERFECT, hit.lastHitQuality)
        assertTrue(hit.skillEnergy > 16f)
        assertTrue(hit.hitFeedbackTimer > 0f)
    }

    @Test
    fun bestRallyPersistsWhenPreparingNextServe() {
        val serving = useCase.initialState(startInMenu = false).copy(rallyCount = 5, bestRally = 5)
        val scored = serving.copy(phase = GamePhase.POINT_END, pointTimer = 1.24f)

        val nextServe = useCase(scored, 0.05f, PlayerInput())

        assertEquals(GamePhase.SERVE, nextServe.phase)
        assertEquals(0, nextServe.rallyCount)
        assertEquals(5, nextServe.bestRally)
    }
}
