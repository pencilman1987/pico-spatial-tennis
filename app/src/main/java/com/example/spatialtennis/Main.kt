package com.example.spatialtennis

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.example.spatialtennis.ui.game.GameScreen
import com.example.spatialtennis.ui.theme.TournamentTheme

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            TournamentTheme {
                GameScreen()
            }
        }
    }
