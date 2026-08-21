package com.example.spatialtennis.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spatialtennis.spatial.input.PicoControllerInputAdapter
import com.example.spatialtennis.spatial.scene.TennisScene
import com.example.spatialtennis.ui.game.components.GameHud
import com.example.spatialtennis.ui.game.components.GameOverlay
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
internal fun GameContent(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = remember { PicoControllerInputAdapter() }
    val scene = remember { TennisScene() }

    DisposableEffect(controller, scene) {
        controller.start()
        onDispose {
            controller.stop()
            scene.destroy()
        }
    }

    LaunchedEffect(scene) {
        // Let the playable court reach the compositor first, then append one small visual group
        // at a fixed cadence. Static Spatial scenes may render at 1 Hz, so this must not depend
        // on Choreographer frame callbacks.
        delay(120L)
        while (isActive && scene.buildNextDeferredBatch()) {
            delay(48L)
        }
    }

    LaunchedEffect(controller) {
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { frameTime ->
                if (lastFrame != 0L) {
                    val deltaSeconds = ((frameTime - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                    onEvent(
                        GameEvent.Frame(
                            deltaSeconds = deltaSeconds,
                            input = controller.snapshot(),
                            controllerStatus = controller.statusText(),
                            controllersReady = controller.controllersReady(),
                        ),
                    )
                }
                lastFrame = frameTime
            }
        }
    }

    SpatialView(
        modifier = modifier,
        initial = { content, attachments ->
            content.addEntity(scene.arenaRoot)
            content.addEntity(scene.trackedRacketRoot)

            attachments.entity(id = HUD_ATTACHMENT)?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, 1.95f, -2.25f))
                    setScaleVector(Vector3(1.12f, 1.12f, 1.12f))
                }
                content.addEntity(this)
            }
            attachments.entity(id = OVERLAY_ATTACHMENT)?.apply {
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(-0.3f, 1.46f, -1.25f))
                    setScaleVector(Vector3(1.08f, 1.08f, 1.08f))
                }
                content.addEntity(this)
            }
        },
        update = { _, _ ->
            scene.update(state.game, state.selectedOpponent, controller.latestRacketPose())
        },
        attachments = {
            AttachmentPanel(id = HUD_ATTACHMENT) {
                GameHud(state)
            }
            AttachmentPanel(id = OVERLAY_ATTACHMENT) {
                GameOverlay(state = state, onEvent = onEvent)
            }
        },
    )
}

private const val HUD_ATTACHMENT = "game-hud"
private const val OVERLAY_ATTACHMENT = "game-overlay"
