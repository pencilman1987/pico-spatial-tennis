package com.example.spatialtennis.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.spatialtennis.data.repository.CupProgressRepository
import com.example.spatialtennis.data.repository.SharedPreferencesCupProgressRepository
import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.CupProgress
import com.example.spatialtennis.domain.model.GameMode
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.PlayerId
import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.STAR_CUP_OPPONENTS
import com.example.spatialtennis.domain.model.ShotCommand
import com.example.spatialtennis.domain.usecase.TickTennisGameUseCase
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel(
    private val tickGame: TickTennisGameUseCase,
    private val progressRepository: CupProgressRepository,
    initialTutorialCompleted: Boolean = false,
    private val recordTutorialCompleted: () -> Unit = {},
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            GameUiState(
                game = tickGame.initialState(),
                cupProgress = progressRepository.load(),
                tutorialStep = if (initialTutorialCompleted) TutorialStep.COMPLETE else TutorialStep.MOVE,
            ),
        )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.SelectOpponent ->
                _state.update { it.copy(selectedOpponent = event.opponent) }
            is GameEvent.StartQuickMatch -> startQuickMatch(event.opponent)
            GameEvent.StartStarCup -> startStarCup()
            is GameEvent.Frame -> advanceFrame(event)
            GameEvent.TogglePause -> togglePause()
            GameEvent.Restart -> restart()
            GameEvent.ContinueCup -> continueCup()
            GameEvent.ReturnToMenu -> returnToMenu()
        }
    }

    private fun startQuickMatch(opponent: AiArchetype) {
        _state.update {
            it.copy(
                mode = GameMode.QUICK_MATCH,
                selectedOpponent = opponent,
                cupIndex = 0,
                cupResultRecorded = false,
                game = tickGame.initialState(opponent, targetScore = 7, startInMenu = false),
            )
        }
    }

    private fun startStarCup() {
        val first = STAR_CUP_OPPONENTS.first()
        _state.update {
            it.copy(
                mode = GameMode.STAR_CUP,
                selectedOpponent = first.archetype,
                cupIndex = 0,
                cupResultRecorded = false,
                game = tickGame.initialState(first.archetype, first.targetScore, startInMenu = false),
            )
        }
    }

    private fun advanceFrame(event: GameEvent.Frame) {
        if (event.input.menuPressed) {
            returnToMenu()
            return
        }
        val tutorialWasIncomplete = _state.value.tutorialStep != TutorialStep.COMPLETE
        _state.update { current ->
            val before = current.game
            val after = tickGame(before, event.deltaSeconds, event.input)
            val handled = handleCupCompletion(current.copy(game = after))
            handled.copy(
                controllerStatus = event.controllerStatus,
                controllersReady = event.controllersReady,
                tutorialStep = advanceTutorial(current.tutorialStep, before.phase, event.input),
            )
        }
        if (tutorialWasIncomplete && _state.value.tutorialStep == TutorialStep.COMPLETE) {
            recordTutorialCompleted()
        }
    }

    private fun advanceTutorial(
        step: TutorialStep,
        phase: GamePhase,
        input: PlayerInput,
    ): TutorialStep {
        if (phase !in setOf(GamePhase.SERVE, GamePhase.RALLY, GamePhase.POINT_END)) return step
        return when (step) {
            TutorialStep.MOVE ->
                if (abs(input.moveX) > 0.2f || abs(input.moveZ) > 0.2f) TutorialStep.SWING else step
            TutorialStep.SWING ->
                when {
                    input.command != ShotCommand.SAFE -> TutorialStep.COMPLETE
                    input.hit -> TutorialStep.DIRECTION
                    else -> step
                }
            TutorialStep.DIRECTION ->
                if (input.command != ShotCommand.SAFE) TutorialStep.COMPLETE else step
            TutorialStep.COMPLETE -> TutorialStep.COMPLETE
        }
    }

    internal fun handleCupCompletion(state: GameUiState): GameUiState {
        if (state.mode != GameMode.STAR_CUP || state.game.phase != GamePhase.MATCH_END) return state
        if (state.game.winner != PlayerId.PLAYER) return state
        if (state.cupResultRecorded) return state
        val completedStage = state.cupIndex + 1
        val champion = completedStage >= STAR_CUP_OPPONENTS.size
        val progress =
            state.cupProgress.copy(
                championCount = state.cupProgress.championCount + if (champion) 1 else 0,
                bestStage = maxOf(state.cupProgress.bestStage, completedStage),
            )
        if (progress != state.cupProgress) progressRepository.save(progress)
        return state.copy(cupProgress = progress, cupResultRecorded = true)
    }

    private fun continueCup() {
        _state.update { current ->
            if (current.mode != GameMode.STAR_CUP || current.game.winner != PlayerId.PLAYER) return@update current
            val nextIndex = current.cupIndex + 1
            if (nextIndex >= STAR_CUP_OPPONENTS.size) {
                current.copy(game = tickGame.initialState(startInMenu = true), cupIndex = 0)
            } else {
                val next = STAR_CUP_OPPONENTS[nextIndex]
                current.copy(
                    cupIndex = nextIndex,
                    cupResultRecorded = false,
                    selectedOpponent = next.archetype,
                    game = tickGame.initialState(next.archetype, next.targetScore, startInMenu = false),
                )
            }
        }
    }

    private fun togglePause() {
        _state.update { current ->
            current.copy(
                game = tickGame(current.game, 0f, PlayerInput(pausePressed = true)),
            )
        }
    }

    private fun restart() {
        _state.update { current ->
            val target =
                if (current.mode == GameMode.STAR_CUP) {
                    STAR_CUP_OPPONENTS[current.cupIndex].targetScore
                } else {
                    7
                }
            current.copy(
                game = tickGame.initialState(current.selectedOpponent, target, startInMenu = false),
                cupResultRecorded = false,
            )
        }
    }

    private fun returnToMenu() {
        _state.update { current ->
            current.copy(
                game = tickGame.initialState(current.selectedOpponent, startInMenu = true),
                cupIndex = 0,
                cupResultRecorded = false,
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GameViewModel(
                        tickGame = TickTennisGameUseCase(),
                        progressRepository = SharedPreferencesCupProgressRepository(context.applicationContext),
                        initialTutorialCompleted =
                            context.applicationContext
                                .getSharedPreferences(TUTORIAL_PREFERENCES, Context.MODE_PRIVATE)
                                .getBoolean(TUTORIAL_COMPLETE_KEY, false),
                        recordTutorialCompleted = {
                            context.applicationContext
                                .getSharedPreferences(TUTORIAL_PREFERENCES, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(TUTORIAL_COMPLETE_KEY, true)
                                .apply()
                        },
                    ) as T
            }

        private const val TUTORIAL_PREFERENCES = "controller_tutorial"
        private const val TUTORIAL_COMPLETE_KEY = "completed"
    }
}
