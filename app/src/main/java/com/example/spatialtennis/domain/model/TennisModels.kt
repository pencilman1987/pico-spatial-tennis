package com.example.spatialtennis.domain.model

enum class GamePhase {
    MENU,
    SERVE,
    RALLY,
    POINT_END,
    MATCH_END,
    PAUSED,
}

enum class GameMode {
    QUICK_MATCH,
    STAR_CUP,
}

enum class PlayerId {
    PLAYER,
    AI,
}

enum class AiArchetype(
    val displayName: String,
    val styleLabel: String,
    val weaknessLabel: String,
    val speed: Float,
    val reactionSeconds: Float,
    val mistakeChance: Float,
) {
    SPEEDSTER("露比兔", "灵敏型 · 林间追风", "弱点：力量一般", 6.0f, 0.12f, 0.07f),
    POWERHOUSE("柏鲁熊", "力量型 · 蜂蜜重炮", "弱点：移动较慢", 3.35f, 0.28f, 0.1f),
    TRICKSTER("菲妮狐", "技巧型 · 尾旋变化", "弱点：回球失误较多", 4.45f, 0.2f, 0.16f),
}

enum class ShotCommand {
    SAFE,
    ATTACK,
    DEFENSE,
}

enum class HitQuality {
    NONE,
    GOOD,
    PERFECT,
}

data class Vec2(
    val x: Float,
    val z: Float,
)

data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class CharacterState(
    val position: Vec2,
    val swingTimer: Float = 0f,
)

data class BallFlight(
    val from: Vec3,
    val bounce: Vec3,
    val end: Vec3,
    val elapsed: Float,
    val durationToBounce: Float,
    val durationAfterBounce: Float,
    val owner: PlayerId,
    val command: ShotCommand,
    val isSkillShot: Boolean = false,
)

data class BallState(
    val position: Vec3,
    val flight: BallFlight? = null,
)

data class TennisGameState(
    val phase: GamePhase,
    val scorePlayer: Int,
    val scoreAi: Int,
    val targetScore: Int,
    val serving: PlayerId,
    val winner: PlayerId?,
    val message: String,
    val aiArchetype: AiArchetype,
    val player: CharacterState,
    val ai: CharacterState,
    val ball: BallState,
    val skillEnergy: Float,
    val rallyCount: Int,
    val bestRally: Int = 0,
    val lastHitQuality: HitQuality = HitQuality.NONE,
    val hitFeedbackTimer: Float = 0f,
    val lastPointWinner: PlayerId? = null,
    val pointTimer: Float = 0f,
    val serveTimer: Float = 0f,
    val queuedShot: ShotCommand? = null,
    val hitBufferTimer: Float = 0f,
    val resumePhase: GamePhase? = null,
)

data class PlayerInput(
    val moveX: Float = 0f,
    val moveZ: Float = 0f,
    val hit: Boolean = false,
    val command: ShotCommand = ShotCommand.SAFE,
    val pausePressed: Boolean = false,
    val menuPressed: Boolean = false,
)

object CourtDimensions {
    const val HALF_WIDTH = 3.65f
    const val HALF_LENGTH = 6.15f
    const val PLAYER_HOME_Z = -4.75f
    const val AI_HOME_Z = 4.75f
    const val PLAYER_MIN_Z = -5.55f
    const val PLAYER_MAX_Z = -1.05f
    const val AI_MIN_Z = 1.05f
    const val AI_MAX_Z = 5.55f
}
