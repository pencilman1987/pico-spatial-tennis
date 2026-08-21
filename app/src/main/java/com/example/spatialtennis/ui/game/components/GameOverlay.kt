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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.GameMode
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.PlayerId
import com.example.spatialtennis.ui.game.GameEvent
import com.example.spatialtennis.ui.game.GameUiState
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

@Composable
fun GameOverlay(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.game.phase) {
        GamePhase.MENU -> MenuOverlay(state, onEvent, modifier)
        GamePhase.POINT_END -> PointEndOverlay(state, modifier)
        GamePhase.PAUSED -> PauseOverlay(onEvent, modifier)
        GamePhase.MATCH_END -> ResultOverlay(state, onEvent, modifier)
        else -> Box(Modifier.size(1.dp))
    }
}

@Composable
private fun MenuOverlay(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier,
) {
    val selectedOpponent = state.selectedOpponent
    OverlayCard(width = 580, height = 620, contentSpacing = 8, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandCrest()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ISLAND RALLY",
                    color = PicoTheme.colorScheme.labelPrimary,
                    style = PicoTheme.typography.titleLarge,
                )
                Text(
                    "PICO 动物岛单机巡回赛",
                    color = PicoTheme.colorScheme.labelSecondary,
                    style = PicoTheme.typography.labelMedium,
                )
            }
            StatusPill(if (state.controllersReady) "手柄就绪" else "手柄检查")
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(PicoTheme.colorScheme.dividerLine))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("今日岛民杯", color = PicoTheme.colorScheme.alert, style = PicoTheme.typography.labelMedium)
                Text("泰格虎，选择挑战者", color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.titleMedium)
            }
            Text("01 — 03", color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
        }

        AiArchetype.entries.forEachIndexed { index, opponent ->
            OpponentRowButton(
                opponent = opponent,
                ranking = index + 1,
                accent = opponentAccent(opponent),
                selected = opponent == selectedOpponent,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(GameEvent.SelectOpponent(opponent)) },
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PicoTheme.colorScheme.fillTertiary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("已选 · ${selectedOpponent.displayName}", color = opponentAccent(selectedOpponent), style = PicoTheme.typography.labelMedium)
                Text(selectedOpponent.weaknessLabel, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
            }
            Text("${opponentIcon(selectedOpponent)}  ${opponentDescriptor(selectedOpponent)}", style = PicoTheme.typography.labelMedium)
        }

        Button(
            onClick = { onEvent(GameEvent.StartQuickMatch(selectedOpponent)) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.alert,
                    contentColor = PicoTheme.colorScheme.fillPrimary,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("挑战 ${selectedOpponent.displayName}", style = PicoTheme.typography.titleSmall)
                    Text("单场练习赛 · 抢 7 分", style = PicoTheme.typography.labelMedium)
                }
                Text("开始比赛  →", style = PicoTheme.typography.labelMedium)
            }
        }

        Button(
            onClick = { onEvent(GameEvent.StartStarCup) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillSecondary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★  岛民巡回赛 · 三场单机连战", style = PicoTheme.typography.labelMedium)
                Text("橡果奖杯  →", color = PicoTheme.colorScheme.alert, style = PicoTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("冠军 ${state.cupProgress.championCount}  ·  进度 ${state.cupProgress.bestStage}/3", color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.labelMedium)
            Text(
                if (state.controllersReady) "指向并按扳机选择" else state.controllerStatus,
                color = if (state.controllersReady) PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.alert,
                style = PicoTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun OpponentRowButton(
    opponent: AiArchetype,
    ranking: Int,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .height(70.dp)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) accent else PicoTheme.colorScheme.dividerLine,
                    RoundedCornerShape(22.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (selected) PicoTheme.colorScheme.fillTertiary else PicoTheme.colorScheme.fillSecondary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(4.dp, 42.dp).clip(RoundedCornerShape(50)).background(accent))
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(opponentIcon(opponent), color = PicoTheme.colorScheme.fillPrimary, style = PicoTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("0$ranking  ${opponent.displayName}", color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.titleSmall)
                Text(opponent.styleLabel, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(opponentDescriptor(opponent), color = accent, style = PicoTheme.typography.labelMedium)
                Text(if (selected) "已选择  ✓" else "选择  →", color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PointEndOverlay(
    state: GameUiState,
    modifier: Modifier,
) {
    val playerWonPoint = state.game.lastPointWinner == PlayerId.PLAYER
    OverlayCard(width = 430, height = 154, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (playerWonPoint) "POINT WON" else "POINT LOST",
                    color = if (playerWonPoint) PicoTheme.colorScheme.passable else PicoTheme.colorScheme.error,
                    style = PicoTheme.typography.labelMedium,
                )
                Text(state.game.message, style = PicoTheme.typography.titleMedium)
            }
            Text(
                "${state.game.scorePlayer} : ${state.game.scoreAi}",
                style = PicoTheme.typography.displaySmall,
            )
        }
    }
}

@Composable
private fun PauseOverlay(
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier,
) {
    OverlayCard(width = 520, height = 300, modifier = modifier) {
        StatusPill("MATCH PAUSED")
        Text("球场暂停", style = PicoTheme.typography.headlineLarge)
        Text("重新站位，准备下一拍", style = PicoTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onEvent(GameEvent.TogglePause) }) { Text("继续比赛") }
            Button(
                onClick = { onEvent(GameEvent.Restart) },
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary),
            ) { Text("重开") }
            Button(
                onClick = { onEvent(GameEvent.ReturnToMenu) },
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary),
            ) { Text("主页") }
        }
    }
}

