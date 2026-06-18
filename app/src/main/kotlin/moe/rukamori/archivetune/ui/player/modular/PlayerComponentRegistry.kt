package moe.rukamori.archivetune.ui.player.modular

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata

data class PlayerComponentStyle(
    val textSizeScale: Float = 1f,
    val playButtonScale: Float = 1f,
    val showTimeOnSeekBar: Boolean = true,
)

data class PlayerActions(
    val onQueueClick: () -> Unit = {},
    val onLyricsClick: () -> Unit = {},
    val onSleepTimerClick: () -> Unit = {},
)

val LocalPlayerActions = staticCompositionLocalOf { PlayerActions() }

typealias ComponentRenderer = @Composable (
    type: PlayerComponentType,
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    sliderPosition: Long?,
    modifier: Modifier,
    style: PlayerComponentStyle,
) -> Unit

object PlayerComponentRegistry {
    private val renderers = mutableMapOf<String, ComponentRenderer>()

    fun register(typeId: String, renderer: ComponentRenderer) {
        renderers[typeId] = renderer
    }

    fun get(typeId: String): ComponentRenderer? = renderers[typeId]

    fun has(typeId: String): Boolean = typeId in renderers

    fun unregister(typeId: String) {
        renderers.remove(typeId)
    }

    fun availableTypes(): Set<String> = renderers.keys

    fun clear() {
        renderers.clear()
    }
}
