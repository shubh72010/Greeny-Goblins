package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.fetchCanvasArtworkForPlayback
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

fun registerCanvasArtworkComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.CANVAS_ARTWORK.id) { _, metadata, playerConnection, isPlaying, _, _, _, _, _, _, modifier, _ ->
        val (canvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
        if (canvasEnabled) {
            CanvasArtworkComponent(metadata, playerConnection, isPlaying, modifier)
        }
    }
}

@Composable
fun CanvasArtworkComponent(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var canvasArtwork by remember(metadata?.id) {
        mutableStateOf<CanvasArtwork?>(null)
    }
    var fetchInFlight by remember(metadata?.id) {
        mutableStateOf(false)
    }

    val storefront = remember {
        val country = java.util.Locale.getDefault().country
        if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
    }

    LaunchedEffect(metadata?.id) {
        val meta = metadata ?: return@LaunchedEffect
        if (meta.title.isBlank() || meta.artists.firstOrNull()?.name.orEmpty().isBlank()) {
            canvasArtwork = null
            return@LaunchedEffect
        }

        CanvasArtworkPlaybackCache.get(meta.id)
            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            ?.let { cached ->
                canvasArtwork = cached
                return@LaunchedEffect
            }

        if (fetchInFlight) return@LaunchedEffect
        fetchInFlight = true
        try {
            val fetched = withContext(Dispatchers.IO) {
                fetchCanvasArtworkForPlayback(
                    songTitleRaw = meta.title,
                    artistNameRaw = meta.artists.firstOrNull()?.name.orEmpty(),
                    storefront = storefront,
                    requireVertical = false,
                )
            }
            canvasArtwork = fetched
            if (fetched != null) {
                canvasArtwork = CanvasArtworkPlaybackCache.put(meta.id, fetched)
            }
        } finally {
            fetchInFlight = false
        }
    }

    val artwork = canvasArtwork
    if (artwork != null) {
        CanvasArtworkPlayer(
            primaryUrl = artwork.preferredAnimationUrl,
            fallbackUrl = artwork.preferredVerticalAnimationUrl.takeIf { !it.isNullOrBlank() }
                ?: artwork.preferredAnimationUrl,
            isPlaying = isPlaying,
            modifier = modifier.fillMaxSize(),
        )
    }
}