@Composable
private fun ResultOverlay(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier,
) {
    val won = state.game.winner == PlayerId.PLAYER
    val stars =
        if (!won) {
            0
        } else {
            1 +
                (if (state.game.scorePlayer - state.game.scoreAi >= 3) 1 else 0) +
                (if (state.game.bestRally >= 5) 1 else 0)
        }
    OverlayCard(width = 540, height = 370, modifier = modifier) {
        StatusPill(if (won) "MATCH COMPLETE" else "FINAL SCORE")
        Text(if (won) "胜利属于你" else "重新登场", style = PicoTheme.typography.headlineLarge)
        Text(
            "${state.game.scorePlayer}  :  ${state.game.scoreAi}",
            style = PicoTheme.typography.displaySmall,
        )
        Text(
            if (won) "★".repeat(stars) + "☆".repeat(3 - stars) else "观察落点环，提前移动",
            color = if (won) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text("最佳回合  ${state.game.bestRally} 拍", style = PicoTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.mode == GameMode.STAR_CUP && won) {
                Button(onClick = { onEvent(GameEvent.ContinueCup) }) {
                    Text(if (state.cupIndex >= 2) "领取冠军" else "下一场")
                }
            } else {
                Button(onClick = { onEvent(GameEvent.Restart) }) { Text("再战一场") }
            }
            Button(
                onClick = { onEvent(GameEvent.ReturnToMenu) },
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary),
            ) { Text("返回主页") }
        }
    }
}

@Composable
private fun BrandCrest() {
    Box(
        modifier =
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PicoTheme.colorScheme.alert)
                .border(1.dp, PicoTheme.colorScheme.labelPrimaryLight, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("森", color = PicoTheme.colorScheme.fillPrimary, style = PicoTheme.typography.titleMedium)
    }
}

@Composable
private fun StatusPill(label: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PicoTheme.colorScheme.fillTertiary)
                .border(1.dp, PicoTheme.colorScheme.dividerLine, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = PicoTheme.colorScheme.labelSecondary, style = PicoTheme.typography.labelMedium)
    }
}

@Composable
private fun OverlayCard(
    width: Int,
    height: Int,
    contentSpacing: Int = 12,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier =
            Modifier
                .size(width.dp, height.dp)
                .then(modifier)
                .clip(shape)
                .background(PicoTheme.colorScheme.fillPrimary)
                .border(1.dp, PicoTheme.colorScheme.dividerLine, shape)
                .padding(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(contentSpacing.dp),
            content = { content() },
        )
    }
}

@Composable
private fun opponentAccent(opponent: AiArchetype): Color =
    when (opponent) {
        AiArchetype.SPEEDSTER -> PicoTheme.colorScheme.interaction
        AiArchetype.POWERHOUSE -> PicoTheme.colorScheme.error
        AiArchetype.TRICKSTER -> PicoTheme.colorScheme.passable
    }

private fun opponentDescriptor(opponent: AiArchetype): String =
    when (opponent) {
        AiArchetype.SPEEDSTER -> "兔 · 追线"
        AiArchetype.POWERHOUSE -> "熊 · 重炮"
        AiArchetype.TRICKSTER -> "狐 · 尾旋"
    }

private fun opponentIcon(opponent: AiArchetype): String =
    when (opponent) {
        AiArchetype.SPEEDSTER -> "兔"
        AiArchetype.POWERHOUSE -> "熊"
        AiArchetype.TRICKSTER -> "狐"
    }
