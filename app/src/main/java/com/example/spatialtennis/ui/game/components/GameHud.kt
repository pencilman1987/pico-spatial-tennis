package com.example.spatialtennis.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.HitQuality
import com.example.spatialtennis.ui.game.GameUiState
import com.example.spatialtennis.ui.game.TutorialStep
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

@Composable
fun GameHud(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    if (state.game.phase !in setOf(GamePhase.SERVE, GamePhase.RALLY, GamePhase.POINT_END)) return

    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier =
            Modifier
                .size(620.dp, 112.dp)
                .then(modifier)
                .clip(shape)
                .background(PicoTheme.colorScheme.fillPrimary)
                .border(1.dp, PicoTheme.colorScheme.dividerLine, shape)
                .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactScoreSide(
                    label = "你",
                    name = "泰格虎",
                    score = state.game.scorePlayer,
                    accent = PicoTheme.colorScheme.alert,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ISLAND RALLY", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
                    Text("抢${state.game.targetScore} · 领先 2 分", style = PicoTheme.typography.labelMedium)
                }
                CompactScoreSide(
                    label = "AI",
                    name = state.game.aiArchetype.displayName,
                    score = state.game.scoreAi,
                    accent = PicoTheme.colorScheme.interaction,
                    reverse = true,
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(PicoTheme.colorScheme.dividerLine))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.tutorialStep != TutorialStep.COMPLETE) {
                    TutorialPrompt(state.tutorialStep, Modifier.weight(1f))
                } else {
                    MatchFeedback(state, Modifier.weight(1f))
                }
                EnergyChip(state)
            }
        }
    }
}

@Composable
private fun TutorialPrompt(
    step: TutorialStep,
    modifier: Modifier = Modifier,
) {
    val (number, label) =
        when (step) {
            TutorialStep.MOVE -> "1/3" to "左摇杆移动站位"
            TutorialStep.SWING -> "2/3" to "挥动右手柄击球"
            TutorialStep.DIRECTION -> "3/3" to "按 A 强攻 · B 防守"
            TutorialStep.COMPLETE -> "" to ""
        }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedbackTag(number, PicoTheme.colorScheme.interaction)
        Text(label, color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.labelMedium)
    }
}

@Composable
private fun MatchFeedback(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            state.game.hitFeedbackTimer > 0f && state.game.lastHitQuality != HitQuality.NONE ->
                FeedbackTag(
                    label = if (state.game.lastHitQuality == HitQuality.PERFECT) "完美 +10" else "好球",
                    accent =
                        if (state.game.lastHitQuality == HitQuality.PERFECT) {
                            PicoTheme.colorScheme.passable
                        } else {
                            PicoTheme.colorScheme.alert
                        },
                )
            state.game.rallyCount >= 3 ->
                FeedbackTag("回合 ${state.game.rallyCount}", PicoTheme.colorScheme.interaction)
        }
        Text(state.game.message, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
    }
}

@Composable
private fun EnergyChip(state: GameUiState) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            if (state.game.skillEnergy >= 100f) "必杀已就绪" else "必杀 ${state.game.skillEnergy.toInt()}%",
            color = if (state.game.skillEnergy >= 100f) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { state.game.skillEnergy / 100f },
            modifier = Modifier.width(142.dp).height(6.dp),
        )
    }
}

@Composable
private fun FeedbackTag(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(accent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = PicoTheme.colorScheme.fillPrimary, style = PicoTheme.typography.labelMedium)
    }
}

@Composable
private fun CompactScoreSide(
    label: String,
    name: String,
    score: Int,
    accent: Color,
    reverse: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!reverse) Box(Modifier.size(4.dp, 36.dp).clip(RoundedCornerShape(50)).background(accent))
        if (!reverse) ScoreLabel(label, name, Alignment.Start)
        Text(score.toString(), color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.headlineLarge)
        if (reverse) ScoreLabel(label, name, Alignment.End)
        if (reverse) Box(Modifier.size(4.dp, 36.dp).clip(RoundedCornerShape(50)).background(accent))
    }
}

@Composable
private fun ScoreLabel(
    label: String,
    name: String,
    alignment: Alignment.Horizontal,
) {
    Column(horizontalAlignment = alignment) {
        Text(label, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
        Text(name, color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.labelMedium)
    }
}
