package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.utils.makeTimeString

fun registerSeekBarComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.SEEK_BAR.id) { _, _, playerConnection, _, position, duration, isSeeking, onSeek, onSeekEnd, sliderPosition, modifier, style ->
        SeekBarComponent(position, duration, isSeeking, onSeek, onSeekEnd, sliderPosition, modifier, style.textSizeScale, style.showTimeOnSeekBar)
    }
    PlayerComponentRegistry.register(PlayerComponentType.TIME_DISPLAY.id) { _, _, _, _, position, duration, _, _, _, _, modifier, style ->
        TimeDisplayComponent(position, duration, modifier, style.textSizeScale)
    }
}

@Composable
fun SeekBarComponent(
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    sliderPosition: Long?,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1f,
    showTimeOnSeekBar: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTimeOnSeekBar) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Text(
                    text = makeTimeString(if (isSeeking) (sliderPosition ?: position) else position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (11 * textSizeScale).sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = makeTimeString(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (11 * textSizeScale).sp,
                    textAlign = TextAlign.End,
                )
            }
        }

        var sliderFloatValue by remember(position, sliderPosition) {
            mutableFloatStateOf(
                if (duration > 0) {
                    ((if (isSeeking) (sliderPosition ?: position) else position).toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                } else 0f
            )
        }

        Slider(
            value = sliderFloatValue,
            onValueChange = { value ->
                sliderFloatValue = value
                onSeek((value * duration).toLong())
            },
            onValueChangeFinished = onSeekEnd,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
fun TimeDisplayComponent(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1f,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${makeTimeString(position)} / ${makeTimeString(duration)}",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * textSizeScale,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
