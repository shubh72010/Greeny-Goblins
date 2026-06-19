package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.ui.player.modular.LocalModularButtonShape
import moe.rukamori.archivetune.ui.player.modular.LocalPlayerButtonStyle
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType

fun registerPlaybackControlsComponents() {
    PlayerComponentRegistry.register(PlayerComponentType.PLAY_PAUSE.id) { _, _, playerConnection, isPlaying, _, _, _, _, _, _, modifier, style ->
        PlayPauseButton(playerConnection, isPlaying, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.PLAY_NEXT.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        NextButton(playerConnection, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.PLAY_PREVIOUS.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        PreviousButton(playerConnection, modifier, style.playButtonScale)
    }
}

@Composable
fun PlayPauseButton(
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val containerColor = if (buttonStyle == PlayerButtonsStyle.SECONDARY)
        MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.player.togglePlayPause()
            },
            modifier = Modifier.size((56 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
            ),
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.pause else R.drawable.play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size((28 * playButtonScale).dp),
            )
        }
    }
}

@Composable
fun NextButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val containerColor = if (buttonStyle == PlayerButtonsStyle.SECONDARY)
        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.seekToNext()
            },
            modifier = Modifier.size((48 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = "Next",
                modifier = Modifier.size((24 * playButtonScale).dp),
            )
        }
    }
}

@Composable
fun PreviousButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val containerColor = if (buttonStyle == PlayerButtonsStyle.SECONDARY)
        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.seekToPrevious()
            },
            modifier = Modifier.size((48 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = "Previous",
                modifier = Modifier.size((24 * playButtonScale).dp),
            )
        }
    }
}
