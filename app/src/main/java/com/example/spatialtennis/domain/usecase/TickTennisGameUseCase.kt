package com.example.spatialtennis.domain.usecase

import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.BallFlight
import com.example.spatialtennis.domain.model.BallState
import com.example.spatialtennis.domain.model.CharacterState
import com.example.spatialtennis.domain.model.CourtDimensions
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.HitQuality
import com.example.spatialtennis.domain.model.PlayerId
import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.ShotCommand
import com.example.spatialtennis.domain.model.TennisGameState
import com.example.spatialtennis.domain.model.Vec2
import com.example.spatialtennis.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class TickTennisGameUseCase(
    private val random: Random = Random.Default,
) {
    fun initialState(
        aiArchetype: AiArchetype = AiArchetype.SPEEDSTER,
        targetScore: Int = 7,
        startInMenu: Boolean = true,
    ): TennisGameState =
        TennisGameState(
            phase = if (startInMenu) GamePhase.MENU else GamePhase.SERVE,
            scorePlayer = 0,
            scoreAi = 0,
            targetScore = targetScore,
            serving = PlayerId.PLAYER,
            winner = null,
            message = if (startInMenu) "选择单机模式" else "扣动扳机发球",
            aiArchetype = aiArchetype,
            player = CharacterState(Vec2(0f, CourtDimensions.PLAYER_HOME_Z)),
            ai = CharacterState(Vec2(0f, CourtDimensions.AI_HOME_Z)),
            ball = BallState(Vec3(0.38f, 0.72f, CourtDimensions.PLAYER_HOME_Z + 0.4f)),
            skillEnergy = 0f,
            rallyCount = 0,
        )

    operator fun invoke(
        state: TennisGameState,
        deltaSeconds: Float,
        input: PlayerInput,
    ): TennisGameState {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (input.pausePressed && state.phase !in setOf(GamePhase.MENU, GamePhase.MATCH_END)) {
            return if (state.phase == GamePhase.PAUSED) {
                state.copy(
                    phase = state.resumePhase ?: GamePhase.SERVE,
                    resumePhase = null,
                    message = "继续比赛",
                )
            } else {
                state.copy(
                    phase = GamePhase.PAUSED,
                    resumePhase = state.phase,
                    message = "比赛暂停",
                )
            }
        }
        if (state.phase in setOf(GamePhase.MENU, GamePhase.MATCH_END, GamePhase.PAUSED)) return state

        var next = moveCharacters(state, dt, input)
        next = bufferPlayerShot(next, input, dt)
        next = next.copy(
            player = next.player.copy(swingTimer = max(0f, next.player.swingTimer - dt)),
            ai = next.ai.copy(swingTimer = max(0f, next.ai.swingTimer - dt)),
            hitFeedbackTimer = max(0f, next.hitFeedbackTimer - dt),
        )

        if (next.phase == GamePhase.POINT_END) {
            val elapsed = next.pointTimer + dt
            return if (elapsed >= 1.25f) prepareServe(next) else next.copy(pointTimer = elapsed)
        }

        if (next.phase == GamePhase.SERVE) {
            val serveTimer = next.serveTimer + dt
            next = next.copy(serveTimer = serveTimer, ball = BallState(serveBallPosition(next)))
            val shouldServe =
                if (next.serving == PlayerId.PLAYER) next.queuedShot != null else serveTimer >= 0.8f
            val command = if (next.serving == PlayerId.PLAYER) next.queuedShot ?: ShotCommand.SAFE else chooseAiShot(next.aiArchetype)
            return if (shouldServe) launchShot(next, next.serving, command) else next
        }

        val flight = next.ball.flight ?: return next
        val advanced = advanceBall(next, flight, dt)
        val advancedFlight = advanced.ball.flight ?: return advanced

        if (advancedFlight.owner == PlayerId.AI && advanced.queuedShot != null && canPlayerReturn(advanced)) {
            return launchShot(advanced, PlayerId.PLAYER, advanced.queuedShot)
        }
        if (advancedFlight.owner == PlayerId.PLAYER && canAiReturn(advanced)) {
            if (random.nextFloat() < advanced.aiArchetype.mistakeChance) {
                return launchShot(
                    advanced,
                    PlayerId.AI,
                    ShotCommand.DEFENSE,
                    weakReturn = true,
                )
            }
            return launchShot(advanced, PlayerId.AI, chooseAiShot(advanced.aiArchetype))
        }

        val totalDuration = advancedFlight.durationToBounce + advancedFlight.durationAfterBounce
        return if (advancedFlight.elapsed >= totalDuration) {
            awardPoint(advanced, advancedFlight.owner)
        } else {
            advanced
        }
    }

    private fun moveCharacters(
        state: TennisGameState,
        dt: Float,
        input: PlayerInput,
    ): TennisGameState {
        val playerSpeed = 4.65f
        val player =
            state.player.copy(
                position =
                    Vec2(
                        x = (state.player.position.x + input.moveX * playerSpeed * dt)
                            .coerceIn(-CourtDimensions.HALF_WIDTH + 0.3f, CourtDimensions.HALF_WIDTH - 0.3f),
                        z = (state.player.position.z + input.moveZ * playerSpeed * dt)
                            .coerceIn(CourtDimensions.PLAYER_MIN_Z, CourtDimensions.PLAYER_MAX_Z),
                    ),
            )
        val targetX = state.ball.position.x
        val targetZ =
            if (state.ball.position.z > 0f) {
                state.ball.position.z.coerceIn(CourtDimensions.AI_MIN_Z, CourtDimensions.AI_MAX_Z)
            } else {
                CourtDimensions.AI_HOME_Z
            }
        val aiSpeed = state.aiArchetype.speed
        val ai =
            state.ai.copy(
                position =
                    Vec2(
                        moveToward(state.ai.position.x, targetX, aiSpeed * dt)
                            .coerceIn(-CourtDimensions.HALF_WIDTH + 0.3f, CourtDimensions.HALF_WIDTH - 0.3f),
                        moveToward(state.ai.position.z, targetZ, aiSpeed * dt)
                            .coerceIn(CourtDimensions.AI_MIN_Z, CourtDimensions.AI_MAX_Z),
                    ),
            )
        return state.copy(player = player, ai = ai)
    }

    private fun launchShot(
        state: TennisGameState,
        hitter: PlayerId,
        command: ShotCommand,
        weakReturn: Boolean = false,
    ): TennisGameState {
        val isSkillShot = hitter == PlayerId.PLAYER && state.skillEnergy >= 100f
        val isPlayerReturn = hitter == PlayerId.PLAYER && state.ball.flight?.owner == PlayerId.AI
        val hitQuality =
            when {
                !isPlayerReturn -> HitQuality.NONE
                isSkillShot -> HitQuality.PERFECT
                else -> playerHitQuality(state)
            }
        val source = if (hitter == PlayerId.PLAYER) state.player.position else state.ai.position
        val direction = if (hitter == PlayerId.PLAYER) 1f else -1f
        val targetX =
            when {
                weakReturn -> source.x * -0.12f
                command == ShotCommand.ATTACK -> if (random.nextBoolean()) 2.8f else -2.8f
                command == ShotCommand.DEFENSE -> source.x * -0.55f
                else -> source.x * -0.35f
            }.coerceIn(-3.1f, 3.1f)
        val targetZ = direction * if (weakReturn) 4.15f else if (command == ShotCommand.DEFENSE) 5.2f else 4.65f
        val start = Vec3(source.x, 0.76f, source.z + direction * 0.4f)
        val bounce = Vec3(targetX, 0.16f, targetZ)
        val end = Vec3(targetX * 0.9f, 0.16f, direction * 6.1f)
        val baseDuration =
            when (command) {
                ShotCommand.ATTACK -> 0.7f
                ShotCommand.DEFENSE -> 1.08f
                ShotCommand.SAFE -> 0.86f
            }
        val duration =
            baseDuration *
                when {
                    isSkillShot -> 0.68f
                    weakReturn -> 1.28f
                    hitQuality == HitQuality.PERFECT && command == ShotCommand.ATTACK -> 0.88f
                    else -> 1f
                }
        val flight = BallFlight(start, bounce, end, 0f, duration, 0.38f, hitter, command, isSkillShot)
        val gainedEnergy =
            when (command) {
                ShotCommand.SAFE -> 16f
                ShotCommand.ATTACK -> 12f
                ShotCommand.DEFENSE -> 10f
            } + if (hitQuality == HitQuality.PERFECT) 10f else 0f
        val nextRallyCount = state.rallyCount + 1
        return state.copy(
            phase = GamePhase.RALLY,
            message =
                when {
                    isSkillShot -> "必杀击球！"
                    weakReturn -> "${state.aiArchetype.displayName} 回球偏软 · 机会球"
                    hitQuality == HitQuality.PERFECT -> "完美击球 · ${shotMessage(command)}"
                    hitter == PlayerId.PLAYER -> shotMessage(command)
                    else -> "${state.aiArchetype.displayName} 回球"
                },
            ball = BallState(start, flight),
            player = state.player.copy(swingTimer = if (hitter == PlayerId.PLAYER) 0.28f else state.player.swingTimer),
            ai = state.ai.copy(swingTimer = if (hitter == PlayerId.AI) 0.28f else state.ai.swingTimer),
            skillEnergy =
                when {
                    isSkillShot -> 0f
                    hitter == PlayerId.PLAYER -> min(100f, state.skillEnergy + gainedEnergy)
                    else -> state.skillEnergy
                },
            rallyCount = nextRallyCount,
            bestRally = max(state.bestRally, nextRallyCount),
            lastHitQuality = if (isPlayerReturn) hitQuality else state.lastHitQuality,
            hitFeedbackTimer = if (isPlayerReturn) HIT_FEEDBACK_SECONDS else state.hitFeedbackTimer,
            serveTimer = 0f,
            queuedShot = null,
            hitBufferTimer = 0f,
        )
    }

    private fun advanceBall(
        state: TennisGameState,
        flight: BallFlight,
        dt: Float,
    ): TennisGameState {
        val elapsed = flight.elapsed + dt
        val position =
            if (elapsed <= flight.durationToBounce) {
                val t = (elapsed / flight.durationToBounce).coerceIn(0f, 1f)
                val arc =
                    when (flight.command) {
                        ShotCommand.ATTACK -> 0.65f
                        ShotCommand.DEFENSE -> 2.25f
                        ShotCommand.SAFE -> 1.1f
                    }
                Vec3(
                    lerp(flight.from.x, flight.bounce.x, t),
                    lerp(flight.from.y, flight.bounce.y, t) + sin(t * Math.PI).toFloat() * arc,
                    lerp(flight.from.z, flight.bounce.z, t),
                )
            } else {
                val t = ((elapsed - flight.durationToBounce) / flight.durationAfterBounce).coerceIn(0f, 1f)
                Vec3(
                    lerp(flight.bounce.x, flight.end.x, t),
                    0.16f + sin(t * Math.PI).toFloat() * 0.55f,
                    lerp(flight.bounce.z, flight.end.z, t),
                )
            }
        return state.copy(ball = BallState(position, flight.copy(elapsed = elapsed)))
    }

    private fun canPlayerReturn(state: TennisGameState): Boolean =
        state.ball.position.z < -1f &&
            abs(state.ball.position.x - state.player.position.x) < 1.05f &&
            abs(state.ball.position.z - state.player.position.z) < 1.35f

    private fun canAiReturn(state: TennisGameState): Boolean {
        val flight = state.ball.flight ?: return false
        return flight.elapsed >= flight.durationToBounce * (0.74f + state.aiArchetype.reactionSeconds) &&
            state.ball.position.z > 1f &&
            abs(state.ball.position.x - state.ai.position.x) < 1.15f &&
            abs(state.ball.position.z - state.ai.position.z) < 1.45f
    }

    private fun playerHitQuality(state: TennisGameState): HitQuality {
        val horizontalDistance = abs(state.ball.position.x - state.player.position.x)
        val depthDistance = abs(state.ball.position.z - state.player.position.z)
        return if (horizontalDistance <= 0.38f && depthDistance <= 0.58f) {
            HitQuality.PERFECT
        } else {
            HitQuality.GOOD
        }
    }

    private fun awardPoint(
        state: TennisGameState,
        scorer: PlayerId,
        reason: String? = null,
    ): TennisGameState {
        val playerScore = state.scorePlayer + if (scorer == PlayerId.PLAYER) 1 else 0
        val aiScore = state.scoreAi + if (scorer == PlayerId.AI) 1 else 0
        val winner = matchWinner(playerScore, aiScore, state.targetScore)
        return state.copy(
            phase = if (winner == null) GamePhase.POINT_END else GamePhase.MATCH_END,
            scorePlayer = playerScore,
            scoreAi = aiScore,
            winner = winner,
            lastPointWinner = scorer,
            message = reason ?: if (scorer == PlayerId.PLAYER) "得分！" else "AI 得分",
            pointTimer = 0f,
            queuedShot = null,
            hitBufferTimer = 0f,
        )
    }

    private fun prepareServe(state: TennisGameState): TennisGameState {
        val serving = if (state.serving == PlayerId.PLAYER) PlayerId.AI else PlayerId.PLAYER
        val next = state.copy(
            phase = GamePhase.SERVE,
            serving = serving,
            message = if (serving == PlayerId.PLAYER) "扣动扳机发球" else "AI 发球",
            player = CharacterState(Vec2(0f, CourtDimensions.PLAYER_HOME_Z)),
            ai = CharacterState(Vec2(0f, CourtDimensions.AI_HOME_Z)),
            rallyCount = 0,
            pointTimer = 0f,
            serveTimer = 0f,
            queuedShot = null,
            hitBufferTimer = 0f,
            lastPointWinner = null,
        )
        return next.copy(ball = BallState(serveBallPosition(next)))
    }

    private fun serveBallPosition(state: TennisGameState): Vec3 {
        val source = if (state.serving == PlayerId.PLAYER) state.player.position else state.ai.position
        val direction = if (state.serving == PlayerId.PLAYER) 1f else -1f
        return Vec3(source.x + 0.38f, 0.72f, source.z + direction * 0.4f)
    }

    private fun chooseAiShot(archetype: AiArchetype): ShotCommand =
        when (archetype) {
            AiArchetype.SPEEDSTER -> if (random.nextFloat() < 0.65f) ShotCommand.SAFE else ShotCommand.DEFENSE
            AiArchetype.POWERHOUSE -> if (random.nextFloat() < 0.75f) ShotCommand.ATTACK else ShotCommand.SAFE
            AiArchetype.TRICKSTER -> if (random.nextFloat() < 0.65f) ShotCommand.DEFENSE else ShotCommand.SAFE
        }

    private fun matchWinner(
        playerScore: Int,
        aiScore: Int,
        targetScore: Int,
    ): PlayerId? {
        if (max(playerScore, aiScore) < targetScore || abs(playerScore - aiScore) < 2) return null
        return if (playerScore > aiScore) PlayerId.PLAYER else PlayerId.AI
    }

    private fun shotMessage(command: ShotCommand): String =
        when (command) {
            ShotCommand.SAFE -> "稳定回球"
            ShotCommand.ATTACK -> "蓄力强攻！"
            ShotCommand.DEFENSE -> "高弧吊球"
        }

    private fun bufferPlayerShot(
        state: TennisGameState,
        input: PlayerInput,
        dt: Float,
    ): TennisGameState =
        if (input.hit) {
            state.copy(queuedShot = input.command, hitBufferTimer = HIT_BUFFER_SECONDS)
        } else {
            val remaining = max(0f, state.hitBufferTimer - dt)
            state.copy(
                queuedShot = if (remaining > 0f) state.queuedShot else null,
                hitBufferTimer = remaining,
            )
        }

    private fun moveToward(current: Float, target: Float, amount: Float): Float =
        when {
            current < target -> min(current + amount, target)
            current > target -> max(current - amount, target)
            else -> current
        }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private companion object {
        const val HIT_BUFFER_SECONDS = 0.42f
        const val HIT_FEEDBACK_SECONDS = 0.62f
    }
}
