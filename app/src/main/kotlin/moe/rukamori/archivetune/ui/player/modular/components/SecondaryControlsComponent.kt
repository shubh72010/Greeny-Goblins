package moe.rukamori.archivetune.ui.player.modular.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.media3.common.Player
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.ui.player.modular.LocalModularButtonShape
import moe.rukamori.archivetune.ui.player.modular.LocalPlayerActions
import moe.rukamori.archivetune.ui.player.modular.LocalPlayerButtonStyle
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType

fun registerSecondaryControlsComponents() {
    PlayerComponentRegistry.register(PlayerComponentType.SHUFFLE.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        ShuffleButton(playerConnection, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.REPEAT.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        RepeatButton(playerConnection, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.LIKE.id) { _, metadata, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        LikeButton(metadata, playerConnection, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.QUEUE.id) { _, _, _, _, _, _, _, _, _, _, modifier, style ->
        QueueButton(modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.LYRICS_TOGGLE.id) { _, _, _, _, _, _, _, _, _, _, modifier, style ->
        LyricsToggleButton(modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.SHARE.id) { _, metadata, _, _, _, _, _, _, _, _, modifier, style ->
        ShareButton(metadata, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.SLEEP_TIMER.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        SleepTimerButton(playerConnection, modifier, style.playButtonScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.MORE_MENU.id) { _, _, _, _, _, _, _, _, _, _, modifier, style ->
        MoreMenuButton(modifier, style.playButtonScale)
    }
}

@Composable
fun ShuffleButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
            },
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (shuffleModeEnabled)
                    if (isSecondary) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else
                    if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = "Shuffle",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (shuffleModeEnabled)
                    if (isSecondary) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary
                else
                    if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RepeatButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val isActive = repeatMode != Player.REPEAT_MODE_OFF
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.player.toggleRepeatMode()
            },
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive)
                    if (isSecondary) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else
                    if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(
                    if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one
                    else R.drawable.repeat
                ),
                contentDescription = "Repeat",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isActive)
                    if (isSecondary) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary
                else
                    if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LikeButton(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val isLiked = currentSong?.song?.liked == true
    val haptic = LocalHapticFeedback.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.toggleLike()
            },
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isLiked)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                else
                    if (buttonStyle == PlayerButtonsStyle.SECONDARY) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(
                    if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                ),
                contentDescription = "Like",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isLiked)
                    MaterialTheme.colorScheme.error
                else
                    if (buttonStyle == PlayerButtonsStyle.SECONDARY) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun QueueButton(
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val actions = LocalPlayerActions.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = actions.onQueueClick,
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = "Queue",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LyricsToggleButton(
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val actions = LocalPlayerActions.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = actions.onLyricsClick,
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.lyrics),
                contentDescription = "Lyrics",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ShareButton(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val context = LocalContext.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${metadata?.id ?: ""}")
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = "Share",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SleepTimerButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val isActive = playerConnection.service.sleepTimer.isActive
    val actions = LocalPlayerActions.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY
    var timeLeft by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                timeLeft = if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                }
                delay(1000L)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = {
                if (isActive) {
                    playerConnection.service.sleepTimer.clear()
                } else {
                    actions.onSleepTimerClick()
                }
            },
            modifier = Modifier.width((48 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive)
                    if (isSecondary) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else
                    if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = "Sleep Timer",
                    modifier = Modifier.size((20 * playButtonScale).dp),
                    tint = if (isActive)
                        if (isSecondary) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    else
                        if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isActive) {
                    Text(
                        text = makeTimeString(timeLeft.coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (8 * playButtonScale).sp,
                        ),
                        color = if (isSecondary) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun MoreMenuButton(
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val actions = LocalPlayerActions.current
    val buttonShape = LocalModularButtonShape.current
    val buttonStyle = LocalPlayerButtonStyle.current
    val isSecondary = buttonStyle == PlayerButtonsStyle.SECONDARY
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = actions.onMenuClick,
            modifier = Modifier.size((40 * playButtonScale).dp),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = "More",
                modifier = Modifier.size((20 * playButtonScale).dp),
                tint = if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
