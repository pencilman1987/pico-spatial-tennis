package com.example.spatialtennis.ui.game

import com.example.spatialtennis.data.repository.CupProgressRepository
import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.CupProgress
import com.example.spatialtennis.domain.model.GameMode
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.PlayerId
import com.example.spatialtennis.domain.model.PlayerInput
import com.example.spatialtennis.domain.model.ShotCommand
import com.example.spatialtennis.domain.usecase.TickTennisGameUseCase
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameViewModelTest {
    private lateinit var repository: FakeCupProgressRepository
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        repository = FakeCupProgressRepository(CupProgress(championCount = 2, bestStage = 3))
        viewModel = GameViewModel(TickTennisGameUseCase(Random(11)), repository)
    }

    @Test
    fun initLoadsLocalCupProgress() {
        assertEquals(2, viewModel.state.value.cupProgress.championCount)
        assertEquals(GamePhase.MENU, viewModel.state.value.game.phase)
    }

    @Test
    fun selectingOpponentUpdatesMenuState() {
        viewModel.onEvent(GameEvent.SelectOpponent(AiArchetype.POWERHOUSE))

        assertEquals(AiArchetype.POWERHOUSE, viewModel.state.value.selectedOpponent)
        assertEquals(GamePhase.MENU, viewModel.state.value.game.phase)
    }

    @Test
    fun startingQuickMatchEntersServeState() {
        viewModel.onEvent(GameEvent.StartQuickMatch(AiArchetype.TRICKSTER))

        assertEquals(GamePhase.SERVE, viewModel.state.value.game.phase)
        assertEquals(AiArchetype.TRICKSTER, viewModel.state.value.game.aiArchetype)
    }

    @Test
    fun frameInputMovesPlayer() {
        viewModel.onEvent(GameEvent.StartQuickMatch(AiArchetype.SPEEDSTER))
        val before = viewModel.state.value.game.player.position.x
        viewModel.onEvent(
            GameEvent.Frame(
                0.05f,
                PlayerInput(moveX = 1f),
                "双手柄已连接 · 支持挥拍",
                controllersReady = true,
            ),
        )

        assertTrue(viewModel.state.value.game.player.position.x > before)
        assertEquals("双手柄已连接 · 支持挥拍", viewModel.state.value.controllerStatus)
        assertTrue(viewModel.state.value.controllersReady)
    }

    @Test
    fun controllerTutorialAdvancesAndRecordsCompletion() {
        var completionCount = 0
        viewModel =
            GameViewModel(
                TickTennisGameUseCase(Random(11)),
                repository,
                recordTutorialCompleted = { completionCount += 1 },
            )
        viewModel.onEvent(GameEvent.StartQuickMatch(AiArchetype.SPEEDSTER))

        viewModel.onEvent(GameEvent.Frame(0.05f, PlayerInput(moveX = 1f), "手柄已连接"))
        assertEquals(TutorialStep.SWING, viewModel.state.value.tutorialStep)

        viewModel.onEvent(GameEvent.Frame(0.05f, PlayerInput(hit = true), "手柄已连接"))
        assertEquals(TutorialStep.DIRECTION, viewModel.state.value.tutorialStep)

        viewModel.onEvent(
            GameEvent.Frame(
                0.05f,
                PlayerInput(hit = true, command = ShotCommand.ATTACK),
                "手柄已连接",
            ),
        )
        assertEquals(TutorialStep.COMPLETE, viewModel.state.value.tutorialStep)
        assertEquals(1, completionCount)
    }

    @Test
    fun completedTutorialDoesNotReturnOnLaunch() {
        viewModel =
            GameViewModel(
                TickTennisGameUseCase(Random(11)),
                repository,
                initialTutorialCompleted = true,
            )

        assertEquals(TutorialStep.COMPLETE, viewModel.state.value.tutorialStep)
    }

    @Test
    fun pauseEventPausesActiveMatch() {
        viewModel.onEvent(GameEvent.StartQuickMatch(AiArchetype.SPEEDSTER))
        viewModel.onEvent(GameEvent.TogglePause)

        assertEquals(GamePhase.PAUSED, viewModel.state.value.game.phase)
    }

    @Test
    fun starCupChampionProgressIsRecordedOnlyOnce() {
        val matchEnd =
            TickTennisGameUseCase(Random(3)).initialState(startInMenu = false).copy(
                phase = GamePhase.MATCH_END,
                winner = PlayerId.PLAYER,
            )
        val result =
            GameUiState(
                game = matchEnd,
                mode = GameMode.STAR_CUP,
                cupIndex = 2,
                cupProgress = CupProgress(championCount = 2, bestStage = 2),
            )

        val firstPass = viewModel.handleCupCompletion(result)
        val secondPass = viewModel.handleCupCompletion(firstPass)

        assertEquals(3, secondPass.cupProgress.championCount)
        assertEquals(3, secondPass.cupProgress.bestStage)
        assertTrue(secondPass.cupResultRecorded)
        assertEquals(1, repository.saveCount)
    }

    private class FakeCupProgressRepository(
        private var progress: CupProgress,
    ) : CupProgressRepository {
        var saveCount: Int = 0
            private set

        override fun load(): CupProgress = progress

        override fun save(progress: CupProgress) {
            this.progress = progress
            saveCount += 1
        }
    }
}
