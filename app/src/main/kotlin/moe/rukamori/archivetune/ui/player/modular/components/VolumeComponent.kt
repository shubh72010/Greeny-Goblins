package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType

fun registerVolumeComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.VOLUME_SLIDER.id) { _, _, playerConnection, _, _, _, _, _, _, _, modifier, style ->
        VolumeSliderComponent(playerConnection, modifier, style.playButtonScale)
    }
}

@Composable
fun VolumeSliderComponent(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    playButtonScale: Float = 1f,
) {
    val playerVolume = playerConnection.service.playerVolume.collectAsState()

    var sliderValue by remember(playerVolume.value) {
        mutableFloatStateOf(playerVolume.value)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = "Volume",
            modifier = Modifier.size((24 * playButtonScale).dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value
                playerConnection.service.playerVolume.value = value.coerceIn(0f, 1f)
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
