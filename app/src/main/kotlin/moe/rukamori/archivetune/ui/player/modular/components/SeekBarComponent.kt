package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.ui.player.StyledPlaybackSlider
import moe.rukamori.archivetune.ui.player.modular.LocalShowTimeOnSeekBar
import moe.rukamori.archivetune.ui.player.modular.LocalSliderStyle
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.utils.makeTimeString

fun registerSeekBarComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.SEEK_BAR.id) { _, _, playerConnection, _, position, duration, isSeeking, onSeek, onSeekEnd, sliderPosition, modifier, style ->
        val showTimeOnSeekBar = LocalShowTimeOnSeekBar.current
        SeekBarComponent(position, duration, isSeeking, onSeek, onSeekEnd, sliderPosition, modifier, style.textSizeScale, showTimeOnSeekBar)
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
    val sliderStyle = LocalSliderStyle.current

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

        val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
        val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, maxOf(0f, safeDuration))

        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = safeValue,
            valueRange = 0f..maxOf(1f, safeDuration),
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekEnd,
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier = Modifier.fillMaxWidth(),
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
