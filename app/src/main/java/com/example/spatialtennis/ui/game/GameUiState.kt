package com.example.spatialtennis.ui.game

import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.CupProgress
import com.example.spatialtennis.domain.model.GameMode
import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.TennisGameState

data class GameUiState(
    val game: TennisGameState,
    val mode: GameMode = GameMode.QUICK_MATCH,
    val selectedOpponent: AiArchetype = AiArchetype.SPEEDSTER,
    val cupIndex: Int = 0,
    val cupProgress: CupProgress = CupProgress(),
    val cupResultRecorded: Boolean = false,
    val controllerStatus: String = "等待 PICO 手柄",
    val controllersReady: Boolean = false,
    val tutorialStep: TutorialStep = TutorialStep.MOVE,
)

enum class TutorialStep {
    MOVE,
    SWING,
    DIRECTION,
    COMPLETE,
}

sealed interface GameEvent {
    data class SelectOpponent(val opponent: AiArchetype) : GameEvent

    data class StartQuickMatch(val opponent: AiArchetype) : GameEvent

    data object StartStarCup : GameEvent

    data class Frame(
        val deltaSeconds: Float,
        val input: PlayerInput,
        val controllerStatus: String,
        val controllersReady: Boolean = false,
    ) : GameEvent

    data object TogglePause : GameEvent

    data object Restart : GameEvent

    data object ContinueCup : GameEvent

    data object ReturnToMenu : GameEvent
}
